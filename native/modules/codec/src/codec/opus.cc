#include "codec/opus.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <new>
#include <span>
#include <stdexcept>

#include <opus/opus.h>

#include "buffer_managed.h"
#include "codec/mini_ogg.h"

namespace ysm::codec {
namespace {
constexpr uint32_t kTargetSampleRate = 48000;
constexpr BufferFixed<8> kOpusId{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
constexpr BufferFixed<8> kOpusTags{'O', 'p', 'u', 's', 'T', 'a', 'g', 's'};

#pragma pack(push, 1)
struct OpusHead {
    BufferFixed<kOpusId.size()> id;
    uint8_t version;
    uint8_t channels;
    uint16_t pre_skip;
    uint32_t input_sample_rate;
    int16_t output_gain;
    uint8_t mapping_family;
};
#pragma pack(pop)

static_assert(sizeof(OpusHead) == 19);
}  // namespace

class OpusAudioStream::Impl {
    enum class HeaderState : uint8_t { kOpusHead, kOpusTags, kComplete };
    enum class PacketStatus : uint8_t { kFull, kNeedInput, kError };

    struct PacketResult {
        BufferViewR packet;
        uint32_t serial_number = 0;
        bool eos = false;
        PacketStatus status = PacketStatus::kNeedInput;
    };

    MiniOgg parser_;
    BufferManaged data_buffer_;
    size_t data_position_ = 0;
    HeaderState header_state_ = HeaderState::kOpusHead;
    uint32_t serial_number_ = std::numeric_limits<uint32_t>::max();
    OpusHead head_{};
    BufferManaged packet_buffer_;

    uint32_t pre_skip_remaining_ = 0;
    uint64_t expected_frames_ = 0;
    uint64_t emitted_frames_ = 0;
    bool input_ended_ = false;
    bool eos_seen_ = false;
    bool failed_ = false;

    OpusDecoder* decoder_ = nullptr;
    BufferManaged pcm_buffer_;
    size_t pcm_buffer_position_ = 0;

   public:
    explicit Impl(uint64_t expected_frames) : expected_frames_(expected_frames) {
        int error = OPUS_OK;
        decoder_ = opus_decoder_create(kTargetSampleRate, 2, &error);
        if (decoder_ == nullptr || error != OPUS_OK) [[unlikely]] {
            if (decoder_ != nullptr) {
                opus_decoder_destroy(decoder_);
                decoder_ = nullptr;
            }
            throw std::bad_alloc();
        }
    }

    ~Impl() {
        if (decoder_ != nullptr) {
            opus_decoder_destroy(decoder_);
        }
    }

    void Consume(BufferViewR data) {
        if (input_ended_ || failed_) [[unlikely]] {
            throw std::logic_error("Opus input is already terminal");
        }
        if (data.empty()) {
            return;
        }

        CompactInput();
        if (data.size() > BufferManaged::kMaxSize - data_buffer_.size())
            [[unlikely]] {
            throw std::bad_alloc();
        }
        const auto offset = data_buffer_.size();
        data_buffer_.resize(offset + data.size());
        std::memcpy(data_buffer_.data() + offset, data.data(), data.size());
    }

    void EndInput() noexcept { input_ended_ = true; }

    int32_t Decode(BufferView dst) {
        if (failed_) [[unlikely]] {
            return kError;
        }
        dst = dst.first(dst.size() - dst.size() % sizeof(int16_t));
        if (dst.empty()) [[unlikely]] {
            return Fail();
        }

        const auto init_result = EnsureInitialized();
        if (init_result != 0) {
            return FinishDecode(init_result);
        }

        size_t written = 0;
        while (!dst.empty()) {
            if (pcm_buffer_position_ < pcm_buffer_.size()) {
                const auto copy_size = std::min(
                    dst.size(), pcm_buffer_.size() - pcm_buffer_position_);
                std::memcpy(dst.data(),
                            pcm_buffer_.data() + pcm_buffer_position_,
                            copy_size);
                dst = dst.subspan(copy_size);
                written += copy_size;
                pcm_buffer_position_ += copy_size;
                if (pcm_buffer_position_ == pcm_buffer_.size()) {
                    pcm_buffer_.clear();
                    pcm_buffer_position_ = 0;
                }
                continue;
            }

            const auto packet = NextPacket();
            if (packet.status == PacketStatus::kNeedInput) {
                if (written > 0) {
                    break;
                }
                if (!input_ended_) {
                    return FinishDecode(kNeedInput);
                }
                return FinishDecode(Complete() ? 0 : Fail());
            }
            if (packet.status == PacketStatus::kError) [[unlikely]] {
                return FinishDecode(Fail());
            }
            if (DecodePacket(packet) < 0) [[unlikely]] {
                return FinishDecode(Fail());
            }
            packet_buffer_.clear();
        }

        return FinishDecode(static_cast<int32_t>(written));
    }

   private:
    int32_t EnsureInitialized() {
        while (header_state_ != HeaderState::kComplete) {
            const auto result = NextPacket();
            if (result.status == PacketStatus::kNeedInput) {
                return input_ended_ ? Fail() : kNeedInput;
            }
            if (result.status == PacketStatus::kError) [[unlikely]] {
                return Fail();
            }

            if (header_state_ == HeaderState::kOpusHead) {
                if (result.packet.size() != sizeof(OpusHead)) [[unlikely]] {
                    return Fail();
                }
                std::memcpy(&head_, result.packet.data(), sizeof(head_));
                if (!Cmp(kOpusId, head_.id) || head_.version > 15 ||
                    (head_.channels != 1 && head_.channels != 2) ||
                    head_.mapping_family != 0) [[unlikely]] {
                    return Fail();
                }

                serial_number_ = result.serial_number;
                if (opus_decoder_init(decoder_, kTargetSampleRate,
                                      head_.channels) != OPUS_OK ||
                    opus_decoder_ctl(decoder_,
                                     OPUS_SET_GAIN(static_cast<opus_int32>(
                                         head_.output_gain))) != OPUS_OK)
                    [[unlikely]] {
                    return Fail();
                }
                pre_skip_remaining_ = head_.pre_skip;
                header_state_ = HeaderState::kOpusTags;
            } else {
                if (result.packet.size() < kOpusTags.size() ||
                    !Cmp(result.packet.first<kOpusTags.size()>(), kOpusTags))
                    [[unlikely]] {
                    return Fail();
                }
                header_state_ = HeaderState::kComplete;
            }
            packet_buffer_.clear();
        }
        return 0;
    }

    int32_t DecodePacket(const PacketResult& packet) {
        const auto samples = opus_packet_get_nb_samples(
            packet.packet.data(),
            static_cast<opus_int32>(packet.packet.size()), kTargetSampleRate);
        if (samples <= 0) [[unlikely]] {
            return kError;
        }

        const auto channels = static_cast<size_t>(head_.channels);
        pcm_buffer_.resize(static_cast<size_t>(samples) * channels *
                           sizeof(int16_t));
        auto* pcm = reinterpret_cast<int16_t*>(pcm_buffer_.data());
        const auto decoded = opus_decode(
            decoder_, packet.packet.data(),
            static_cast<opus_int32>(packet.packet.size()), pcm, samples, 0);
        if (decoded < 0) [[unlikely]] {
            pcm_buffer_.clear();
            return kError;
        }

        const auto skipped = std::min<size_t>(pre_skip_remaining_, decoded);
        pre_skip_remaining_ -= static_cast<uint32_t>(skipped);
        auto output_frames = static_cast<size_t>(decoded) - skipped;
        const auto remaining = expected_frames_ - emitted_frames_;
        if (output_frames > remaining) {
            if (!packet.eos || remaining == 0) [[unlikely]] {
                pcm_buffer_.clear();
                return kError;
            }
            output_frames = static_cast<size_t>(remaining);
        }

        if (head_.channels == 2) {
            auto* source = pcm + skipped * 2;
            Downmix({source, output_frames * 2});
            if (source != pcm && output_frames > 0) {
                std::memmove(pcm, source, output_frames * sizeof(int16_t));
            }
        } else if (skipped > 0 && output_frames > 0) {
            std::memmove(pcm, pcm + skipped,
                         output_frames * sizeof(int16_t));
        }

        emitted_frames_ += output_frames;
        pcm_buffer_.resize(output_frames * sizeof(int16_t));
        pcm_buffer_position_ = 0;
        return 0;
    }

    static void Downmix(std::span<int16_t> pcm) noexcept {
        const auto frames = pcm.size() / 2;
        for (size_t i = 0; i < frames; ++i) {
            pcm[i] = static_cast<int16_t>(
                std::nearbyint((static_cast<float>(pcm[i * 2]) +
                                static_cast<float>(pcm[i * 2 + 1])) /
                               2.0f));
        }
    }

    PacketResult NextPacket() {
        while (data_position_ < data_buffer_.size()) {
            const auto input = BufferViewR(data_buffer_).subspan(data_position_);
            auto result_or = parser_.Process(input);
            if (!result_or.ok()) [[unlikely]] {
                return {.status = PacketStatus::kError};
            }
            const auto result = *result_or;
            data_position_ += result.consumed;

            const auto page = parser_.Page();
            if (!page) {
                return {.status = result.status == MiniOgg::ProcessStatus::kEagain
                                      ? PacketStatus::kNeedInput
                                      : PacketStatus::kError};
            }
            if (serial_number_ != std::numeric_limits<uint32_t>::max() &&
                page->serial_no != serial_number_) [[unlikely]] {
                return {.status = PacketStatus::kError};
            }
            if (page->eos) {
                eos_seen_ = true;
            }

            if (result.status == MiniOgg::ProcessStatus::kEagain) {
                if (result.consumed != 0 &&
                    data_position_ < data_buffer_.size()) {
                    continue;
                }
                return {.status = PacketStatus::kNeedInput};
            }
            if (result.status == MiniOgg::ProcessStatus::kPartial) {
                AppendPacket(result.packet);
                continue;
            }
            if (result.status != MiniOgg::ProcessStatus::kFull) [[unlikely]] {
                return {.status = PacketStatus::kError};
            }

            AppendPacket(result.packet);
            return {packet_buffer_, page->serial_no, page->eos,
                    PacketStatus::kFull};
        }
        return {.status = PacketStatus::kNeedInput};
    }

    void AppendPacket(BufferViewR packet) {
        const auto offset = packet_buffer_.size();
        packet_buffer_.resize(offset + packet.size());
        std::memcpy(packet_buffer_.data() + offset, packet.data(),
                    packet.size());
    }

    [[nodiscard]] bool Complete() const noexcept {
        return header_state_ == HeaderState::kComplete && eos_seen_ &&
               packet_buffer_.empty() && pre_skip_remaining_ == 0 &&
               emitted_frames_ == expected_frames_ &&
               data_position_ == data_buffer_.size();
    }

    int32_t Fail() noexcept {
        failed_ = true;
        pcm_buffer_.clear();
        pcm_buffer_position_ = 0;
        return kError;
    }

    int32_t FinishDecode(int32_t result) noexcept {
        CompactInput();
        return result;
    }

    void CompactInput() noexcept {
        if (data_position_ == 0) {
            return;
        }
        const auto remaining = data_buffer_.size() - data_position_;
        if (remaining > 0) {
            std::memmove(data_buffer_.data(),
                         data_buffer_.data() + data_position_, remaining);
            data_buffer_.resize(remaining);
        } else {
            data_buffer_.clear();
        }
        data_position_ = 0;
    }
};

YSM_PIMPL_DEFINITION(OpusAudioStream)

OpusAudioStream::OpusAudioStream(uint64_t expected_frames)
    : YSM_PIMPL_CONSTRUCT(expected_frames) {}

OpusAudioStream::~OpusAudioStream() = default;

void OpusAudioStream::Consume(BufferViewR data_buffer) {
    Pimpl().Consume(data_buffer);
}

void OpusAudioStream::EndInput() noexcept {
    Pimpl().EndInput();
}

int32_t OpusAudioStream::Decode(BufferView dst_buffer) {
    return Pimpl().Decode(dst_buffer);
}
}  // namespace ysm::codec

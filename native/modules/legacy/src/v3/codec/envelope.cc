#include "envelope.h"

#include <zstd.h>

#include <algorithm>
#include <array>
#include <memory>
#include <optional>
#include <string>
#include <utility>

#include <buffer_managed.h>
#include <err.h>

#include "chacha.h"
#include "deobfuscator.h"
#include "dirty_zstd.h"
#include "modified_city_hash.h"

namespace ysm::legacy::v3::codec {
namespace {
constexpr BufferFixed<4> kRawMagic{'Y', 'S', 'G', 'P'};
constexpr BufferFixed<7> kV3Magic{0xEF, 0xBB, 0xBF, 'Y', 'S', 'G', 'P'};
constexpr BufferFixed<4> kZstdMagic{0x28, 0xB5, 0x2F, 0xFD};
constexpr std::uint64_t kEnvelopeHashSeed = 11409194399050398761ULL;
constexpr std::size_t kFooterSize = 64;
constexpr std::size_t kCryptoBufferSize = 127 * 64;
constexpr std::size_t kCompressedBufferSize = 32 * 1024;
constexpr std::size_t kPlaintextBufferSize = 64 * 1024;

std::uint32_t LoadLe32(BufferViewR bytes) noexcept {
    return static_cast<std::uint32_t>(bytes[0]) |
           static_cast<std::uint32_t>(bytes[1]) << 8U |
           static_cast<std::uint32_t>(bytes[2]) << 16U |
           static_cast<std::uint32_t>(bytes[3]) << 24U;
}

std::uint32_t LoadBe32(BufferViewR bytes) noexcept {
    return static_cast<std::uint32_t>(bytes[3]) |
           static_cast<std::uint32_t>(bytes[2]) << 8U |
           static_cast<std::uint32_t>(bytes[1]) << 16U |
           static_cast<std::uint32_t>(bytes[0]) << 24U;
}

std::uint64_t LoadLe64(BufferViewR bytes) noexcept {
    std::uint64_t value{};
    for (std::size_t index = sizeof(value); index != 0; --index) {
        value = (value << 8U) | bytes[index - 1];
    }
    return value;
}

struct ZstdStreamDeleter {
    void operator()(ZSTD_DStream* stream) const noexcept {
        ZSTD_freeDStream(stream);
    }
};

absl::Status ZstdFailure(std::size_t result) {
    const auto code = ZSTD_getErrorCode(result);
    if (code == ZSTD_error_memory_allocation ||
        code == ZSTD_error_dstSize_tooSmall ||
        code == ZSTD_error_noForwardProgress_destFull) {
        return absl::ResourceExhaustedError(
            "Legacy zstd resource limit exceeded");
    }
    return absl::DataLossError(std::string("Legacy zstd frame is invalid: ") +
                               ZSTD_getErrorName(result));
}

absl::StatusOr<std::size_t> FindSummaryEnd(BufferViewR source) {
    const auto available = source.subspan(kV3Magic.size());
    const auto summary = available.first(
        std::min<std::size_t>(available.size(), kLegacySummaryByteLimit + 1));
    const auto terminator = std::find(summary.begin(), summary.end(), 0);
    if (terminator != summary.end()) {
        return kV3Magic.size() +
               static_cast<std::size_t>(terminator - summary.begin());
    }
    if (available.size() > kLegacySummaryByteLimit) {
        return absl::ResourceExhaustedError(
            "Legacy summary exceeds its byte limit");
    }
    return absl::DataLossError("Legacy summary is truncated");
}
}  // namespace

class ContainerReader::Impl final {
   public:
    explicit Impl(BufferViewR source)
        : source_(source),
          decrypted_(kCryptoBufferSize),
          compressed_(kCompressedBufferSize),
          plaintext_(kPlaintextBufferSize) {}

    absl::Status Initialize() {
        if (initialized_) {
            return absl::InternalError("Legacy reader was initialized twice");
        }
        initialized_ = true;

        source_size_ = source_.size();
        YSM_ASSERT(source_size_ <= kLegacySourceByteLimit,
                   absl::ResourceExhaustedError(
                       "Legacy source exceeds its byte limit"));

        BufferFixed<8> prefix{};
        const auto prefix_size = static_cast<std::size_t>(
            std::min<std::uint64_t>(prefix.size(), source_size_));
        Copy(Slice(source_, 0, prefix_size), Slice(prefix, 0, prefix_size));
        if (prefix_size >= kRawMagic.size() &&
            Cmp(Slice(prefix, 0, kRawMagic.size()), kRawMagic)) {
            if (prefix_size == prefix.size()) {
                const auto version =
                    LoadBe32(Slice(prefix, kRawMagic.size(), 4));
                return absl::InvalidArgumentError(
                    version == 1 || version == 2
                        ? "Raw YSM must be routed to the raw reader"
                        : "Raw YSM version is not a legacy v3 container");
            }
            return absl::InvalidArgumentError(
                "Raw YSM is not a legacy v3 container");
        }
        YSM_ASSERT(
            prefix_size >= kV3Magic.size() &&
                Cmp(Slice(prefix, 0, kV3Magic.size()), kV3Magic),
            absl::InvalidArgumentError("Source is not a legacy v3 container"));

        std::size_t summary_end{};
        YSM_ASSIGN_OR_RETURN(summary_end, FindSummaryEnd(source_));
        const auto version_offset = summary_end + 1;
        YSM_ASSERT(version_offset <= source_size_ &&
                       source_size_ - version_offset >= sizeof(std::uint32_t),
                   absl::DataLossError("Legacy envelope version is truncated"));
        BufferFixed<4> version_bytes{};
        Copy(Slice(source_, version_offset, version_bytes.size()),
             version_bytes);
        YSM_ASSERT(
            LoadLe32(version_bytes) == 3,
            absl::UnimplementedError("Legacy envelope version is unsupported"));

        content_offset_ = version_offset + sizeof(std::uint32_t);
        YSM_ASSERT(source_size_ >= kFooterSize &&
                       content_offset_ < source_size_ - kFooterSize,
                   absl::DataLossError("Legacy envelope body is truncated"));
        content_size_ = source_size_ - kFooterSize - content_offset_;

        BufferFixed<kChaChaPasswordSize> password{};
        BufferFixed<8> expected_hash{};
        Copy(Slice(source_, source_size_ - kFooterSize, password.size()),
             password);
        Copy(Slice(source_, source_size_ - expected_hash.size(),
                   expected_hash.size()),
             expected_hash);
        const auto actual_hash = ModifiedCityHash64WithSeed(
            source_.first(source_size_ - expected_hash.size()),
            kEnvelopeHashSeed);
        YSM_ASSERT(
            actual_hash == LoadLe64(expected_hash),
            absl::DataLossError("Legacy envelope checksum does not match"));

        chacha_.emplace(password);
        deobfuscator_.emplace(password);
        zstd_.reset(ZSTD_createDStream());
        YSM_ASSERT(zstd_ != nullptr, absl::ResourceExhaustedError(
                                         "Unable to allocate zstd decoder"));
        const auto result = ZSTD_initDStream(zstd_.get());
        if (ZSTD_isError(result)) {
            return ZstdFailure(result);
        }
        compressed_.resize(kCompressedBufferSize);
        return absl::OkStatus();
    }

    bool ReadExact(BufferView destination) {
        if (!initialized_) {
            return Fail(
                absl::InternalError("Legacy reader is not initialized"));
        }
        while (!destination.empty()) {
            if (plaintext_begin_ != plaintext_end_) {
                const auto count = std::min(destination.size(),
                                            plaintext_end_ - plaintext_begin_);
                Copy(Slice(plaintext_, plaintext_begin_, count),
                     Slice(destination, 0, count));
                plaintext_begin_ += count;
                plaintext_size_ += count;
                destination = destination.subspan(count);
                continue;
            }

            if (destination.size() >= plaintext_.size()) {
                std::size_t produced{};
                if (!DecompressInto(destination, produced)) {
                    return false;
                }
                if (produced == 0) {
                    return Fail(absl::DataLossError(
                        "Legacy model plaintext is truncated"));
                }
                plaintext_size_ += produced;
                destination = destination.subspan(produced);
                continue;
            }

            plaintext_begin_ = 0;
            if (!DecompressInto(plaintext_, plaintext_end_)) {
                return false;
            }
            if (plaintext_end_ == 0) {
                return Fail(
                    absl::DataLossError("Legacy model plaintext is truncated"));
            }
        }
        return true;
    }

    bool Finish() {
        if (plaintext_begin_ != plaintext_end_) {
            return Fail(absl::DataLossError(
                "Historical model has trailing plaintext bytes"));
        }
        BufferFixed<1> probe{};
        while (!frame_done_) {
            if (pending_input_.empty()) {
                if (!PrepareInput()) {
                    return false;
                }
            }
            if (pending_input_.empty()) {
                return Fail(
                    absl::DataLossError("Legacy zstd frame is truncated"));
            }
            ZSTD_inBuffer input{pending_input_.data(), pending_input_.size(),
                                0};
            ZSTD_outBuffer output{probe.data(), probe.size(), 0};
            const auto result =
                ZSTD_decompressStream(zstd_.get(), &output, &input);
            if (ZSTD_isError(result)) {
                return Fail(ZstdFailure(result));
            }
            pending_input_ = pending_input_.subspan(input.pos);
            if (output.pos != 0) {
                return Fail(absl::DataLossError(
                    "Historical model has trailing plaintext bytes"));
            }
            if (result == 0) {
                if (!pending_input_.empty()) {
                    return Fail(absl::DataLossError(
                        "Legacy zstd frame has trailing data"));
                }
                frame_done_ = true;
            }
            if (input.pos == 0 && output.pos == 0) {
                return Fail(absl::DataLossError(
                    "Legacy zstd decoder made no progress"));
            }
        }
        if (phase_ != Phase::kDone || !pending_input_.empty()) {
            return Fail(
                absl::DataLossError("Legacy zstd frame has trailing data"));
        }
        if (decrypted_offset_ != decrypted_.size() ||
            content_consumed_ != content_size_) {
            return Fail(
                absl::DataLossError("Legacy envelope body has trailing data"));
        }
        return true;
    }

    [[nodiscard]] std::uint64_t source_size() const noexcept {
        return source_size_;
    }

    [[nodiscard]] std::uint64_t offset() const noexcept {
        return plaintext_size_;
    }

    [[nodiscard]] const absl::Status& status() const noexcept {
        return status_;
    }

   private:
    enum class Phase {
        kFrameHeader,
        kBlockHeader,
        kBlockPayload,
        kChecksum,
        kDone,
    };

    bool Fail(absl::Status status) {
        status_ = std::move(status);
        return false;
    }

    bool DecompressInto(BufferView destination, std::size_t& produced) {
        ZSTD_outBuffer output{destination.data(), destination.size(), 0};
        while (output.pos < output.size && !frame_done_) {
            if (pending_input_.empty() && !PrepareInput()) {
                return false;
            }
            if (pending_input_.empty()) {
                return Fail(
                    absl::DataLossError("Legacy zstd frame is truncated"));
            }
            ZSTD_inBuffer input{pending_input_.data(), pending_input_.size(),
                                0};
            const auto before_output = output.pos;
            const auto result =
                ZSTD_decompressStream(zstd_.get(), &output, &input);
            if (ZSTD_isError(result)) {
                return Fail(ZstdFailure(result));
            }
            pending_input_ = pending_input_.subspan(input.pos);
            if (result == 0) {
                if (!pending_input_.empty()) {
                    return Fail(absl::DataLossError(
                        "Legacy zstd frame has trailing data"));
                }
                frame_done_ = true;
            }
            if (input.pos == 0 && output.pos == before_output) {
                return Fail(absl::DataLossError(
                    "Legacy zstd decoder made no progress"));
            }
        }
        produced = output.pos;
        return true;
    }

    bool FillDecrypted() {
        while (decrypted_offset_ == decrypted_.size()) {
            if (content_consumed_ >= content_size_) {
                return Fail(
                    absl::DataLossError("Legacy encrypted body is truncated"));
            }
            const auto expected = chacha_->next_chunk_size();
            const auto count = static_cast<std::size_t>(std::min<std::uint64_t>(
                expected, content_size_ - content_consumed_));
            decrypted_.resize(count);
            Copy(Slice(source_, content_offset_ + content_consumed_, count),
                 decrypted_);
            content_consumed_ += count;
            chacha_->Decrypt(decrypted_, count == expected);
            const auto transformed = deobfuscator_->Apply(decrypted_);
            decrypted_offset_ = static_cast<std::size_t>(transformed.data() -
                                                         decrypted_.data());
        }
        return true;
    }

    bool ReadEncryptedExact(BufferView destination) {
        while (!destination.empty()) {
            if (!FillDecrypted()) {
                return false;
            }
            const auto count = std::min(destination.size(),
                                        decrypted_.size() - decrypted_offset_);
            Copy(Slice(decrypted_, decrypted_offset_, count),
                 Slice(destination, 0, count));
            decrypted_offset_ += count;
            destination = destination.subspan(count);
        }
        return true;
    }

    bool PrepareInput() {
        if (!pending_input_.empty()) {
            return Fail(absl::InternalError(
                "Legacy zstd input was replaced before consumption"));
        }
        switch (phase_) {
            case Phase::kFrameHeader: {
                if (!ReadEncryptedExact(Slice(frame_header_, 0, 5))) {
                    return false;
                }
                if (!Cmp(Slice(frame_header_, 0, kZstdMagic.size()),
                         kZstdMagic)) {
                    return Fail(absl::DataLossError(
                        "Legacy zstd frame magic is invalid"));
                }
                const auto descriptor = frame_header_[4];
                checksum_ = (descriptor & 0x04U) != 0;
                const auto single_segment = (descriptor & 0x20U) != 0;
                constexpr std::array<std::size_t, 4> kDictionarySize{0, 1, 2,
                                                                     4};
                const auto content_flag = descriptor >> 6U;
                const auto content_size = content_flag == 0
                                              ? (single_segment ? 1U : 0U)
                                              : (1U << content_flag);
                frame_header_size_ = 5 + (single_segment ? 0 : 1) +
                                     kDictionarySize[descriptor & 3U] +
                                     content_size;
                if (!ReadEncryptedExact(
                        Slice(frame_header_, 5, frame_header_size_ - 5))) {
                    return false;
                }
                pending_input_ = Slice(frame_header_, 0, frame_header_size_);
                phase_ = Phase::kBlockHeader;
                return true;
            }
            case Phase::kBlockHeader: {
                if (!ReadEncryptedExact(block_header_)) {
                    return false;
                }
                absl::StatusOr<NormalizedZstdBlockHeader> normalized =
                    NormalizeDirtyZstdBlockHeader(block_header_);
                if (!normalized.ok()) {
                    return Fail(normalized.status());
                }
                normalized_block_header_ = normalized->bytes;
                block_remaining_ = normalized->payload_size;
                last_block_ = normalized->last;
                pending_input_ = normalized_block_header_;
                phase_ = Phase::kBlockPayload;
                if (block_remaining_ == 0) {
                    AdvanceAfterBlock();
                }
                return true;
            }
            case Phase::kBlockPayload: {
                const auto count =
                    std::min(block_remaining_, compressed_.size());
                if (!ReadEncryptedExact(Slice(compressed_, 0, count))) {
                    return false;
                }
                block_remaining_ -= count;
                pending_input_ = Slice(compressed_, 0, count);
                if (block_remaining_ == 0) {
                    AdvanceAfterBlock();
                }
                return true;
            }
            case Phase::kChecksum:
                if (!ReadEncryptedExact(checksum_bytes_)) {
                    return false;
                }
                pending_input_ = checksum_bytes_;
                phase_ = Phase::kDone;
                return true;
            case Phase::kDone:
                return Fail(
                    absl::DataLossError("Legacy zstd frame is truncated"));
        }
        return Fail(absl::InternalError("Legacy zstd phase is invalid"));
    }

    void AdvanceAfterBlock() noexcept {
        if (!last_block_) {
            phase_ = Phase::kBlockHeader;
        } else if (checksum_) {
            phase_ = Phase::kChecksum;
        } else {
            phase_ = Phase::kDone;
        }
    }

    BufferViewR source_;
    BufferManaged decrypted_;
    BufferManaged compressed_;
    BufferManaged plaintext_;
    std::optional<YsmChaCha> chacha_;
    std::optional<Deobfuscator> deobfuscator_;
    std::unique_ptr<ZSTD_DStream, ZstdStreamDeleter> zstd_;
    BufferFixed<18> frame_header_{};
    BufferFixed<3> block_header_{};
    BufferFixed<3> normalized_block_header_{};
    BufferFixed<4> checksum_bytes_{};
    BufferViewR pending_input_;
    std::uint64_t source_size_{};
    std::uint64_t content_offset_{};
    std::uint64_t content_size_{};
    std::uint64_t content_consumed_{};
    std::uint64_t plaintext_size_{};
    std::size_t decrypted_offset_{kCryptoBufferSize};
    std::size_t plaintext_begin_{};
    std::size_t plaintext_end_{};
    std::size_t frame_header_size_{};
    std::size_t block_remaining_{};
    bool initialized_{};
    bool checksum_{};
    bool last_block_{};
    bool frame_done_{};
    Phase phase_{Phase::kFrameHeader};
    absl::Status status_;
};

YSM_PIMPL_DEFINITION(ContainerReader)

ContainerReader::ContainerReader(BufferViewR source)
    : YSM_PIMPL_CONSTRUCT(source) {}

ContainerReader::~ContainerReader() = default;

absl::Status ContainerReader::Initialize() {
    return Pimpl().Initialize();
}

std::uint64_t ContainerReader::source_size() const noexcept {
    return Pimpl().source_size();
}

bool ContainerReader::ReadExact(BufferView destination) {
    return Pimpl().ReadExact(destination);
}

bool ContainerReader::Finish() {
    return Pimpl().Finish();
}

const absl::Status& ContainerReader::status() const noexcept {
    return Pimpl().status();
}

std::uint64_t ContainerReader::offset() const noexcept {
    return Pimpl().offset();
}
}  // namespace ysm::legacy::v3::codec

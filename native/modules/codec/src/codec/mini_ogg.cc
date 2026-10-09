#include "codec/mini_ogg.h"

#include <cstring>

namespace ysm::codec {
namespace {
constexpr uint32_t kHeaderSize = 27;
constexpr BufferFixed<4> kHeadId{'O', 'g', 'g', 'S'};

static_assert(sizeof(MiniOgg::PageHeader) == kHeaderSize);
}  // namespace

MiniOgg::MiniOgg() noexcept {
    Reset();
}

void MiniOgg::Reset() noexcept {
    header_ = {};
    segments_ = {};
    demux_state_ = State::kHeader;
    packet_processed_ = 0;
    segment_index_ = 0;
}

gch::optional_cref<MiniOgg::PageHeader> MiniOgg::Page() const noexcept {
    if (demux_state_ != State::kHeader) {
        return gch::optional_cref(header_);
    }
    return gch::nullopt;
}

absl::StatusOr<MiniOgg::ProcessResult> MiniOgg::Process(BufferViewR data) {
    ProcessResult result;

    auto remaining = data;
    switch (demux_state_) {
        case State::kPageEof:
            demux_state_ = State::kHeader;
        case State::kHeader: {
            if (remaining.size() < kHeaderSize) {
                result.status = ProcessStatus::kEagain;
                break;
            }
            auto header_span = Consume(remaining, kHeaderSize);
            std::memcpy(&header_, header_span.data(), sizeof(header_));
            YSM_ASSERT(Cmp(header_.id, kHeadId), DataCorruption());

            demux_state_ = State::kSegmentTable;
        }
            /* fall-through */
        case State::kSegmentTable: {
            if (remaining.size() < header_.segments) {
                result.status = ProcessStatus::kEagain;
                break;
            }
            auto segment_span = Consume(remaining, header_.segments);
            Copy(BufferViewR{(segment_span.data()), segment_span.size()},
                 segments_);

            segment_index_ = 0;
            demux_state_ = State::kBody;
        }
            /* fall-through */
        case State::kBody: {
            while (true) {
                if (segment_index_ == header_.segments) {
                    if (packet_processed_ > 0) {
                        result.status = ProcessStatus::kPartial;
                        result.packet = Consume(remaining, packet_processed_);
                        packet_processed_ = 0;
                    } else {
                        result.status = ProcessStatus::kEagain;
                    }
                    demux_state_ = State::kPageEof;
                    break;
                }
                auto segment_size = segments_[segment_index_];
                if (packet_processed_ + segment_size > remaining.size()) {
                    result.status = ProcessStatus::kEagain;
                    break;
                }
                if (++segment_index_ == header_.segments) {
                    demux_state_ = State::kPageEof;
                }
                packet_processed_ += segment_size;
                if (segment_size < UINT8_MAX) {
                    result.status = ProcessStatus::kFull;
                    result.packet = Consume(remaining, packet_processed_);
                    packet_processed_ = 0;
                    break;
                }
            }
        }
    }

    result.consumed = static_cast<uint16_t>(data.size() - remaining.size());
    return result;
}
}  // namespace ysm::codec

#pragma once

/* SPDX-License-Identifier: 0BSD */
/* License text available at end of this file */

#include <cstdint>
#include <optional_ref.hpp>
#include "buffer.h"

namespace ysm::codec {
class MiniOgg {
   public:
    static constexpr size_t kMaxSegments = 255;

    enum class State : uint8_t { kHeader, kSegmentTable, kBody, kPageEof };

#pragma pack(push, 1)

    struct PageHeader {
        BufferFixed<4> id;
        uint8_t version;

        bool continuation : 1;
        bool bos : 1;
        bool eos : 1;
        uint8_t reserved : 5;

        uint64_t granule_pos;
        uint32_t serial_no;
        uint32_t page_seq;
        uint32_t crc32;
        uint8_t segments;
    };

#pragma pack(pop)

    enum class ProcessStatus : uint8_t { kNone, kPartial, kFull, kEagain };

    struct ProcessResult {
        BufferViewR packet;
        uint16_t consumed = 0;
        ProcessStatus status = ProcessStatus::kNone;
    };

    MiniOgg() noexcept;

    void Reset() noexcept;

    [[nodiscard]] gch::optional_cref<PageHeader> Page() const noexcept;

    absl::StatusOr<ProcessResult> Process(BufferViewR data);

   private:
    PageHeader header_{};
    BufferFixed<kMaxSegments> segments_{};

    uint16_t packet_processed_ = 0;
    uint8_t segment_index_ = 0;
    State demux_state_ = State::kHeader;
};
}  // namespace ysm::codec

/*
BSD Zero Clause License

Copyright (c) 2023 John Regan

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
*/

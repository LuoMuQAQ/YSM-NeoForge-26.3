#pragma once

#include <absl/status/statusor.h>

#include <cstddef>

#include <buffer.h>

namespace ysm::legacy::v3::codec {
struct NormalizedZstdBlockHeader {
    BufferFixed<3> bytes;
    std::size_t payload_size;
    bool last;
};

absl::StatusOr<NormalizedZstdBlockHeader> NormalizeDirtyZstdBlockHeader(
    BufferFixedViewR<3> dirty) noexcept;
}  // namespace ysm::legacy::v3::codec

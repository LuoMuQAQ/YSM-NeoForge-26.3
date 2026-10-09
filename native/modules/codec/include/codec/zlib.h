#pragma once

#include "buffer_managed.h"
#include "err.h"

namespace ysm::codec {
absl::Status ZlibDecompress(BufferViewR input, BufferManaged& dst);
absl::StatusOr<size_t> ZlibDecompress(BufferViewR input, BufferView dst);
}  // namespace ysm::codec

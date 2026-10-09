#pragma once

#include "codec/image.h"
#include "err.h"

namespace ysm::codec::image {
struct JpegDecoder final {
    bool Is(BufferViewR img) const;

    absl::StatusOr<ImageInfo> Probe(BufferViewR img) const;

    absl::Status Decode(BufferViewR img, const ImageInfo& info,
                        BufferView dst) const;
};
}  // namespace ysm::codec::image
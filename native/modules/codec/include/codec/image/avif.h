#pragma once

#include "codec/image.h"
#include "err.h"

namespace ysm::codec::image {
struct AvifDecoder final {
    bool Is(BufferViewR img) const;

    absl::StatusOr<ImageInfo> Probe(BufferViewR img) const;

    absl::Status Decode(BufferViewR img, const ImageInfo& info,
                        BufferView dst) const;
};

absl::StatusOr<EncodeResult> AvifEncodeLossy(BufferViewR pixels, uint32_t width,
                                             uint32_t height, BufferView dst,
                                             size_t max_width,
                                             size_t max_height);
}  // namespace ysm::codec::image

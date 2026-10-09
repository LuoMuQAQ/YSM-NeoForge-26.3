#pragma once

#include "codec/image.h"
#include "err.h"

namespace ysm::codec::image {
struct WebpDecoder final {
    bool Is(BufferViewR img) const noexcept;

    absl::StatusOr<ImageInfo> Probe(BufferViewR img) const;

    absl::Status Decode(BufferViewR img, const ImageInfo& info,
                        BufferView dst) const;
};

absl::StatusOr<EncodeResult> WebpEncodeLossless(BufferViewR pixels,
                                                uint32_t width, uint32_t height,
                                                BufferView dst);
}  // namespace ysm::codec::image
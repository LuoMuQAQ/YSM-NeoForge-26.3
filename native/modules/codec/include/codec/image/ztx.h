#pragma once

#include "codec/image.h"
#include "err.h"

namespace ysm::codec::image {
// 随便搓的格式：得益于 zstd，绝大多数情况下的各项指标均优于 png 的任意参数组合。
struct ZtxDecoder final {
    bool Is(BufferViewR img) const;

    absl::StatusOr<ImageInfo> Probe(BufferViewR img) const;

    absl::Status Decode(BufferViewR img, const ImageInfo& info,
                        BufferView dst) const;
};

absl::StatusOr<EncodeResult> ZtxEncodeLossless(BufferViewR pixels,
                                               uint32_t width, uint32_t height,
                                               BufferView dst,
                                               uint32_t max_width,
                                               uint32_t max_height);
}  // namespace ysm::codec::image
#pragma once

#include "buffer_managed.h"
#include "err.h"

namespace ysm::codec {
enum class ImageFormat : uint32_t {
    kRgba = 0,
    kPng = 1,
    kJpeg = 2,
    kWebp = 3,
    kAvif = 4,
    kZtx = 5,

    kSize
};

struct ImageInfo {
    uint32_t width = 0;
    uint32_t height = 0;
    ImageFormat format = ImageFormat::kRgba;
    uint32_t frame_count = 0;
};

struct EncodeResult {
    ImageInfo info;
    size_t size;
};

absl::StatusOr<ImageInfo> ImageProbe(BufferViewR image_buffer);

absl::Status ImageDecode(BufferViewR img, const ImageInfo& info,
                         BufferView dst);

absl::StatusOr<EncodeResult> ImageEncodeLossy(BufferViewR pixels,
                                              uint32_t width, uint32_t height,
                                              BufferView dst, size_t max_width,
                                              size_t max_height);

absl::StatusOr<EncodeResult> ImageEncodeLossless(BufferViewR pixels,
                                                 uint32_t width,
                                                 uint32_t height,
                                                 BufferView dst);
}  // namespace ysm::codec

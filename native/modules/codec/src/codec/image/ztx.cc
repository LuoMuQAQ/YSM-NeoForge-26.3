#include "codec/image/ztx.h"

#include <libyuv.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>

#include "codec/zstd.h"
#include "buffer_scalar.h"

namespace ysm::codec::image {
namespace {
constexpr BufferFixed<4> kHeader{'z', 't', 'x', '1'};
constexpr int kZstdLevel = 16;

struct ImageSize {
    uint32_t width;
    uint32_t height;
};

ImageSize FitWithin(uint32_t width, uint32_t height, uint32_t max_width,
                    uint32_t max_height) {
    if (width <= max_width && height <= max_height) {
        return {width, height};
    }

    if (static_cast<double>(width) / height >=
        static_cast<double>(max_width) / max_height) {
        const auto scaled_height = static_cast<uint32_t>(
            std::round(static_cast<double>(height) * max_width / width));
        return {max_width, std::max(1u, std::min(max_height, scaled_height))};
    }

    const auto scaled_width = static_cast<uint32_t>(
        std::round(static_cast<double>(width) * max_height / height));
    return {std::max(1u, std::min(max_width, scaled_width)), max_height};
}

absl::StatusOr<size_t> RgbaByteSize(uint32_t width, uint32_t height) {
    YSM_ASSERT(width > 0 && height > 0,
               absl::InvalidArgumentError("Invalid image dimensions."sv));

    const auto pixels = static_cast<uint64_t>(width) * height;
    YSM_ASSERT(pixels <= std::numeric_limits<size_t>::max() / 4,
               absl::InvalidArgumentError("Image dimensions are too large."));
    return static_cast<size_t>(pixels) * 4;
}
}  // namespace

bool ZtxDecoder::Is(BufferViewR img) const {
    return img.size() > 4 && Cmp(img.subspan(0, 4), kHeader);
}

absl::StatusOr<ImageInfo> ZtxDecoder::Probe(BufferViewR img) const {
    YSM_ASSERT(img.size() > 12, DataCorruption());
    return ImageInfo{BufScalar<uint32_t>(img.subspan(4, 4)),
                     BufScalar<uint32_t>(img.subspan(8, 4)), ImageFormat::kZtx,
                     1};
}

absl::Status ZtxDecoder::Decode(BufferViewR img, const ImageInfo& info,
                                BufferView dst) const {
    auto pixel_size = info.width * info.height;
    YSM_ASSERT(dst.size() >= pixel_size,
               absl::ResourceExhaustedError("Dst buffer to small"sv));
    YSM_RETURN_IF_ERROR(ZstdDecompress(img.subspan(12), dst));
    return OkStatus();
}

absl::StatusOr<EncodeResult> ZtxEncodeLossless(
    BufferViewR pixels, uint32_t in_width, uint32_t in_height, BufferView dst,
    uint32_t max_width, uint32_t max_height) {
    YSM_DECLARE_OR_RETURN(input_size, RgbaByteSize(in_width, in_height));
    YSM_ASSERT(pixels.size() >= input_size,
               absl::InvalidArgumentError("Input pixel buffer too small."sv));
    BufferViewR input_pixels = pixels.first(input_size);

    YSM_ASSERT(max_width > 0 && max_height > 0,
               absl::InvalidArgumentError("Invalid image limits."sv));
    auto image_size = FitWithin(in_width, in_height, max_width, max_height);
    BufferManaged scaled_pixels;

    if (image_size.width != in_width || image_size.height != in_height) {
        size_t scaled_size = 0;
        YSM_ASSIGN_OR_RETURN(scaled_size,
                             RgbaByteSize(image_size.width, image_size.height));
        scaled_pixels.resize(scaled_size);

        YSM_ASSERT(
            libyuv::ARGBScale(
                input_pixels.data(), static_cast<int>(in_width * 4),
                static_cast<int>(in_width), static_cast<int>(in_height),
                scaled_pixels.data(), static_cast<int>(image_size.width * 4),
                static_cast<int>(image_size.width),
                static_cast<int>(image_size.height), libyuv::kFilterBox) == 0,
            absl::InternalError("Error scaling image."));
        input_pixels = scaled_pixels;
    }

    auto buf = dst;
    Copy(kHeader, Consume(buf, 4_B));
    Copy(ScalarBuf(image_size.width), Consume(buf, 4_B));
    Copy(ScalarBuf(image_size.height), Consume(buf, 4_B));
    YSM_DECLARE_OR_RETURN(compress_size,
                          ZstdCompress(input_pixels, buf, kZstdLevel));
    return EncodeResult{
        ImageInfo{image_size.width, image_size.height, ImageFormat::kZtx, 1},
        12 + compress_size};
}
}  // namespace ysm::codec::image

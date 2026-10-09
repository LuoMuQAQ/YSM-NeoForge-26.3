#include <bitpack.h>
#include <codec/image.h>
#include <enum.h>
#include <java/buffer.h>
#include <java/entry.h>
#include <log.h>

namespace ysm::lib::image {
namespace {
YSM_BIT_PACK(PackedEncodeResult, jlong,
             YSM_BIT_FIELD(size, uint32_t, 28),
             YSM_BIT_FIELD(format, codec::ImageFormat, 4),
             YSM_BIT_FIELD(height, uint16_t, 16),
             YSM_BIT_FIELD(width, uint16_t, 16));
}  // namespace

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/image/ImageEncoder;nEncode(JIIJJZII)J",
    (pixels, width, height, dst, dst_size, lossless, max_width, max_height)) {
    YSM_ASSERT(width > 0 && height > 0,
               absl::InvalidArgumentError("Invalid image size"sv));
    YSM_ASSERT(dst != 0 && dst_size > 0,
               absl::InvalidArgumentError("Invalid output buf"sv));
    BufferViewR in_pixels{reinterpret_cast<const Byte*>(pixels),
                          static_cast<size_t>(width * height * 4)};
    BufferView out_buf{reinterpret_cast<Byte*>(dst),
                       static_cast<size_t>(dst_size)};

    codec::EncodeResult result;
    if (lossless) {
        YSM_ASSIGN_OR_RETURN(
            result,
            codec::ImageEncodeLossless(in_pixels, width, height, out_buf));
    } else {
        YSM_ASSERT(max_height > 0 && max_width > 0,
                   absl::InvalidArgumentError("Invalid image max size"sv));
        YSM_ASSIGN_OR_RETURN(
            result, codec::ImageEncodeLossy(in_pixels, width, height, out_buf,
                                             max_width, max_height));
    }
    YSM_LOG_DEBUG(
        "Encoded image: lossless={}, source={}x{}, result={}x{}, "
        "format={}, output={} bytes",
        lossless == JNI_TRUE, width, height, result.info.width,
        result.info.height, static_cast<int>(result.info.format),
        result.size);
    return PackedEncodeResult::pack(result.size, result.info.format,
                                    result.info.height, result.info.width);
}
}  // namespace ysm::lib::image

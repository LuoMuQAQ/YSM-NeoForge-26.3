#include "codec/image.h"

#include <proxy/proxy.h>
#include <algorithm>

#include "codec/image/avif.h"
#include "codec/image/jpeg.h"
#include "codec/image/png.h"
#include "codec/image/webp.h"
#include "codec/image/ztx.h"

namespace ysm::codec {
namespace {
using namespace image;

constexpr size_t kMinLossySize = 96;

PRO_DEF_MEM_DISPATCH(MemIs, Is);
PRO_DEF_MEM_DISPATCH(MemProbe, Probe);
PRO_DEF_MEM_DISPATCH(MemDecode, Decode);

struct DecoderFacade
    : pro::facade_builder ::add_convention<MemIs, bool(BufferViewR img) const>::
          add_convention<MemProbe,
                         absl::StatusOr<ImageInfo>(BufferViewR img) const>::
              add_convention<MemDecode, absl::Status(BufferViewR img,
                                                     const ImageInfo& info,
                                                     BufferView dst) const>::
                  support_destruction<pro::constraint_level::trivial>::build {};

using Decoder = pro::proxy<DecoderFacade>;

template <typename Type>
auto CreateDecoder() {
    return pro::make_proxy_inplace<DecoderFacade, Type>();
}

const std::array<Decoder, static_cast<size_t>(ImageFormat::kSize)> kDecoders{
    nullptr,
    CreateDecoder<PngDecoder>(),
    CreateDecoder<JpegDecoder>(),
    CreateDecoder<WebpDecoder>(),
    CreateDecoder<AvifDecoder>(),
    CreateDecoder<ZtxDecoder>(),
};
}  // namespace

absl::StatusOr<ImageInfo> ImageProbe(BufferViewR img) {
    auto result = std::ranges::find_if(kDecoders, [&](const auto& decoder) {
        return decoder != nullptr && decoder->Is(img);
    });
    YSM_ASSERT(result != kDecoders.end(),
               absl::InvalidArgumentError("Unsupported image format."sv));
    ImageInfo info;
    YSM_ASSIGN_OR_RETURN(info, (*result)->Probe(img));
    return info;
}

absl::Status ImageDecode(BufferViewR img, const ImageInfo& info,
                         BufferView dst) {
    YSM_ASSERT_BUF_SIZE(dst, info.width * info.height * 4);

    if (info.format == ImageFormat::kRgba) [[unlikely]] {
        YSM_ASSERT(img.size() <= dst.size(),
                   absl::InvalidArgumentError("Dst buffer too small."sv));
        Copy(img, dst);
        return OkStatus();
    }
    auto type_id = static_cast<uint32_t>(info.format);
    YSM_ASSERT(type_id < static_cast<uint32_t>(ImageFormat::kSize),
               absl::InvalidArgumentError("Invalid image type."));

    auto& decoder = kDecoders[type_id];
    YSM_ASSERT(decoder != nullptr,
               absl::InvalidArgumentError("Decoder not presented."sv));
    return decoder->Decode(img, info, dst);
}

absl::StatusOr<EncodeResult> ImageEncodeLossy(BufferViewR pixels,
                                              uint32_t width, uint32_t height,
                                              BufferView dst, size_t max_width,
                                              size_t max_height) {
#ifndef YSM_ANDROID
    if (static_cast<uint64_t>(width) * height <=
            kMinLossySize * kMinLossySize &&
        width <= max_width && height <= max_height) {
        return ImageEncodeLossless(pixels, width, height, dst);
    }
    return AvifEncodeLossy(pixels, width, height, dst, max_width, max_height);
#else
    return ZtxEncodeLossless(pixels, width, height, dst, max_width, max_height);
#endif
}

absl::StatusOr<EncodeResult> ImageEncodeLossless(BufferViewR pixels,
                                                 uint32_t width,
                                                 uint32_t height,
                                                 BufferView dst) {
#ifndef YSM_ANDROID
    return WebpEncodeLossless(pixels, width, height, dst);
#else
    return ZtxEncodeLossless(pixels, width, height, dst, width, height);
#endif
}
}  // namespace ysm::codec

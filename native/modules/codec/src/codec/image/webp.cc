#include "codec/image/webp.h"

#include <webp/decode.h>
#include <webp/encode.h>

#include "log.h"
#include "scope_guard.h"

namespace ysm::codec::image {
namespace {
constexpr BufferFixed<4> kRiffSignature{0x52, 0x49, 0x46, 0x46};
constexpr BufferFixed<4> kWebpSignature{0x57, 0x45, 0x42, 0x50};
}  // namespace

bool WebpDecoder::Is(BufferViewR image_data) const noexcept {
    return image_data.size() > 12 &&
           Cmp(image_data.subspan(0, 4), kRiffSignature) &&
           Cmp(image_data.subspan(8, 4), kWebpSignature);
}

absl::StatusOr<ImageInfo> WebpDecoder::Probe(BufferViewR image_data) const {
    int width = 0;
    int height = 0;
    YSM_ASSERT(
        WebPGetInfo(image_data.data(), image_data.size(), &width, &height),
        DataCorruption());
    return ImageInfo{static_cast<uint32_t>(width),
                     static_cast<uint32_t>(height), ImageFormat::kWebp, 1};
}

absl::Status WebpDecoder::Decode(BufferViewR img, const ImageInfo& info,
                                 BufferView dst) const {
    YSM_ASSERT(WebPDecodeRGBAInto(img.data(), img.size(), dst.data(),
                                  dst.size(), info.width * 4),
               DataCorruption());
    return OkStatus();
}

absl::StatusOr<EncodeResult> WebpEncodeLossless(BufferViewR pixels,
                                                uint32_t width, uint32_t height,
                                                BufferView dst) {
    WebPPicture pic{};
    WebPPictureInit(&pic);
    auto pic_guard = ScopeGuard([&]() { WebPPictureFree(&pic); });

    pic.width = static_cast<int>(width);
    pic.height = static_cast<int>(height);
    pic.use_argb = true;
    YSM_ASSERT(WebPPictureImportRGBA(&pic, pixels.data(), width * 4),
               absl::InternalError("Failed to import pixels data"sv));

    WebPConfig cfg;
    WebPConfigInit(&cfg);
    WebPConfigLosslessPreset(&cfg, 4);

    BufferView out_buf = dst;
    pic.custom_ptr = &out_buf;
    pic.writer = [](const uint8_t* data, size_t data_size,
                    const WebPPicture* picture) -> int {
        auto& out_buf = *static_cast<BufferView*>(picture->custom_ptr);
        if (data_size > out_buf.size()) [[unlikely]] {
            return false;
        }
        Copy(BufferViewR{data, data_size}, Consume(out_buf, data_size));
        return true;
    };

    YSM_ASSERT(WebPEncode(&cfg, &pic), absl::InternalError(std::format(
                                           "Error encoding webp: {}",
                                           static_cast<int>(pic.error_code))));
    return EncodeResult{ImageInfo{width, height, ImageFormat::kWebp, 1},
                        dst.size() - out_buf.size()};
}
}  // namespace ysm::codec::image

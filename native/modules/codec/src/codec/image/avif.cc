#include "codec/image/avif.h"

#include <avif/avif.h>
#include <avif/avif_cxx.h>

#include "buffer.h"
#include "scope_guard.h"

namespace ysm::codec::image {
namespace {
constexpr BufferFixed<4> kFtypBoxType{0x66, 0x74, 0x79, 0x70};
constexpr BufferFixed<4> kAvifBrand{0x61, 0x76, 0x69, 0x66};
constexpr BufferFixed<4> kAvisBrand{0x61, 0x76, 0x69, 0x73};
}  // namespace

bool AvifDecoder::Is(BufferViewR img) const {
    return img.size() > 16 && Cmp(img.subspan(4, 4), kFtypBoxType) &&
           (Cmp(img.subspan(8, 4), kAvifBrand) ||
            Cmp(img.subspan(8, 4), kAvisBrand));
}

absl::StatusOr<ImageInfo> AvifDecoder::Probe(BufferViewR img) const {
    ::avif::DecoderPtr decoder(avifDecoderCreate());

    decoder->ignoreExif = AVIF_TRUE;
    decoder->ignoreXMP = AVIF_TRUE;

    YSM_ASSERT(avifDecoderSetIOMemory(decoder.get(), img.data(), img.size()) ==
                   AVIF_RESULT_OK,
               absl::ResourceExhaustedError("OOM"sv));
    YSM_ASSERT(avifDecoderParse(decoder.get()) == AVIF_RESULT_OK &&
                   decoder->image->width > 0 && decoder->image->height > 0,
               DataCorruption());

    return ImageInfo{decoder->image->width, decoder->image->height,
                     ImageFormat::kAvif,
                     static_cast<uint32_t>(decoder->imageCount)};
}

absl::Status AvifDecoder::Decode(BufferViewR img, const ImageInfo& info,
                                 BufferView dst) const {
    ::avif::ImagePtr yuv_image(avifImageCreateEmpty());
    {
        ::avif::DecoderPtr decoder(avifDecoderCreate());
        decoder->codecChoice = avifCodecChoice::AVIF_CODEC_CHOICE_DAV1D;
        decoder->ignoreExif = AVIF_TRUE;
        decoder->ignoreXMP = AVIF_TRUE;
        decoder->maxThreads = 1;

        YSM_ASSERT(
            avifDecoderReadMemory(decoder.get(), yuv_image.get(), img.data(),
                                  img.size()) == AVIF_RESULT_OK,
            DataCorruption());
    }

    avifRGBImage rgb_image{};
    rgb_image.format = avifRGBFormat::AVIF_RGB_FORMAT_RGBA;
    rgb_image.depth = 8;
    rgb_image.width = yuv_image->width;
    rgb_image.height = yuv_image->height;
    rgb_image.rowBytes = info.width * 4;
    rgb_image.chromaUpsampling =
        avifChromaUpsampling::AVIF_CHROMA_UPSAMPLING_BEST_QUALITY;
    rgb_image.pixels = dst.data();
    rgb_image.maxThreads = 1;

    YSM_ASSERT(avifImageYUVToRGB(yuv_image.get(), &rgb_image) == AVIF_RESULT_OK,
               DataCorruption());

    return OkStatus();
}

absl::StatusOr<EncodeResult> AvifEncodeLossy(BufferViewR pixels, uint32_t width,
                                             uint32_t height, BufferView dst,
                                             size_t max_width,
                                             size_t max_height) {
    std::unique_ptr<avifImage, absl::FunctionRef<void(avifImage*)>> yuv_image(
        avifImageCreateEmpty(), avifImageDestroy);
    yuv_image->width = width;
    yuv_image->height = height;
    yuv_image->depth = 8;
    yuv_image->yuvFormat = avifPixelFormat::AVIF_PIXEL_FORMAT_YUV420;
    {
        avifRGBImage rgb_image{};
        rgb_image.width = width;
        rgb_image.height = height;
        rgb_image.depth = 8;
        rgb_image.format = avifRGBFormat::AVIF_RGB_FORMAT_RGBA;
        rgb_image.rowBytes = width * 4;
        rgb_image.pixels = const_cast<uint8_t*>(pixels.data());
        rgb_image.chromaDownsampling =
            avifChromaDownsampling::AVIF_CHROMA_DOWNSAMPLING_SHARP_YUV;
        rgb_image.maxThreads = 1;
        YSM_ASSERT(
            avifImageRGBToYUV(yuv_image.get(), &rgb_image) == AVIF_RESULT_OK,
            DataCorruption());
    }

    if (width > max_width || height > max_height) {
        uint32_t dst_width, dst_height;
        if (static_cast<float>(width) / height >=
            static_cast<float>(max_width) / max_height) {
            dst_width = max_width;
            dst_height = static_cast<uint32_t>(
                std::round(static_cast<float>(height) * max_width / width));
        } else {
            dst_height = max_height;
            dst_width = static_cast<uint32_t>(
                std::round(static_cast<float>(width) * max_height / height));
        }
        avifDiagnostics diag{};
        YSM_ASSERT(avifImageScale(yuv_image.get(), dst_width, dst_height,
                                  &diag) == AVIF_RESULT_OK,
                   DataCorruption());
    }

    avifRWData output(AVIF_DATA_EMPTY);
    auto output_guard = ScopeGuard([&] { avifRWDataFree(&output); });
    {
        ::avif::EncoderPtr encoder(avifEncoderCreate());
        encoder->autoTiling = AVIF_TRUE;
        encoder->codecChoice = avifCodecChoice::AVIF_CODEC_CHOICE_SVT;
        encoder->quality = 75;
        encoder->qualityAlpha = AVIF_QUALITY_BEST;
        encoder->speed = 5;
        encoder->maxThreads = 1;
        YSM_ASSERT(avifEncoderWrite(encoder.get(), yuv_image.get(), &output) ==
                       AVIF_RESULT_OK,
                   absl::InternalError("Error encoding avif"sv));
    }

    YSM_ASSERT(output.size <= dst.size(),
               absl::ResourceExhaustedError("Encoded image too large"sv));

    Copy(BufferView{output.data, output.size}, dst);
    return EncodeResult{
        ImageInfo{yuv_image->width, yuv_image->height, ImageFormat::kAvif, 1},
        output.size};
}
}  // namespace ysm::codec::image

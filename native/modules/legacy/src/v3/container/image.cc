#include "image.h"

#include <algorithm>
#include <cstdint>
#include <string>
#include <utility>

#include <codec/image.h>
#include <err.h>

namespace ysm::legacy::v3::container {
namespace {
constexpr std::uint32_t kImageDimensionLimit = 4'096;
constexpr std::size_t kCodecHeadroom = 1024 * 1024;

::ysm::codec::ImageFormat CodecFormat(ImageEncoding encoding) noexcept {
    switch (encoding) {
        case ImageEncoding::kRgba:
            return ::ysm::codec::ImageFormat::kRgba;
        case ImageEncoding::kPng:
            return ::ysm::codec::ImageFormat::kPng;
        case ImageEncoding::kJpeg:
            return ::ysm::codec::ImageFormat::kJpeg;
        case ImageEncoding::kWebp:
            return ::ysm::codec::ImageFormat::kWebp;
        case ImageEncoding::kAvif:
            return ::ysm::codec::ImageFormat::kAvif;
        case ImageEncoding::kZtx:
            return ::ysm::codec::ImageFormat::kZtx;
    }
    return ::ysm::codec::ImageFormat::kSize;
}

absl::StatusOr<ImageEncoding> ImageEncodingFor(
    ::ysm::codec::ImageFormat format) {
    switch (format) {
        case ::ysm::codec::ImageFormat::kPng:
            return ImageEncoding::kPng;
        case ::ysm::codec::ImageFormat::kJpeg:
            return ImageEncoding::kJpeg;
        case ::ysm::codec::ImageFormat::kWebp:
            return ImageEncoding::kWebp;
        case ::ysm::codec::ImageFormat::kAvif:
            return ImageEncoding::kAvif;
        case ::ysm::codec::ImageFormat::kZtx:
            return ImageEncoding::kZtx;
        case ::ysm::codec::ImageFormat::kRgba:
        case ::ysm::codec::ImageFormat::kSize:
            break;
    }
    return absl::FailedPreconditionError(
        "Shared image codec produced an invalid format");
}

absl::Status ValidateHistoricalImage(const LegacyImage& image) {
    if (image.width == 0 || image.height == 0) {
        return absl::DataLossError("Historical image has an empty dimension");
    }
    if (image.width > kImageDimensionLimit ||
        image.height > kImageDimensionLimit) {
        return absl::ResourceExhaustedError(
            "Historical image dimension exceeds its limit");
    }
    if (image.frame_count != 1) {
        return absl::DataLossError("Historical image is not static");
    }
    const auto raw_size =
        static_cast<std::uint64_t>(image.width) * image.height * 4U;
    if (image.encoding == ImageEncoding::kRgba) {
        if (raw_size != image.bytes.size()) {
            return absl::DataLossError(
                "Historical RGBA image length is inconsistent");
        }
        return absl::OkStatus();
    }

    const auto probed = ::ysm::codec::ImageProbe(BufferViewR(image.bytes));
    if (!probed.ok()) {
        return absl::DataLossError(
            "Historical encoded image cannot be decoded");
    }
    if (probed->format != CodecFormat(image.encoding) ||
        probed->width != image.width || probed->height != image.height ||
        probed->frame_count != 1) {
        return absl::DataLossError(
            "Historical encoded image metadata is inconsistent");
    }
    return absl::OkStatus();
}

absl::StatusOr<::ysm::codec::EncodeResult> EncodeForRole(BufferViewR pixels,
                                                         std::uint32_t width,
                                                         std::uint32_t height,
                                                         BufferView output,
                                                         ImageRole role) {
    switch (role) {
        case ImageRole::kTexture:
            return ::ysm::codec::ImageEncodeLossless(pixels, width, height,
                                                     output);
        case ImageRole::kGui:
            return ::ysm::codec::ImageEncodeLossy(pixels, width, height, output,
                                                  260, 450);
        case ImageRole::kAvatar:
            return ::ysm::codec::ImageEncodeLossy(pixels, width, height, output,
                                                  320, 320);
        case ImageRole::kIcon:
            return ::ysm::codec::ImageEncodeLossy(pixels, width, height, output,
                                                  192, 192);
        case ImageRole::kThumbnail:
            return ::ysm::codec::ImageEncodeLossy(pixels, width, height, output,
                                                  156, 270);
    }
    return absl::FailedPreconditionError("Image role was not selected");
}
}  // namespace

absl::Status PrepareImage(LegacyImage& image, ImageRole role) {
    if (const auto status = ValidateHistoricalImage(image); !status.ok()) {
        return status;
    }
    image.role = role;
    if (image.encoding != ImageEncoding::kRgba &&
        image.encoding != ImageEncoding::kPng) {
        return absl::OkStatus();
    }

    const auto raw_size = static_cast<std::size_t>(image.width) * image.height *
                          static_cast<std::size_t>(4);
    BufferManaged pixels;
    if (image.encoding == ImageEncoding::kRgba) {
        pixels = std::move(image.bytes);
    } else {
        pixels.resize(raw_size);
        const ::ysm::codec::ImageInfo source_info{
            image.width, image.height, ::ysm::codec::ImageFormat::kPng, 1};
        if (!::ysm::codec::ImageDecode(BufferViewR(image.bytes), source_info,
                                       pixels)
                 .ok()) {
            return absl::DataLossError(
                "Historical PNG image cannot be decoded");
        }
        image.bytes.reset();
    }

    if (raw_size > BufferManaged::kMaxSize - kCodecHeadroom) {
        return absl::FailedPreconditionError(
            "Image output cannot fit a native buffer");
    }
    const auto output_capacity = raw_size + kCodecHeadroom;
    BufferManaged output(output_capacity);
    auto encoded = EncodeForRole(BufferViewR(pixels), image.width, image.height,
                                 output, role);
    if (!encoded.ok() || encoded->size > output.size()) {
        return absl::FailedPreconditionError("Shared image encoding failed");
    }
    output.resize(encoded->size);
    const auto probed = ::ysm::codec::ImageProbe(BufferViewR(output));
    if (!probed.ok() || probed->format != encoded->info.format ||
        probed->width != encoded->info.width ||
        probed->height != encoded->info.height || probed->frame_count != 1) {
        return absl::FailedPreconditionError(
            "Shared image output does not round trip");
    }

    ImageEncoding encoding;
    YSM_ASSIGN_OR_RETURN(encoding, ImageEncodingFor(encoded->info.format));
    pixels.reset();
    image.bytes = std::move(output);
    image.width = encoded->info.width;
    image.height = encoded->info.height;
    image.encoding = encoding;
    image.frame_count = 1;
    return absl::OkStatus();
}

absl::Status ValidatePreparedImage(const LegacyImage& image, ImageRole role) {
    if (image.role != role || image.width == 0 || image.height == 0 ||
        image.frame_count != 1 || image.encoding == ImageEncoding::kRgba) {
        return absl::FailedPreconditionError(
            "Prepared image metadata is not target-representable");
    }
    const auto probed = ::ysm::codec::ImageProbe(BufferViewR(image.bytes));
    if (!probed.ok() || probed->format != CodecFormat(image.encoding) ||
        probed->width != image.width || probed->height != image.height ||
        probed->frame_count != 1) {
        return absl::FailedPreconditionError(
            "Prepared image tuple is not target-representable");
    }
    return absl::OkStatus();
}
}  // namespace ysm::legacy::v3::container

#include "codec/image/jpeg.h"

#include <turbojpeg.h>

namespace ysm::codec::image {
namespace {
constexpr BufferFixed<3> kJpegStart{0xFF, 0xD8, 0xFF};
constexpr Byte kJfifMarker = 0xE0;
constexpr Byte kExifMarker = 0xE1;

struct HandleDeleter {
    void operator()(void* ptr) const noexcept { tj3Destroy(ptr); }
};

using HandlePtr = std::unique_ptr<void, HandleDeleter>;
}  // namespace

bool JpegDecoder::Is(BufferViewR img) const {
    return img.size() > 16 && Cmp(img.subspan(0, 3), kJpegStart) &&
           (img[3] == kJfifMarker || img[3] == kExifMarker);
}

absl::StatusOr<ImageInfo> JpegDecoder::Probe(BufferViewR img) const {
    HandlePtr handle(tj3Init(TJINIT::TJINIT_DECOMPRESS));
    YSM_ASSERT(0 == tj3DecompressHeader(handle.get(), img.data(), img.size()),
               DataCorruption());

    auto width = tj3Get(handle.get(), TJPARAM::TJPARAM_JPEGWIDTH);
    auto height = tj3Get(handle.get(), TJPARAM::TJPARAM_JPEGHEIGHT);

    YSM_ASSERT(width > 0 && height > 0, DataCorruption());
    return ImageInfo{static_cast<uint32_t>(width),
                     static_cast<uint32_t>(height), ImageFormat::kJpeg, 1};
}

absl::Status JpegDecoder::Decode(BufferViewR img, const ImageInfo& info,
                                 BufferView dst) const {
    HandlePtr handle(tj3Init(TJINIT::TJINIT_DECOMPRESS));
    YSM_ASSERT(0 == tj3Decompress8(handle.get(), img.data(), img.size(),
                                   dst.data(), info.width * 4, TJPF::TJPF_RGBA),
               DataCorruption());
    return OkStatus();
}
}  // namespace ysm::codec::image

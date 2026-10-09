#include "codec/image/png.h"

#include <spng.h>
#include <memory>

#include "mimalloc.h"

namespace ysm::codec::image {
namespace {
constexpr BufferFixed<8> kPngSignature{0x89, 0x50, 0x4E, 0x47,
                                       0x0D, 0x0A, 0x1A, 0x0A};

#define YSM_SPNG_ASSERT(expr)                                                \
    {                                                                        \
        auto ret = (expr);                                                   \
        YSM_ASSERT(ret == SPNG_OK, absl::InternalError(spng_strerror(ret))); \
    }

constexpr struct CtxDeleter {
    void operator()(spng_ctx* ptr) const noexcept { spng_ctx_free(ptr); }
} kDeleter;

spng_alloc allocator{
    &mi_malloc,
    &mi_realloc,
    &mi_calloc,
    &mi_free,
};

absl::StatusOr<std::unique_ptr<spng_ctx, CtxDeleter>> CreateContext(int flags) {
    auto ptr = std::unique_ptr<spng_ctx, CtxDeleter>(
        spng_ctx_new2(&allocator, flags), kDeleter);
    YSM_ASSERT(ptr,
               absl::ResourceExhaustedError("Png codec allocation failed."sv));
    return ptr;
}
}  // namespace

bool PngDecoder::Is(BufferViewR img) const {
    return img.size() > 16 && Cmp(img.subspan(0, 8), kPngSignature);
}

absl::StatusOr<ImageInfo> PngDecoder::Probe(BufferViewR img) const {
    YSM_DECLARE_OR_RETURN(ctx, CreateContext(0));
    YSM_SPNG_ASSERT(spng_set_png_buffer(ctx.get(), img.data(), img.size()));

    spng_ihdr ihdr{};
    YSM_SPNG_ASSERT(spng_get_ihdr(ctx.get(), &ihdr));
    YSM_ASSERT(ihdr.width > 0 && ihdr.height > 0, DataCorruption());

    return ImageInfo{ihdr.width, ihdr.height, ImageFormat::kPng, 1};
}

absl::Status PngDecoder::Decode(BufferViewR img, const ImageInfo& info,
                                BufferView dst) const {
    YSM_DECLARE_OR_RETURN(ctx, CreateContext(SPNG_CTX_IGNORE_ADLER32));
    YSM_SPNG_ASSERT(spng_set_png_buffer(ctx.get(), img.data(), img.size()));

    size_t decoded_size = info.width * info.height * 4;
    YSM_ASSERT(dst.size() >= decoded_size,
               absl::ResourceExhaustedError("Dst buffer too small"sv));
    YSM_SPNG_ASSERT(spng_decode_image(ctx.get(), dst.data(), decoded_size,
                                      SPNG_FMT_RGBA8, SPNG_DECODE_TRNS));

    return OkStatus();
}
}  // namespace ysm::codec::image

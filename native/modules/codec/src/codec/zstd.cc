#include "codec/zstd.h"

#include <zstd.h>
#include <algorithm>
#include <memory>
#include <new>
#include <stdexcept>

#include <absl/strings/str_cat.h>

namespace ysm::codec {
namespace {
struct ZstdDStreamDeleter {
    void operator()(ZSTD_DStream* stream) const noexcept {
        ZSTD_freeDStream(stream);
    }
};

absl::Status ZstdStatus(size_t result) {
    const auto* error_name = ZSTD_getErrorName(result);
    switch (ZSTD_getErrorCode(result)) {
        case ZSTD_error_memory_allocation:
            return absl::ResourceExhaustedError(
                absl::StrCat("zstd memory allocation failed: ", error_name));
        case ZSTD_error_dstSize_tooSmall:
        case ZSTD_error_noForwardProgress_destFull:
            return absl::ResourceExhaustedError(
                absl::StrCat("zstd output exceeds destination size: ",
                             error_name));
        case ZSTD_error_parameter_unsupported:
        case ZSTD_error_parameter_combination_unsupported:
        case ZSTD_error_parameter_outOfBound:
            return absl::InvalidArgumentError(
                absl::StrCat("zstd parameter error: ", error_name));
        default:
            return absl::DataLossError(
                absl::StrCat("zstd frame is invalid or truncated: ",
                             error_name));
    }
}

absl::Status ResizeOutput(BufferManaged& dst, size_t size) {
    if (size > BufferManaged::kMaxSize) {
        return absl::ResourceExhaustedError("zstd output too large.");
    }
    try {
        dst.resize(size);
    } catch (const std::bad_alloc&) {
        return absl::ResourceExhaustedError("zstd output allocation failed.");
    } catch (const std::length_error&) {
        return absl::ResourceExhaustedError("zstd output too large.");
    }
    return OkStatus();
}

absl::Status GrowOutput(BufferManaged& dst) {
    const size_t min_size = std::max<size_t>(1, ZSTD_DStreamOutSize());
    const size_t next_size = dst.empty() ? min_size : dst.size() * 2;
    if (!dst.empty() && next_size <= dst.size()) {
        return absl::ResourceExhaustedError("zstd output too large.");
    }
    return ResizeOutput(dst, next_size);
}

template <bool kAutoGrow>
absl::StatusOr<size_t> Decompress(BufferViewR input, auto&& dst) {
    std::unique_ptr<ZSTD_DStream, ZstdDStreamDeleter> stream(
        ZSTD_createDStream());
    YSM_ASSERT(stream != nullptr, absl::ResourceExhaustedError(
                                      "zstd stream allocation failed."sv));

    const size_t init_result = ZSTD_initDStream(stream.get());

    if (ZSTD_isError(init_result)) {
        return ZstdStatus(init_result);
    }

    ZSTD_inBuffer in{input.data(), input.size(), 0};
    size_t output_offset = 0;

    while (true) {
        if (output_offset == dst.size()) {
            if constexpr (kAutoGrow) {
                YSM_RETURN_IF_ERROR(GrowOutput(dst));
            } else {
                return absl::ResourceExhaustedError("Dst buffer too small.");
            }
        }

        const size_t input_pos_before = in.pos;
        const size_t output_offset_before = output_offset;
        ZSTD_outBuffer out{dst.data() + output_offset,
                           dst.size() - output_offset, 0};

        const size_t result = ZSTD_decompressStream(stream.get(), &out, &in);
        output_offset += out.pos;
        if (ZSTD_isError(result)) {
            return ZstdStatus(result);
        }
        if (result == 0 && in.pos == in.size) {
            break;
        }
        if (in.pos == input_pos_before &&
            output_offset == output_offset_before) {
            return DataCorruption();
        }
    }

    return output_offset;
}
}  // namespace

absl::StatusOr<size_t> ZstdGetCompressMaxSize(size_t uncompress_size) {
    auto bound = ZSTD_compressBound(uncompress_size);
    if (ZSTD_isError(bound)) {
        return absl::ResourceExhaustedError("zstd input too large.");
    }
    return bound;
}

absl::StatusOr<size_t> ZstdCompress(BufferViewR input, BufferView dst,
                                    int level) {
    YSM_ASSERT(
        level >= ZSTD_minCLevel() && level <= ZSTD_maxCLevel(),
        absl::InvalidArgumentError("zstd compression level out of range."));

    const size_t written = ZSTD_compress(dst.data(), dst.size(), input.data(),
                                         input.size(), level);
    if (ZSTD_isError(written)) {
        return ZstdStatus(written);
    }

    return written;
}

absl::Status ZstdDecompress(BufferViewR input, BufferManaged& dst) {
    if (dst.empty()) {
        dst.resize(1);
    }

    YSM_DECLARE_OR_RETURN(size, Decompress<true>(input, dst));

    dst.resize(size);
    return OkStatus();
}

absl::StatusOr<size_t> ZstdDecompress(BufferViewR input, BufferView dst) {
    const size_t written =
        ZSTD_decompress(dst.data(), dst.size(), input.data(), input.size());
    if (ZSTD_isError(written)) {
        return ZstdStatus(written);
    }
    return written;
}
}  // namespace ysm::codec

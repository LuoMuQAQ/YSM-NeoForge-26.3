#include "codec/zlib.h"

#include <zlib.h>
#include <algorithm>
#include <limits>
#include <new>
#include <stdexcept>

namespace ysm::codec {
namespace {
constexpr size_t kMaxZlibAvail =
    static_cast<size_t>(std::numeric_limits<uInt>::max());

uInt ChunkSize(size_t size) {
    return static_cast<uInt>(std::min(size, kMaxZlibAvail));
}

absl::Status ZlibStatus(int err) {
    switch (err) {
        case Z_MEM_ERROR:
            return absl::ResourceExhaustedError("zlib out of memory.");
        case Z_VERSION_ERROR:
        case Z_STREAM_ERROR:
            return absl::InternalError("zlib stream error.");
        case Z_NEED_DICT:
        case Z_DATA_ERROR:
        case Z_BUF_ERROR:
        default:
            return DataCorruption();
    }
}

absl::Status GrowOutput(BufferManaged& dst) {
    if (dst.size() > BufferManaged::kMaxSize) {
        return absl::ResourceExhaustedError("zlib output too large.");
    }
    try {
        dst.resize(dst.size() * 2);
    } catch (const std::bad_alloc&) {
        return absl::ResourceExhaustedError("zlib output allocation failed.");
    } catch (const std::length_error&) {
        return absl::ResourceExhaustedError("zlib output too large.");
    }
    return OkStatus();
}

template <bool kAutoGrow>
absl::StatusOr<size_t> Decompress(BufferViewR input, auto&& dst) {
    z_stream stream{};
    stream.zalloc = nullptr;
    stream.zfree = nullptr;
    stream.opaque = nullptr;

    int err = inflateInit(&stream);
    YSM_ASSERT(err == Z_OK, ZlibStatus(err));

    size_t input_offset = 0;
    size_t output_offset = 0;
    absl::Status status = OkStatus();

    while (true) {
        if (stream.avail_in == 0 && input_offset < input.size()) {
            const size_t remaining = input.size() - input_offset;
            const uInt chunk = ChunkSize(remaining);
            stream.next_in = const_cast<Byte*>(input.data() + input_offset);
            stream.avail_in = chunk;
            input_offset += chunk;
        }

        if (stream.avail_out == 0) {
            if (output_offset == dst.size()) {
                if constexpr (kAutoGrow) {
                    status = GrowOutput(dst);
                    if (!status.ok()) {
                        break;
                    }
                } else {
                    return absl::ResourceExhaustedError("Dst buffer too small");
                }
            }
            const size_t remaining = dst.size() - output_offset;
            stream.next_out = dst.data() + output_offset;
            stream.avail_out = ChunkSize(remaining);
        }

        err = inflate(&stream, Z_NO_FLUSH);
        output_offset = static_cast<size_t>(stream.next_out - dst.data());
        if (err == Z_STREAM_END) {
            break;
        }
        if (err != Z_OK) {
            status = ZlibStatus(err);
            break;
        }
    }

    const int end_err = inflateEnd(&stream);
    if (!status.ok()) {
        return status;
    }
    YSM_ASSERT(end_err == Z_OK, ZlibStatus(end_err));
    YSM_ASSERT(err == Z_STREAM_END, DataCorruption());

    if constexpr (kAutoGrow) {
        dst.resize(output_offset);
    }

    return output_offset;
}
}  // namespace

absl::Status ZlibDecompress(BufferViewR input, BufferManaged& dst) {
    if (dst.empty()) {
        dst.resize(1);
    }

    YSM_RETURN_IF_ERROR(Decompress<true>(input, dst));

    return OkStatus();
}

absl::StatusOr<size_t> ZlibDecompress(BufferViewR input, BufferView dst) {
    return Decompress<false>(input, dst);
}
}  // namespace ysm::codec

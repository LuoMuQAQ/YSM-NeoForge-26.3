#include "codec/hex.h"

#include "absl/container/flat_hash_map.h"

namespace ysm::codec {
namespace {
constexpr int HexValue(const Byte c) {
    if (c >= '0' && c <= '9')
        return c - '0';
    if (c >= 'a' && c <= 'f')
        return c - 'a' + 10;
    if (c >= 'A' && c <= 'F')
        return c - 'A' + 10;
    return -1;
}

constexpr char kHexTable[] = "0123456789abcdef";
}  // namespace

absl::Status HexEncode(BufferViewR input, BufferView output) {
    if (input.empty()) [[unlikely]] {
        return OkStatus();
    }
    YSM_ASSERT(output.size() >= HexEncodeOutputSize(input.size()),
               absl::InvalidArgumentError("Output is not large enough"sv));

    auto iter = output.begin();
    for (Byte b : input) {
        *iter++ = kHexTable[b >> 4];
        *iter++ = kHexTable[b & 0x0F];
    }

    return OkStatus();
}

absl::Status HexDecode(BufferViewR input, BufferView output) {
    if (input.empty()) [[unlikely]] {
        return OkStatus();
    }
    YSM_ASSERT(input.size() % 2 == 0,
               absl::InvalidArgumentError("Invalid input size"sv));
    auto decode_size = HexDecodeOutputSize(input.size());
    YSM_ASSERT(output.size() >= decode_size,
               absl::InvalidArgumentError("Output is not large enough"sv));

    for (size_t i = 0; i < decode_size; ++i) {
        int hi = HexValue(input[i * 2]);
        int lo = HexValue(input[i * 2 + 1]);

        YSM_ASSERT(hi >= 0 && lo >= 0,
                   absl::InvalidArgumentError("Invalid input"sv));

        output[i] = static_cast<Byte>((hi << 4) | lo);
    }

    return OkStatus();
}
}  // namespace ysm::codec

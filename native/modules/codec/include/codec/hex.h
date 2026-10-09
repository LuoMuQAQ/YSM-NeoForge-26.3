#pragma once

#include <string>
#include "buffer.h"
#include "err.h"

namespace ysm::codec {
constexpr size_t HexEncodeOutputSize(size_t data_size) {
    return data_size * 2;
}

constexpr size_t HexDecodeOutputSize(size_t hex_size) {
    return hex_size / 2;
}

absl::Status HexEncode(BufferViewR input, BufferView output);

absl::Status HexDecode(BufferViewR input, BufferView output);

inline absl::StatusOr<std::string> HexEncode(BufferViewR input) {
    std::string output(HexEncodeOutputSize(input.size()), '\0');
    YSM_RETURN_IF_ERROR(HexEncode(input, StrBuf(output)));
    return output;
}
}  // namespace ysm::codec
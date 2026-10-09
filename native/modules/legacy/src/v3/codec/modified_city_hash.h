#pragma once

#include <cstdint>

#include <buffer.h>

namespace ysm::legacy::v3::codec {
std::uint64_t ModifiedCityHash64(BufferViewR bytes) noexcept;
std::uint64_t ModifiedCityHash64WithSeed(BufferViewR bytes,
                                         std::uint64_t seed) noexcept;
}  // namespace ysm::legacy::v3::codec

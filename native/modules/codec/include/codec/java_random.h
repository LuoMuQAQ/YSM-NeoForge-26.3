#pragma once

#include <cstdint>

#include "buffer.h"

namespace ysm::codec {
class JavaRandom {
    uint64_t seed_;

    uint32_t Next(uint32_t bits) noexcept;

   public:
    explicit JavaRandom(uint64_t seed) noexcept;

    void NextBytes(BufferView bytes) noexcept;

    uint32_t NextInt() noexcept;
};
}  // namespace ysm::codec

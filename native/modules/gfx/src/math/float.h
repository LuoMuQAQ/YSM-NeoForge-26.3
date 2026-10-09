#pragma once

#include <bit>
#include <cstdint>

#include "inline.h"

namespace ysm::gfx::math {
[[nodiscard]] YSM_INLINE constexpr bool SignBit(float value) noexcept {
    return (std::bit_cast<uint32_t>(value) & 0x80000000U) != 0;
}
}  // namespace ysm::gfx::math

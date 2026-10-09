#pragma once

#include <bit>
#include <cmath>
#include <cstdint>
#include <limits>

namespace ysm::gfx::renderer::buffer {
constexpr uint32_t kInvalidDepthSortKey =
    std::numeric_limits<uint32_t>::max();

constexpr uint32_t NdcDepthSortKey(float depth) noexcept {
    if (!std::isfinite(depth)) [[unlikely]] {
        return kInvalidDepthSortKey;
    }
    if (depth == 0.0f) [[unlikely]] {
        depth = 0.0f;
    }
    const auto bits = std::bit_cast<uint32_t>(depth);
    const auto ascending_key =
        bits ^ ((bits & 0x80000000U) != 0 ? 0xffffffffU : 0x80000000U);
    return ~ascending_key;
}

#pragma pack(push, 1)

struct QuadRef {
    uint32_t vertex_offset;
    uint32_t depth_key;
};

#pragma pack(pop)

static_assert(sizeof(uint64_t) == sizeof(QuadRef));
}  // namespace ysm::gfx::renderer::buffer

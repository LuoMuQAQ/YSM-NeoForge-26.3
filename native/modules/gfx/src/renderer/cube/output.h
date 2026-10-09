#pragma once

#include "gfx/bake/baked_model.h"

namespace ysm::gfx::renderer::cube {
template <typename CubeGroupType>
struct alignas(simd::bytes(CubeGroupType::kVecWidth)) CubeOutput {
    static constexpr size_t kCubeGroupCapacity = CubeGroupType::kCubeGroupCapacity;
    static constexpr size_t kQuadAttrCapacity = CubeGroupType::kQuadAttrCapacity;

    std::array<std::array<float, 8 * kCubeGroupCapacity>, 3> pos;
    std::array<uint32_t, kQuadAttrCapacity> normal;
    std::array<uint32_t, kQuadAttrCapacity> tangent;
    std::array<float, kQuadAttrCapacity> face_depth;
    std::array<bool, kQuadAttrCapacity> back_face;
};
}

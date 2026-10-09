#pragma once

#include <array>

#include "cpu.h"

namespace ysm::gfx::renderer::buffer::iris {
template<simd::Width kVecWidth>
struct alignas(simd::bytes(kVecWidth)) QuadBatch {
    std::array<std::array<std::array<float, simd::floats(kVecWidth)>, 3>, 3> pos;
    std::array<std::array<std::array<float, simd::floats(kVecWidth)>, 2>, 3> uv;
    std::array<std::array<float, simd::floats(kVecWidth)>, 3> normal;
};
}
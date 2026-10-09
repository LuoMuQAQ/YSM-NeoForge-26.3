#pragma once

#include <cglm/vec3.h>
#include <cstdint>

#include <span>

#include "cpu.h"
#include "quad_batch.h"
#include "renderer/buffer/normal.h"

#ifdef YSM_X64

#elif defined(YSM_AARCH64)

#endif

namespace ysm::gfx::renderer::buffer::iris {
// 参考实现
template <typename VertexType>
void ComputeFaceNormal(vec4 saveTo, std::span<VertexType, 4> q) noexcept {
    vec3 diagonal0{
        q[2].x - q[0].x,
        q[2].y - q[0].y,
        q[2].z - q[0].z
    };

    vec3 diagonal1{
        q[3].x - q[1].x,
        q[3].y - q[1].y,
        q[3].z - q[1].z
    };

    glm_vec3_crossn(diagonal0, diagonal1, saveTo);
}

template <typename VertexType>
uint32_t ComputeTangent(vec4 normal, std::span<VertexType, 4> t) noexcept {
    vec3 edge1{
        t[1].x - t[0].x,
        t[1].y - t[0].y,
        t[1].z - t[0].z
    };

    vec3 edge2{
        t[2].x - t[0].x,
        t[2].y - t[0].y,
        t[2].z - t[0].z
    };

    const float deltaU1 = t[1].texU - t[0].texU;
    const float deltaV1 = t[1].texV - t[0].texV;
    const float deltaU2 = t[2].texU - t[0].texU;
    const float deltaV2 = t[2].texV - t[0].texV;

    const float denominator =
        deltaU1 * deltaV2 -
        deltaU2 * deltaV1;

    const float f = denominator != 0.0f
        ? 1.0f / denominator
        : 1.0f;

    vec4 tangent;

    glm_vec3_scale(edge1, deltaV2, tangent);
    glm_vec3_mulsubs(edge2, deltaV1, tangent);
    glm_vec3_scale(tangent, f, tangent);
    glm_vec3_normalize(tangent);

    vec3 bitangent;

    glm_vec3_scale(edge2, deltaU1, bitangent);
    glm_vec3_mulsubs(edge1, deltaU2, bitangent);
    glm_vec3_scale(bitangent, f, bitangent);

    vec3 projectedBitangent;
    glm_vec3_cross(tangent, normal, projectedBitangent);

    tangent[3] = glm_vec3_dot(bitangent, projectedBitangent) < 0.0f
        ? -1.0f
        : 1.0f;

    return PackTangent(tangent);
}

template <bool kFull, typename VertexType, simd::Type kSimdType>
void ComputeExtendedField(simd::Tag<kSimdType>, const QuadBatch<simd::width<kSimdType>()>& quadBatch,
                          const std::span<VertexType> vertices) noexcept {
    // TODO
}
}  // namespace ysm::gfx::renderer::buffer::iris

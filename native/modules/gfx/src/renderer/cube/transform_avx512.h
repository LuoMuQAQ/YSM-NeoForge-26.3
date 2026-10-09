#pragma once

#include <immintrin.h>
#include <limits>

#include "gfx/bake/baked_model.h"
#include "renderer/render_state.h"
#include "cpu.h"
#include "fast_math.h"
#include "inline.h"
#include "output.h"
#include "transform_sse41.h"

YSM_FAST_MATH_BEGIN

namespace ysm::gfx::renderer::cube {

namespace internal {

struct Avx512Vec3 {
    __m512 x;
    __m512 y;
    __m512 z;
};

YSM_TARGET_AVX512 YSM_INLINE __m512
InverseSqrtAvx512(__m512 value, __mmask16 valid) noexcept {
#if YSM_TRANSFORM_USE_APPROXIMATE_RSQRT
    return _mm512_maskz_rsqrt14_ps(valid, value);
#else
    return _mm512_maskz_div_ps(valid, _mm512_set1_ps(1.0f),
                               _mm512_sqrt_ps(value));
#endif
}

YSM_TARGET_AVX512 YSM_INLINE Avx512Vec3
TransformPointAvx512(const mat4 matrix, __m512 x, __m512 y, __m512 z) noexcept {
    const auto x_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][0]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][0])));
    const auto x_zt = _mm512_fmadd_ps(z, _mm512_set1_ps(matrix[2][0]),
                                      _mm512_set1_ps(matrix[3][0]));
    const auto y_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][1]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][1])));
    const auto y_zt = _mm512_fmadd_ps(z, _mm512_set1_ps(matrix[2][1]),
                                      _mm512_set1_ps(matrix[3][1]));
    const auto z_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][2]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][2])));
    const auto z_zt = _mm512_fmadd_ps(z, _mm512_set1_ps(matrix[2][2]),
                                      _mm512_set1_ps(matrix[3][2]));
    return {_mm512_add_ps(x_xy, x_zt), _mm512_add_ps(y_xy, y_zt),
            _mm512_add_ps(z_xy, z_zt)};
}

YSM_TARGET_AVX512 YSM_INLINE Avx512Vec3 TransformDirectionAvx512(
    const mat3 matrix, __m512 x, __m512 y, __m512 z) noexcept {
    const auto x_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][0]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][0])));
    const auto x_z = _mm512_mul_ps(z, _mm512_set1_ps(matrix[2][0]));
    const auto y_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][1]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][1])));
    const auto y_z = _mm512_mul_ps(z, _mm512_set1_ps(matrix[2][1]));
    const auto z_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][2]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][2])));
    const auto z_z = _mm512_mul_ps(z, _mm512_set1_ps(matrix[2][2]));
    return {_mm512_add_ps(x_xy, x_z), _mm512_add_ps(y_xy, y_z),
            _mm512_add_ps(z_xy, z_z)};
}

YSM_TARGET_AVX512 YSM_INLINE Avx512Vec3 TransformDirectionAvx512(
    const mat4 matrix, __m512 x, __m512 y, __m512 z) noexcept {
    const auto x_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][0]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][0])));
    const auto x_z = _mm512_mul_ps(z, _mm512_set1_ps(matrix[2][0]));
    const auto y_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][1]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][1])));
    const auto y_z = _mm512_mul_ps(z, _mm512_set1_ps(matrix[2][1]));
    const auto z_xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(matrix[1][2]),
                        _mm512_mul_ps(x, _mm512_set1_ps(matrix[0][2])));
    const auto z_z = _mm512_mul_ps(z, _mm512_set1_ps(matrix[2][2]));
    return {_mm512_add_ps(x_xy, x_z), _mm512_add_ps(y_xy, y_z),
            _mm512_add_ps(z_xy, z_z)};
}

YSM_TARGET_AVX512 YSM_INLINE __m512 DotAvx512(
    __m512 x, __m512 y, __m512 z, __m512 w, const vec4 coefficients) noexcept {
    const auto xy =
        _mm512_fmadd_ps(y, _mm512_set1_ps(coefficients[1]),
                        _mm512_mul_ps(x, _mm512_set1_ps(coefficients[0])));
    const auto zw =
        _mm512_fmadd_ps(w, _mm512_set1_ps(coefficients[3]),
                        _mm512_mul_ps(z, _mm512_set1_ps(coefficients[2])));
    return _mm512_add_ps(xy, zw);
}

YSM_TARGET_AVX512 YSM_INLINE void NormalizeDirectionAvx512(
    Avx512Vec3& direction) noexcept {
    const auto length_xy = _mm512_fmadd_ps(
        direction.y, direction.y, _mm512_mul_ps(direction.x, direction.x));
    const auto length_z = _mm512_mul_ps(direction.z, direction.z);
    const auto length_squared = _mm512_add_ps(length_xy, length_z);
    const auto valid =
        _mm512_cmp_ps_mask(length_squared, _mm512_setzero_ps(), _CMP_GT_OQ) &
        _mm512_cmp_ps_mask(length_squared,
                           _mm512_set1_ps(std::numeric_limits<float>::max()),
                           _CMP_LE_OQ);
    const auto inverse_length = InverseSqrtAvx512(length_squared, valid);
    direction.x = _mm512_maskz_mul_ps(valid, direction.x, inverse_length);
    direction.y = _mm512_maskz_mul_ps(valid, direction.y, inverse_length);
    direction.z = _mm512_maskz_mul_ps(valid, direction.z, inverse_length);
}

YSM_TARGET_AVX512 YSM_INLINE void NormalizeDirectionsAvx512(
    Avx512Vec3& first, Avx512Vec3& second) noexcept {
    const auto first_xy =
        _mm512_fmadd_ps(first.y, first.y, _mm512_mul_ps(first.x, first.x));
    const auto second_xy =
        _mm512_fmadd_ps(second.y, second.y, _mm512_mul_ps(second.x, second.x));
    const auto first_z = _mm512_mul_ps(first.z, first.z);
    const auto second_z = _mm512_mul_ps(second.z, second.z);
    const auto first_length_squared = _mm512_add_ps(first_xy, first_z);
    const auto second_length_squared = _mm512_add_ps(second_xy, second_z);
    const auto zero = _mm512_setzero_ps();
    const auto finite_limit = _mm512_set1_ps(std::numeric_limits<float>::max());
    const auto first_valid =
        _mm512_cmp_ps_mask(first_length_squared, zero, _CMP_GT_OQ) &
        _mm512_cmp_ps_mask(first_length_squared, finite_limit, _CMP_LE_OQ);
    const auto second_valid =
        _mm512_cmp_ps_mask(second_length_squared, zero, _CMP_GT_OQ) &
        _mm512_cmp_ps_mask(second_length_squared, finite_limit, _CMP_LE_OQ);
    const auto first_inverse =
        InverseSqrtAvx512(first_length_squared, first_valid);
    const auto second_inverse =
        InverseSqrtAvx512(second_length_squared, second_valid);
    first.x = _mm512_maskz_mul_ps(first_valid, first.x, first_inverse);
    second.x = _mm512_maskz_mul_ps(second_valid, second.x, second_inverse);
    first.y = _mm512_maskz_mul_ps(first_valid, first.y, first_inverse);
    second.y = _mm512_maskz_mul_ps(second_valid, second.y, second_inverse);
    first.z = _mm512_maskz_mul_ps(first_valid, first.z, first_inverse);
    second.z = _mm512_maskz_mul_ps(second_valid, second.z, second_inverse);
}

YSM_TARGET_AVX512 YSM_INLINE __m512i
ConvertSnorm8Avx512(__m512 value) noexcept {
    auto result =
        _mm512_cvt_roundps_epi32(_mm512_mul_ps(value, _mm512_set1_ps(127.0f)),
                                 _MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC);
    result = _mm512_max_epi32(_mm512_set1_epi32(-127),
                              _mm512_min_epi32(_mm512_set1_epi32(127), result));
    return _mm512_and_si512(result, _mm512_set1_epi32(0xff));
}

YSM_TARGET_AVX512 YSM_INLINE __m512i PackSnorm4x8Avx512(__m512 x, __m512 y,
                                                        __m512 z,
                                                        __m512 w) noexcept {
    const auto packed_x = ConvertSnorm8Avx512(x);
    const auto packed_y = _mm512_slli_epi32(ConvertSnorm8Avx512(y), 8);
    const auto packed_z = _mm512_slli_epi32(ConvertSnorm8Avx512(z), 16);
    const auto packed_w = _mm512_slli_epi32(ConvertSnorm8Avx512(w), 24);
    return _mm512_or_si512(_mm512_or_si512(packed_x, packed_y),
                           _mm512_or_si512(packed_z, packed_w));
}

}  // namespace internal

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename CubeGroupType>
    requires(CubeGroupType::kCubeGroupCapacity == 2)
YSM_TARGET_AVX512 void Transform(simd::Tag<simd::Type::AVX512>,
                                 const CubeGroupType& cube_group,
                                 const RenderBoneState& state,
                                 CubeOutput<CubeGroupType>& out) {
    if (cube_group.IsSingleQuad()) {
        internal::TransformSingleQuadSse41<
            kCulling, kIris, kHasPbr, kPosOnly>(cube_group, state, out);
        return;
    }

    const auto position_x = _mm512_load_ps(cube_group.pos[0].data());
    const auto position_y = _mm512_load_ps(cube_group.pos[1].data());
    const auto position_z = _mm512_load_ps(cube_group.pos[2].data());
    const auto position = internal::TransformPointAvx512(
        state.pose, position_x, position_y, position_z);
    _mm512_store_ps(out.pos[0].data(), position.x);
    _mm512_store_ps(out.pos[1].data(), position.y);
    _mm512_store_ps(out.pos[2].data(), position.z);

    const auto source_normal_x = _mm512_load_ps(cube_group.normal[0].data());
    const auto source_normal_y = _mm512_load_ps(cube_group.normal[1].data());
    const auto source_normal_z = _mm512_load_ps(cube_group.normal[2].data());
    const auto plane_d = _mm512_load_ps(cube_group.plane_d.data());
    auto facing =
        internal::DotAvx512(source_normal_x, source_normal_y, source_normal_z,
                            plane_d, state.facing_coeff);
    facing =
        _mm512_mul_ps(facing, _mm512_load_ps(cube_group.winding_sign.data()));
    const auto back_face =
        _mm512_cmp_ps_mask(facing, _mm512_setzero_ps(), _CMP_LE_OQ);
    _mm_storeu_si128(reinterpret_cast<__m128i*>(out.back_face.data()),
                     _mm_maskz_set1_epi8(back_face, 1));

    auto normal = internal::TransformDirectionAvx512(
        state.normal, source_normal_x, source_normal_y, source_normal_z);

    if constexpr (kIris && kHasPbr && !kPosOnly) {
        const auto source_tangent_x =
            _mm512_load_ps(cube_group.tangent[0].data());
        const auto source_tangent_y =
            _mm512_load_ps(cube_group.tangent[1].data());
        const auto source_tangent_z =
            _mm512_load_ps(cube_group.tangent[2].data());
        auto tangent = state.uniform_scale
                           ? internal::TransformDirectionAvx512(
                                 state.normal, source_tangent_x,
                                 source_tangent_y, source_tangent_z)
                           : internal::TransformDirectionAvx512(
                                 state.pose, source_tangent_x, source_tangent_y,
                                 source_tangent_z);
        if (!state.uniform_scale) [[unlikely]] {
            internal::NormalizeDirectionsAvx512(normal, tangent);
        }

        const auto zero = _mm512_setzero_ps();
        normal.x = _mm512_mask_sub_ps(normal.x, back_face, zero, normal.x);
        normal.y = _mm512_mask_sub_ps(normal.y, back_face, zero, normal.y);
        normal.z = _mm512_mask_sub_ps(normal.z, back_face, zero, normal.z);
        _mm512_store_si512(
            out.normal.data(),
            internal::PackSnorm4x8Avx512(normal.x, normal.y, normal.z, zero));

        const auto baked_handedness =
            _mm512_load_ps(cube_group.tangent[3].data());
        auto tangent_w = _mm512_mul_ps(
            baked_handedness, _mm512_set1_ps(state.tangent_orientation));
        tangent_w = _mm512_mask_sub_ps(tangent_w, back_face, zero, tangent_w);
        const auto degenerate =
            _mm512_cmp_ps_mask(baked_handedness, zero, _CMP_EQ_OQ);
        tangent_w =
            _mm512_mask_mov_ps(tangent_w, degenerate, _mm512_set1_ps(1.0f));
        _mm512_store_si512(out.tangent.data(),
                           internal::PackSnorm4x8Avx512(tangent.x, tangent.y,
                                                        tangent.z, tangent_w));
    } else {
        if (!state.uniform_scale) [[unlikely]] {
            internal::NormalizeDirectionAvx512(normal);
        }

        const auto zero = _mm512_setzero_ps();
        normal.x = _mm512_mask_sub_ps(normal.x, back_face, zero, normal.x);
        normal.y = _mm512_mask_sub_ps(normal.y, back_face, zero, normal.y);
        normal.z = _mm512_mask_sub_ps(normal.z, back_face, zero, normal.z);
        _mm512_store_si512(
            out.normal.data(),
            internal::PackSnorm4x8Avx512(normal.x, normal.y, normal.z, zero));
    }

    if constexpr (CubeGroupType::kTranslucent) {
        const auto center_x = _mm512_load_ps(cube_group.center[0].data());
        const auto center_y = _mm512_load_ps(cube_group.center[1].data());
        const auto center_z = _mm512_load_ps(cube_group.center[2].data());
        const auto center_w = _mm512_set1_ps(1.0f);
        const auto clip_z = internal::DotAvx512(center_x, center_y, center_z,
                                                center_w, state.depth_z);
        const auto clip_w = internal::DotAvx512(center_x, center_y, center_z,
                                                center_w, state.depth_w);
        _mm512_store_ps(out.face_depth.data(), _mm512_div_ps(clip_z, clip_w));
    }

    static_cast<void>(kCulling);
}
}  // namespace ysm::gfx::renderer::cube

YSM_FAST_MATH_END

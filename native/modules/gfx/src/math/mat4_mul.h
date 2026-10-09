#pragma once

#ifdef YSM_X64
#include <immintrin.h>
#endif

#include <cglm/mat4.h>
#include <cglm/affine-mat.h>

#include "cpu.h"
#include "fast_math.h"
#include "inline.h"

YSM_FAST_MATH_BEGIN

namespace ysm::gfx::math {
YSM_INLINE void Mat4Mul(simd::GenericTag, const mat4 m1, const mat4 m2, mat4 dest) noexcept {
    glm_mat4_mul(m1, m2, dest);
}

#ifdef YSM_X64
YSM_TARGET_AVX2
static void Mat4Mul(simd::Tag<simd::Type::AVX2>, const mat4 m1, const mat4 m2, mat4 dest) noexcept {
    __m256 y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, r1, r2;
    __m256i y10, y11, y12, y13;

    y0 = _mm256_load_ps(m2[0]);
    y2 = _mm256_load_ps(m1[0]);
    y10 = _mm256_set_epi32(1, 1, 1, 1,   0, 0, 0, 0);
    y6 = _mm256_permutevar_ps(y0, y10);
    r1 = _mm256_mul_ps(y2, y6);

    y3 = _mm256_load_ps(m1[2]);
    y11 = _mm256_set_epi32(3, 3, 3, 3,   2, 2, 2, 2);
    y7 = _mm256_permutevar_ps(y0, y11);
    r2 = _mm256_mul_ps(y3, y7);

    y4 = _mm256_permute2f128_ps(y2, y2, 0x03);
    y12 = _mm256_set_epi32(0, 0, 0, 0,   1, 1, 1, 1);
    y8 = _mm256_permutevar_ps(y0, y12);
    r1 = _mm256_fmadd_ps(y4, y8, r1);

    y5 = _mm256_permute2f128_ps(y3, y3, 0x03);
    y13 = _mm256_set_epi32(2, 2, 2, 2,   3, 3, 3, 3);
    y9 = _mm256_permutevar_ps(y0, y13);
    r2 = _mm256_fmadd_ps(y5, y9, r2);

    y1 = _mm256_load_ps(m2[2]);
    _mm256_store_ps(dest[0], _mm256_add_ps(r1, r2));

    y6 = _mm256_permutevar_ps(y1, y10);
    r1 = _mm256_mul_ps(y2, y6);

    y7 = _mm256_permutevar_ps(y1, y11);
    r2 = _mm256_mul_ps(y3, y7);

    y8 = _mm256_permutevar_ps(y1, y12);
    r1 = _mm256_fmadd_ps(y4, y8, r1);

    y9 = _mm256_permutevar_ps(y1, y13);
    r2 = _mm256_fmadd_ps(y5, y9, r2);

    _mm256_store_ps(dest[2], _mm256_add_ps(r1, r2));
}

YSM_TARGET_AVX512
static void Mat4Mul(simd::Tag<simd::Type::AVX512>, const mat4 m1, const mat4 m2, mat4 dest) noexcept {
    /* b33 b32 b31 b30   b23 b22 b21 b20   b13 b12 b11 b10   b03 b02 b01 b00 */
    auto b = _mm512_load_ps(m2);

    /* a33 a32 a31 a30   a23 a22 a21 a20   a13 a12 a11 a10   a03 a02 a01 a00 */
    /* b33 b33 b33 b33   b22 b22 b22 b22   b11 b11 b11 b11   b00 b00 b00 b00 */
    auto a0 = _mm512_load_ps(m1);
    auto b0 = _mm512_permutevar_ps(b,
        _mm512_set_epi32(3, 3, 3, 3,   2, 2, 2, 2,   1, 1, 1, 1,   0, 0, 0, 0));
    auto r0 = _mm512_mul_ps(a0, b0);

    /* a03 a02 a01 a00   a33 a32 a31 a30   a23 a22 a21 a20   a13 a12 a11 a10 */
    /* b30 b30 b30 b30   b23 b23 b23 b23   b12 b12 b12 b12   b01 b01 b01 b01 */
    auto a1 = _mm512_castsi512_ps(_mm512_alignr_epi64(_mm512_castps_si512(a0), _mm512_castps_si512(a0), 2));
    auto b1 = _mm512_permutevar_ps(b,
        _mm512_set_epi32(0, 0, 0, 0,   3, 3, 3, 3,   2, 2, 2, 2,   1, 1, 1, 1));
    auto r1 = _mm512_mul_ps(a1, b1);

    /* a13 a12 a11 a10   a03 a02 a01 a00   a33 a32 a31 a30   a23 a22 a21 a20 */
    /* b31 b31 b31 b31   b20 b20 b20 b20   b13 b13 b13 b13   b02 b02 b02 b02 */
    auto a2 = _mm512_castsi512_ps(_mm512_alignr_epi64(_mm512_castps_si512(a0), _mm512_castps_si512(a0), 4));
    auto b2 = _mm512_permutevar_ps(b,
        _mm512_set_epi32(1, 1, 1, 1,   0, 0, 0, 0,   3, 3, 3, 3,   2, 2, 2, 2));
    r0 = _mm512_fmadd_ps(a2, b2, r0);

    /* a23 a22 a21 a20   a13 a12 a11 a10   a03 a02 a01 a00   a33 a32 a31 a30 */
    /* b32 b32 b32 b32   b21 b21 b21 b21   b10 b10 b10 b10   b03 b03 b03 b03 */
    auto a3 = _mm512_castsi512_ps(_mm512_alignr_epi64(_mm512_castps_si512(a0), _mm512_castps_si512(a0), 6));
    auto b3 = _mm512_permutevar_ps(b,
        _mm512_set_epi32(2, 2, 2, 2,   1, 1, 1, 1,   0, 0, 0, 0,   3, 3, 3, 3));
    r1 = _mm512_fmadd_ps(a3, b3, r1);

    _mm512_store_ps(dest, _mm512_add_ps(r0, r1));
}

template <simd::Type T>
YSM_INLINE void AffineMul(simd::Tag<T> tag, const mat4 m1, const mat4 m2, mat4 dest) noexcept {
    if constexpr (T == simd::Type::AVX512 || T == simd::Type::AVX2) {
        Mat4Mul(tag, m1, m2, dest);
    } else {
        glm_mul(m1, m2, dest);
    }
}
#else
template <simd::Type T>
YSM_INLINE void AffineMul(simd::Tag<T>, const mat4 m1, const mat4 m2, mat4 dest) noexcept {
    glm_mul(m1, m2, dest);
}
#endif

}

YSM_FAST_MATH_END

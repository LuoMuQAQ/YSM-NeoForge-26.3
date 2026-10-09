#pragma once

#include <cglm/types.h>
#include <bit>
#include <array>
#include "cpu.h"

#ifdef YSM_X64
#include <immintrin.h>
#elif (defined YSM_ARM64)
#include <arm_neon.h>
#endif

namespace ysm::gfx::renderer::buffer {
    namespace internal {
    inline uint32_t PackSnorm4x8(vec4 value) noexcept {
        CGLM_ALIGN(16) std::array<int32_t, 4> result;
#ifdef YSM_X64
        auto m = _mm_load_ps(value);
        m = _mm_mul_ps(m, _mm_set1_ps(127.0f));
        m = _mm_round_ps(m, _MM_FROUND_TO_NEAREST_INT | _MM_FROUND_NO_EXC);

        auto mi = _mm_cvtps_epi32(m);
        mi = _mm_max_epi32(_mm_set1_epi32(-127), _mm_min_epi32(_mm_set1_epi32(127), mi));

        _mm_store_si128(reinterpret_cast<__m128i *>(result.data()), mi);
#elif  (defined YSM_ARM64)
        auto m = vld1q_f32(value);
        m = vmulq_f32(m, vdupq_n_f32(127.0f));

        auto mi = vcvtnq_s32_f32(m);
        mi = vmaxq_s32(vdupq_n_s32(-127), vminq_s32(vdupq_n_s32(127), mi));

        vst1q_s32(result.data(), mi);
#else
#error "Unknown cpu arch" // TODO: generic
#endif
        return static_cast<uint32_t>(std::bit_cast<uint8_t>(static_cast<int8_t>(result[0]))) |
            (static_cast<uint32_t>(std::bit_cast<uint8_t>(static_cast<int8_t>(result[1]))) << 8) |
            (static_cast<uint32_t>(std::bit_cast<uint8_t>(static_cast<int8_t>(result[2]))) << 16) |
            (static_cast<uint32_t>(std::bit_cast<uint8_t>(static_cast<int8_t>(result[3]))) << 24); // little endian
    }
    }  // namespace internal

    inline uint32_t PackNormal(vec4 normal) noexcept {
        return internal::PackSnorm4x8(normal) & 0x00ffffffU;
    }

    inline uint32_t PackTangent(vec4 tangent) noexcept {
        return internal::PackSnorm4x8(tangent);
    }
}

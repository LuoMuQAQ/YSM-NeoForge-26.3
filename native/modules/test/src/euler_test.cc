#include <algorithm>
#include <bit>
#include <cmath>
#include <cstdint>
#include <limits>
#include <numbers>

#include <cglm/euler.h>
#include <gtest/gtest.h>

#include "math/euler.h"

namespace ysm::test {
namespace {
float MaxAbsDifference(const mat4 left, const mat4 right) noexcept {
    float difference = 0.0f;
    for (int column = 0; column < 4; ++column) {
        for (int row = 0; row < 4; ++row) {
            difference = std::max(
                difference,
                std::abs(left[column][row] - right[column][row]));
        }
    }
    return difference;
}

#if defined(YSM_X64) || defined(YSM_ARM64)
using EulerKernel = void (*)(vec3, mat4) noexcept;

#ifdef YSM_X64

YSM_TARGET_AVX2 YSM_NOINLINE void EulerZYXAvx2(
    vec3 angles, mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::Tag<simd::Type::AVX2>{}, angles,
                   destination);
}

YSM_TARGET_AVX512 YSM_NOINLINE void EulerZYXAvx512(
    vec3 angles, mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::Tag<simd::Type::AVX512>{}, angles,
                   destination);
}

#elif defined(YSM_ARM64)

YSM_NOINLINE void EulerZYXNeon(vec3 angles,
                               mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::Tag<simd::Type::NEON>{}, angles,
                   destination);
}

#endif

void ExpectFmaKernelMatchesCglm(EulerKernel kernel) {
    constexpr float kTolerance = 0.000002f;
    uint32_t random_state = 0x12345678U;
    float max_difference = 0.0f;
    for (size_t sample = 0; sample < (1U << 16); ++sample) {
        const auto next_angle = [&] {
            random_state = random_state * 1664525U + 1013904223U;
            return static_cast<float>(
                       static_cast<int32_t>(random_state)) *
                   (8192.0f / 2147483648.0f);
        };
        vec3 angles{next_angle(), next_angle(), next_angle()};
        mat4 expected;
        mat4 actual;
        glm_euler_zyx(angles, expected);
        kernel(angles, actual);
        max_difference = std::max(
            max_difference, MaxAbsDifference(expected, actual));
    }

    constexpr float kLargeAngles[][3]{
        {9000.0f, -10000.0f, 12000.0f},
        {1000000.0f, -1000000.0f, 2000000.0f},
    };
    for (const auto& sample : kLargeAngles) {
        vec3 angles{sample[0], sample[1], sample[2]};
        mat4 expected;
        mat4 actual;
        glm_euler_zyx(angles, expected);
        kernel(angles, actual);
        max_difference = std::max(
            max_difference, MaxAbsDifference(expected, actual));
    }
    EXPECT_LE(max_difference, kTolerance);
}
#endif
}  // namespace

TEST(EulerTest, ReducesLargeAnglesWithoutModifyingInput) {
    vec3 angles{9000.0f, -10000.0f, 12000.0f};
    const vec3 original{angles[0], angles[1], angles[2]};
    mat4 actual;
    mat4 expected;

    gfx::math::EulerZYX(simd::GenericTag{}, angles, actual);
    glm_euler_zyx(angles, expected);

    for (int column = 0; column < 4; ++column) {
        for (int row = 0; row < 4; ++row) {
            EXPECT_NEAR(actual[column][row], expected[column][row],
                        0.000002f);
        }
    }
    EXPECT_FLOAT_EQ(angles[0], original[0]);
    EXPECT_FLOAT_EQ(angles[1], original[1]);
    EXPECT_FLOAT_EQ(angles[2], original[2]);
}

TEST(EulerTest, ReducesToRightClosedPrincipalInterval) {
    constexpr float kPi = std::numbers::pi_v<float>;
    constexpr float kSamples[]{
        8193.0f,
        -8193.0f,
        1000000.0f,
        -1000000.0f,
        std::numeric_limits<float>::max(),
        -std::numeric_limits<float>::max(),
    };

    for (const float angle : kSamples) {
        const float reduced = gfx::math::internal::ReduceAngle(angle);
        double expected =
            std::remainder(static_cast<double>(angle),
                           2.0 * std::numbers::pi_v<double>);
        if (expected <= -std::numbers::pi_v<double>) {
            expected += 2.0 * std::numbers::pi_v<double>;
        }
        EXPECT_GT(reduced, -kPi);
        EXPECT_LE(reduced, kPi);
        EXPECT_FLOAT_EQ(reduced, static_cast<float>(expected));
    }

    EXPECT_GT(gfx::math::internal::ReduceAngle(-kPi), 0.0f);
}

TEST(EulerTest, ReductionMatchesLibmAcrossFloatExponentRange) {
    constexpr uint32_t kMantissas[]{0, 1, 0x123456, 0x7fffff};
    constexpr uint32_t kSigns[]{0, uint32_t{1} << 31};
    constexpr double kPi = std::numbers::pi_v<double>;
    constexpr double kTwoPi = 2.0 * kPi;

    for (uint32_t exponent = 140; exponent <= 254; ++exponent) {
        for (const uint32_t mantissa : kMantissas) {
            for (const uint32_t sign : kSigns) {
                const float angle = std::bit_cast<float>(
                    sign | exponent << 23 | mantissa);
                double expected =
                    std::remainder(static_cast<double>(angle), kTwoPi);
                if (expected <= -kPi) {
                    expected += kTwoPi;
                }
                EXPECT_FLOAT_EQ(gfx::math::internal::ReduceAngle(angle),
                                static_cast<float>(expected));
            }
        }
    }
}

#ifdef YSM_X64
TEST(EulerTest, FmaKernelsMatchCglmAcrossSupportedRange) {
    InitCpuInfo();
    if (simd::kSupported == simd::Type::AVX2 ||
        simd::kSupported == simd::Type::AVX512) {
        ExpectFmaKernelMatchesCglm(EulerZYXAvx2);
    }
    if (simd::kSupported == simd::Type::AVX512) {
        ExpectFmaKernelMatchesCglm(EulerZYXAvx512);
    }
}
#elif defined(YSM_ARM64)
TEST(EulerTest, NeonFmaKernelMatchesCglmAcrossSupportedRange) {
    ExpectFmaKernelMatchesCglm(EulerZYXNeon);
}
#endif
}  // namespace ysm::test

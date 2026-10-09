#include "euler_zyx.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <numbers>
#include <string>
#include <string_view>
#include <vector>

#include <benchmark/benchmark.h>
#include <cglm/euler.h>

#include "cpu.h"
#include "inline.h"
#include "math/euler.h"

namespace ysm::benchmarking {
namespace {

constexpr size_t kThroughputBatchSize = 64;
constexpr float kTolerance = 0.000002f;

struct alignas(16) Angles {
    vec3 value;
};

struct alignas(64) Matrix {
    mat4 value;
};

using Kernel = void (*)(vec3, mat4) noexcept;

struct Variant {
    std::string_view implementation;
    std::string_view isa;
    Kernel kernel;
};

using InputFactory = Angles (*)(size_t) noexcept;

struct Distribution {
    std::string_view name;
    InputFactory make_input;
};

YSM_NOINLINE void CglmEulerZYXBaseline(vec3 angles,
                                      mat4 destination) noexcept {
    glm_euler_zyx(angles, destination);
}

YSM_NOINLINE void YsmEulerZYXBaseline(vec3 angles,
                                     mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::GenericTag{}, angles, destination);
}

#ifdef YSM_X64

YSM_TARGET_AVX2 YSM_NOINLINE void CglmEulerZYXAvx2(
    vec3 angles, mat4 destination) noexcept {
    glm_euler_zyx(angles, destination);
}

YSM_TARGET_AVX2 YSM_NOINLINE void YsmEulerZYXAvx2(
    vec3 angles, mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::Tag<simd::Type::AVX2>{}, angles, destination);
}

YSM_TARGET_AVX512 YSM_NOINLINE void CglmEulerZYXAvx512(
    vec3 angles, mat4 destination) noexcept {
    glm_euler_zyx(angles, destination);
}

YSM_TARGET_AVX512 YSM_NOINLINE void YsmEulerZYXAvx512(
    vec3 angles, mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::Tag<simd::Type::AVX512>{}, angles, destination);
}

std::vector<Variant> SupportedVariants() {
    std::vector<Variant> variants{
        {"cglm", "SSE41", CglmEulerZYXBaseline},
        {"ysm", "SSE41", YsmEulerZYXBaseline},
    };
    if (simd::kSupported == simd::Type::AVX2 ||
        simd::kSupported == simd::Type::AVX512) {
        variants.push_back({"cglm", "AVX2_FMA", CglmEulerZYXAvx2});
        variants.push_back({"ysm", "AVX2_FMA", YsmEulerZYXAvx2});
    }
    if (simd::kSupported == simd::Type::AVX512) {
        variants.push_back({"cglm", "AVX512", CglmEulerZYXAvx512});
        variants.push_back({"ysm", "AVX512", YsmEulerZYXAvx512});
    }
    return variants;
}

#elif defined(YSM_ARM64)

YSM_NOINLINE void YsmEulerZYXNeon(
    vec3 angles, mat4 destination) noexcept {
    gfx::math::EulerZYX(simd::Tag<simd::Type::NEON>{}, angles, destination);
}

std::vector<Variant> SupportedVariants() {
    return {
        {"cglm", "NEON", CglmEulerZYXBaseline},
        {"ysm", "NEON_FMA", YsmEulerZYXNeon},
    };
}

#endif

Angles MakeBroadInput(size_t index) noexcept {
    const float phase = static_cast<float>(index) * 0.03125f;
    return Angles{{
        -std::numbers::pi_v<float> * 0.75f + phase,
        0.625f - phase * 0.5f,
        std::numbers::pi_v<float> * 0.25f + phase * 0.75f,
    }};
}

Angles MakeSmallInput(size_t index) noexcept {
    const float progress = static_cast<float>(index % kThroughputBatchSize) /
                           static_cast<float>(kThroughputBatchSize - 1);
    return Angles{{
        -0.7f + progress * 1.4f,
        0.6f - progress * 1.2f,
        -0.5f + progress,
    }};
}

constexpr Distribution kDistributions[]{
    {"SmallAngles", MakeSmallInput},
    {"BroadRadians", MakeBroadInput},
};

Angles MakeValidationInput(size_t sample) noexcept {
    const float progress = static_cast<float>(sample) / 4095.0f;
    return Angles{{
        -std::numbers::pi_v<float> +
            progress * (2.0f * std::numbers::pi_v<float>),
        0.75f - progress * 1.5f,
        -0.25f + progress * 0.5f,
    }};
}

float MaxAbsDifference(const mat4 left, const mat4 right) noexcept {
    float difference = 0.0f;
    for (size_t column = 0; column < 4; ++column) {
        for (size_t row = 0; row < 4; ++row) {
            difference =
                std::max(difference,
                         std::abs(left[column][row] - right[column][row]));
        }
    }
    return difference;
}

void SyntheticDependencyChain(benchmark::State& state, Kernel kernel) {
    Angles angles = MakeBroadInput(3);
    Matrix matrix;

    for (auto _ : state) {
        kernel(angles.value, matrix.value);
        angles.value[0] = matrix.value[0][1] * 0.75f + 0.125f;
        angles.value[1] = matrix.value[1][2] * 0.5f - 0.25f;
        angles.value[2] = matrix.value[2][0] * 0.625f + 0.375f;
        benchmark::DoNotOptimize(angles.value);
    }
    state.SetItemsProcessed(state.iterations());
}

void Throughput(benchmark::State& state, Kernel kernel,
                InputFactory make_input) {
    std::array<Angles, kThroughputBatchSize> angles;
    std::array<Matrix, kThroughputBatchSize> matrices;
    for (size_t index = 0; index < angles.size(); ++index) {
        angles[index] = make_input(index);
    }

    for (auto _ : state) {
        for (size_t index = 0; index < angles.size(); ++index) {
            kernel(angles[index].value, matrices[index].value);
        }
        benchmark::DoNotOptimize(matrices.data());
        benchmark::ClobberMemory();
    }
    state.SetItemsProcessed(state.iterations() * kThroughputBatchSize);
}

void RegisterSyntheticDependencyChain(const Variant& variant) {
    std::string name = "EulerZYX/";
    name += "SyntheticDependencyChain/";
    name += variant.isa;
    name += '/';
    name += variant.implementation;
    benchmark::RegisterBenchmark(name, SyntheticDependencyChain,
                                 variant.kernel)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);
}

void RegisterThroughput(const Distribution& distribution,
                        const Variant& variant) {
    std::string name = "EulerZYX/Throughput/";
    name += distribution.name;
    name += '/';
    name += variant.isa;
    name += '/';
    name += variant.implementation;
    benchmark::RegisterBenchmark(name, Throughput, variant.kernel,
                                 distribution.make_input)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);
}

}  // namespace

bool ValidateEulerZYXBenchmarks() {
    const auto variants = SupportedVariants();
    for (size_t sample = 0; sample < 4096; ++sample) {
        Angles angles = MakeValidationInput(sample);
        Matrix expected;
        CglmEulerZYXBaseline(angles.value, expected.value);
        for (const auto& variant : variants) {
            Matrix actual;
            variant.kernel(angles.value, actual.value);
            const float difference =
                MaxAbsDifference(expected.value, actual.value);
            if (difference > kTolerance) {
                std::fprintf(stderr,
                             "EulerZYX %.*s/%.*s validation failed at sample "
                             "%zu: max abs difference %.9g\n",
                             static_cast<int>(variant.isa.size()),
                             variant.isa.data(),
                             static_cast<int>(variant.implementation.size()),
                             variant.implementation.data(), sample,
                             static_cast<double>(difference));
                return false;
            }
        }
    }
    return true;
}

void RegisterEulerZYXBenchmarks() {
    const auto variants = SupportedVariants();
    benchmark::AddCustomContext(
        "euler_zyx_throughput_batch_size",
        std::to_string(kThroughputBatchSize));
    benchmark::AddCustomContext(
        "euler_zyx_dependency_chain",
        "synthetic matrix-output-to-next-angle dependency; not a ModelState "
        "data flow");
    benchmark::AddCustomContext(
        "euler_zyx_small_angles",
        "all lanes within [-0.7, 0.7], including the UCRT pi/4 fast path");
    benchmark::AddCustomContext(
        "euler_zyx_broad_radians",
        "normal finite radians spanning multiple quadrants; large-angle "
        "reduction excluded");
    for (const auto& variant : variants) {
        RegisterSyntheticDependencyChain(variant);
        for (const auto& distribution : kDistributions) {
            RegisterThroughput(distribution, variant);
        }
    }
}

}  // namespace ysm::benchmarking

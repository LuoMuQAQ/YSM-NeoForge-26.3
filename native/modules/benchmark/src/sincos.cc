#include "sincos.h"

#ifdef YSM_X64

#include <array>
#include <cmath>
#include <cstdio>
#include <numbers>
#include <string>
#include <string_view>
#include <vector>

#include <benchmark/benchmark.h>
#include <immintrin.h>

#include "cpu.h"
#include "inline.h"
#include "math/euler.h"

namespace ysm::benchmarking {
namespace {

constexpr size_t kStorageLaneCount = 4;
constexpr size_t kRelevantLaneCount = 3;
constexpr size_t kThroughputBatchSize = 64;
constexpr float kTolerance = 0.000002f;

struct alignas(16) Values {
    float lanes[kStorageLaneCount];
};

using Kernel = void (*)(float*) noexcept;

struct Variant {
    std::string_view name;
    Kernel kernel;
};

YSM_TARGET_AVX2 YSM_NOINLINE void ApproxAvx2(float* values) noexcept {
    const auto [sin, cos] =
        gfx::math::internal::SinCosApproxFmaAvx2(_mm_load_ps(values));
    _mm_store_ps(values, _mm_add_ps(sin, cos));
}

YSM_TARGET_AVX512 YSM_NOINLINE void ApproxAvx512(float* values) noexcept {
    const auto [sin, cos] =
        gfx::math::internal::SinCosApproxFmaAvx512(_mm_load_ps(values));
    _mm_store_ps(values, _mm_add_ps(sin, cos));
}

YSM_NOINLINE void ScalarStd(float* values) noexcept {
    for (size_t lane = 0; lane < kRelevantLaneCount; ++lane) {
        const float value = values[lane];
        values[lane] = std::sin(value) + std::cos(value);
    }
}

#if defined(__INTEL_LLVM_COMPILER) || defined(__INTEL_COMPILER)
#define YSM_HAS_INTEL_SIN_COS_INTRINSICS 1
YSM_NOINLINE void IntelSinCos128(float* values) noexcept {
    const __m128 input = _mm_load_ps(values);
    const __m128 sin = _mm_sin_ps(input);
    const __m128 cos = _mm_cos_ps(input);
    _mm_store_ps(values, _mm_add_ps(sin, cos));
}
#else
#define YSM_HAS_INTEL_SIN_COS_INTRINSICS 0
#endif

std::vector<Variant> SupportedVariants() {
    std::vector<Variant> variants{
        {"Scalar_std_sin_plus_cos", ScalarStd},
    };
    if (simd::kSupported == simd::Type::AVX2 ||
        simd::kSupported == simd::Type::AVX512) {
        variants.push_back({"Approx_AVX2_FMA", ApproxAvx2});
    }
    if (simd::kSupported == simd::Type::AVX512) {
        variants.push_back({"Approx_AVX512VL_FMA", ApproxAvx512});
    }
#if YSM_HAS_INTEL_SIN_COS_INTRINSICS
    variants.push_back(
        {"Intel_mm_sin_ps_plus_mm_cos_ps", IntelSinCos128});
#endif
    return variants;
}

Values MakeInput(size_t index) noexcept {
    const float offset = static_cast<float>(index) * 0.0009765625f;
    return Values{{
        0.125f + offset,
        -0.75f - offset,
        std::numbers::pi_v<float> * 0.5f + offset,
        -std::numbers::pi_v<float> + offset,
    }};
}

void Latency(benchmark::State& state, Kernel kernel) {
    Values values = MakeInput(3);
    for (auto _ : state) {
        kernel(values.lanes);
        benchmark::DoNotOptimize(values.lanes);
    }
    state.SetItemsProcessed(state.iterations() * kRelevantLaneCount);
}

void Throughput(benchmark::State& state, Kernel kernel) {
    std::array<Values, kThroughputBatchSize> values;
    for (size_t index = 0; index < values.size(); ++index) {
        values[index] = MakeInput(index);
    }

    for (auto _ : state) {
        for (auto& batch : values) {
            kernel(batch.lanes);
        }
        benchmark::DoNotOptimize(values.data());
        benchmark::ClobberMemory();
    }
    state.SetItemsProcessed(state.iterations() * kThroughputBatchSize *
                            kRelevantLaneCount);
}

void Register(std::string_view mode, const Variant& variant,
              void (*function)(benchmark::State&, Kernel)) {
    std::string name = "SinCos/";
    name += mode;
    name += '/';
    name += variant.name;
    benchmark::RegisterBenchmark(name, function, variant.kernel)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);
}

}  // namespace

bool ValidateSinCosBenchmarks() {
    const auto variants = SupportedVariants();
    for (size_t sample = 0; sample < 4096; ++sample) {
        const Values input{{
            -8.0f * std::numbers::pi_v<float> +
                static_cast<float>(sample) *
                    (16.0f * std::numbers::pi_v<float> / 4095.0f),
            -3.0f + static_cast<float>(sample) * (6.0f / 4095.0f),
            -0.25f + static_cast<float>(sample) * (0.5f / 4095.0f),
            0.0f,
        }};
        for (const auto& variant : variants) {
            Values actual = input;
            variant.kernel(actual.lanes);
            for (size_t lane = 0; lane < kRelevantLaneCount; ++lane) {
                const float expected =
                    std::sin(input.lanes[lane]) + std::cos(input.lanes[lane]);
                if (std::abs(actual.lanes[lane] - expected) > kTolerance) {
                    std::fprintf(
                        stderr,
                        "SIMD sincos %.*s validation failed at sample %zu, "
                        "lane %zu: actual %.9g, expected %.9g\n",
                        static_cast<int>(variant.name.size()),
                        variant.name.data(), sample, lane,
                        actual.lanes[lane], expected);
                    return false;
                }
            }
        }
    }
    return true;
}

void RegisterSinCosBenchmarks() {
    const auto variants = SupportedVariants();
    benchmark::AddCustomContext(
        "sincos_scalar_reference",
        "std::sin + std::cos (no std::sincos in Windows UCRT)");
#if YSM_HAS_INTEL_SIN_COS_INTRINSICS
    benchmark::AddCustomContext("intel_mm_sin_ps_plus_mm_cos_ps", "available");
#else
    benchmark::AddCustomContext(
        "intel_mm_sin_ps_plus_mm_cos_ps",
        "unavailable in the active clang 22 intrinsic headers/SVML runtime");
#endif
    benchmark::AddCustomContext("sincos_relevant_lanes_per_call",
                                std::to_string(kRelevantLaneCount));
    benchmark::AddCustomContext(
        "sincos_simd_storage_lanes",
        std::to_string(kStorageLaneCount) +
            " (fourth result is intentionally discarded)");
    for (const auto& variant : variants) {
        Register("LatencyChain", variant, Latency);
        Register("Throughput", variant, Throughput);
    }
}

}  // namespace ysm::benchmarking

#else

namespace ysm::benchmarking {

bool ValidateSinCosBenchmarks() {
    return true;
}

void RegisterSinCosBenchmarks() {}

}  // namespace ysm::benchmarking

#endif

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <string_view>
#include <vector>

#include <benchmark/benchmark.h>

#include "cpu.h"
#include "cube_transform.h"
#include "euler_zyx.h"
#include "model_state_extract.h"
#include "sincos.h"

#ifdef YSM_X64

#include <cglm/affine-mat.h>
#include <cglm/euler.h>
#include <cglm/mat4.h>

#include "cglm_avx.h"
#include "inline.h"
#include "math/mat4_mul.h"

namespace {

using Multiply = void (*)(const mat4, const mat4, mat4);

struct alignas(64) Matrix {
    mat4 value{};
};

struct Implementation {
    std::string_view name;
    Multiply multiply;
};

constexpr size_t kThroughputBatchSize = 64;
constexpr double kCorrectnessTolerance = 1.0e-6;

YSM_NOINLINE void MulRotSse2(const mat4 left, const mat4 right,
                            mat4 destination) noexcept {
    glm_mul_rot_sse2(left, right, destination);
}

YSM_NOINLINE void GlmMul(const mat4 left, const mat4 right,
                        mat4 destination) noexcept {
    glm_mul(left, right, destination);
}

YSM_NOINLINE void GlmMat4MulSse2(const mat4 left, const mat4 right,
                                mat4 destination) noexcept {
    glm_mat4_mul_sse2(left, right, destination);
}

YSM_NOINLINE YSM_TARGET_AVX2 void Mat4MulAvx2(
    const mat4 left, const mat4 right, mat4 destination) noexcept {
    ysm::gfx::math::Mat4Mul(ysm::simd::Tag<ysm::simd::Type::AVX2>{}, left,
                                 right, destination);
}

YSM_NOINLINE YSM_TARGET_AVX512 void Mat4MulAvx512(
    const mat4 left, const mat4 right, mat4 destination) noexcept {
    ysm::gfx::math::Mat4Mul(ysm::simd::Tag<ysm::simd::Type::AVX512>{},
                                 left, right, destination);
}

void MakeAffine(float seed, mat4 destination) noexcept {
    const mat4 value = {
        {0.91f, 0.13f, -0.27f, 0.0f},
        {-0.17f, 1.11f, 0.08f, 0.0f},
        {0.22f, -0.05f, 0.97f, 0.0f},
        {seed * 0.01f, seed * -0.02f, seed * 0.005f, 1.0f},
    };
    std::memcpy(destination, value, sizeof(mat4));
}

void MakeRotation(mat4 destination) noexcept {
    vec3 angles{0.000137f, -0.000113f, 0.000097f};
    glm_euler_zyx(angles, destination);
}

double MaxAbsDifference(const mat4 left, const mat4 right) noexcept {
    double result = 0.0;
    for (size_t column = 0; column < 4; ++column) {
        for (size_t row = 0; row < 4; ++row) {
            result = std::max(
                result, static_cast<double>(
                            std::abs(left[column][row] - right[column][row])));
        }
    }
    return result;
}

std::string_view SimdName(ysm::simd::Type type) noexcept {
    switch (type) {
        case ysm::simd::Type::SSE41:
            return "SSE4.1";
        case ysm::simd::Type::AVX2:
            return "AVX2";
        case ysm::simd::Type::AVX512:
            return "AVX-512";
        default:
            return "none";
    }
}

std::vector<Implementation> SupportedImplementations() {
    std::vector<Implementation> implementations{
        {"glm_mul_rot_sse2", MulRotSse2},
        {"glm_mul", GlmMul},
        {"glm_mat4_mul_sse2", GlmMat4MulSse2},
    };
    if (ysm::simd::kSupported == ysm::simd::Type::AVX2 ||
        ysm::simd::kSupported == ysm::simd::Type::AVX512) {
        implementations.push_back(
            {"glm_mat4_mul_avx", ysm::benchmarking::GlmMat4MulAvx});
        implementations.push_back({"Mat4Mul_AVX2", Mat4MulAvx2});
    }
    if (ysm::simd::kSupported == ysm::simd::Type::AVX512) {
        implementations.push_back({"Mat4Mul_AVX512", Mat4MulAvx512});
    }
    return implementations;
}

bool ValidateImplementations(
    const std::vector<Implementation>& implementations) {
    Matrix left;
    Matrix rotation;
    Matrix reference;
    Matrix result;
    MakeAffine(7.0f, left.value);
    MakeRotation(rotation.value);
    glm_mat4_mul(left.value, rotation.value, reference.value);

    for (const auto& implementation : implementations) {
        implementation.multiply(left.value, rotation.value, result.value);
        const double difference =
            MaxAbsDifference(reference.value, result.value);
        if (difference > kCorrectnessTolerance) {
            std::fprintf(stderr,
                         "%.*s failed validation: max abs difference %.9g\n",
                         static_cast<int>(implementation.name.size()),
                         implementation.name.data(), difference);
            return false;
        }
    }
    return true;
}

void BenchmarkLatency(benchmark::State& state, Multiply multiply) {
    Matrix accumulator;
    Matrix rotation;
    MakeAffine(7.0f, accumulator.value);
    MakeRotation(rotation.value);

    for (auto _ : state) {
        multiply(accumulator.value, rotation.value, accumulator.value);
        benchmark::DoNotOptimize(accumulator.value);
    }
}

void BenchmarkThroughput(benchmark::State& state, Multiply multiply) {
    std::array<Matrix, kThroughputBatchSize> inputs;
    std::array<Matrix, kThroughputBatchSize> outputs;
    Matrix rotation;
    MakeRotation(rotation.value);
    for (size_t index = 0; index < inputs.size(); ++index) {
        MakeAffine(static_cast<float>(index + 1), inputs[index].value);
    }

    for (auto _ : state) {
        for (size_t index = 0; index < inputs.size(); ++index) {
            multiply(inputs[index].value, rotation.value, outputs[index].value);
        }
        benchmark::DoNotOptimize(outputs.data());
        benchmark::ClobberMemory();
    }
    state.SetItemsProcessed(state.iterations() * kThroughputBatchSize);
}

void RegisterBenchmarks(const std::vector<Implementation>& implementations) {
    for (const auto& implementation : implementations) {
        const std::string latency_name =
            "Mat4Mul/Latency/" + std::string(implementation.name);
        benchmark::RegisterBenchmark(latency_name, BenchmarkLatency,
                                     implementation.multiply)
            ->UseRealTime()
            ->Unit(benchmark::kNanosecond);

        const std::string throughput_name =
            "Mat4Mul/Throughput/" + std::string(implementation.name);
        benchmark::RegisterBenchmark(throughput_name, BenchmarkThroughput,
                                     implementation.multiply)
            ->UseRealTime()
            ->Unit(benchmark::kNanosecond);
    }
}

}  // namespace

#elif defined(YSM_ARM64)

alignas(64) thread_local unsigned char kBionicTlsAlignment;

#endif

int main(int argc, char** argv) {
    ysm::InitCpuInfo();
    if (!ysm::benchmarking::ValidateEulerZYXBenchmarks() ||
        !ysm::benchmarking::ValidateModelStateExtractBenchmarks()) {
        return EXIT_FAILURE;
    }
#ifdef YSM_X64
    const auto implementations = SupportedImplementations();
    if (!ValidateImplementations(implementations) ||
        !ysm::benchmarking::ValidateSinCosBenchmarks() ||
        !ysm::benchmarking::ValidateCubeTransformBenchmarks()) {
        return EXIT_FAILURE;
    }

    benchmark::AddCustomContext("ysm_simd",
                                std::string(SimdName(ysm::simd::kSupported)));
    benchmark::AddCustomContext("throughput_batch_size",
                                std::to_string(kThroughputBatchSize));
    RegisterBenchmarks(implementations);
    ysm::benchmarking::RegisterSinCosBenchmarks();
#elif defined(YSM_ARM64)
    if (!ysm::benchmarking::ValidateCubeTransformBenchmarks()) {
        return EXIT_FAILURE;
    }
    benchmark::DoNotOptimize(kBionicTlsAlignment);
    benchmark::AddCustomContext("ysm_simd", "NEON");
#else
#error "Unsupported benchmark architecture"
#endif
    ysm::benchmarking::RegisterEulerZYXBenchmarks();
    ysm::benchmarking::RegisterModelStateExtractBenchmarks();
    ysm::benchmarking::RegisterCubeTransformBenchmarks();

    benchmark::Initialize(&argc, argv);
    if (benchmark::ReportUnrecognizedArguments(argc, argv)) {
        return EXIT_FAILURE;
    }
    benchmark::RunSpecifiedBenchmarks();
    benchmark::Shutdown();
    return EXIT_SUCCESS;
}

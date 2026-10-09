#include "cube_transform.h"

#include <array>
#include <cmath>
#include <cstdio>
#include <string>
#include <string_view>

#define YSM_TRANSFORM_USE_APPROXIMATE_RSQRT 1

#include <benchmark/benchmark.h>
#include <cglm/mat3.h>
#include <cglm/mat4.h>

#include "cpu.h"
#include "inline.h"
#include "renderer/cube/transform.h"

#ifdef YSM_X64

namespace ysm::benchmarking {
namespace {

template <bool kTranslucent>
using CubeGroup = gfx::bake::CubeGroup<simd::Width::B512, kTranslucent>;

template <bool kTranslucent>
using Sse41CubeGroup = gfx::bake::CubeGroup<simd::Width::B128, kTranslucent>;

template <bool kTranslucent>
using Avx2CubeGroup = gfx::bake::CubeGroup<simd::Width::B256, kTranslucent>;

template <typename Group>
using TransformFunction = void (*)(const Group&,
                                   const gfx::renderer::RenderBoneState&,
                                   gfx::renderer::cube::CubeOutput<Group>&) noexcept;

template <typename Group>
struct TransformFixture {
    Group group;
    gfx::renderer::RenderBoneState state;
};

template <typename Group>
struct TransformPairFixture {
    std::array<Group, 2> groups;
    gfx::renderer::RenderBoneState state;
};

bool SupportsAvx2() noexcept {
    return kX86Features.avx2 && kX86Features.fma3;
}

bool SupportsAvx512() noexcept {
    return kX86Features.avx512f && kX86Features.avx512bw &&
           kX86Features.avx512vl;
}

template <bool kTranslucent>
CubeGroup<kTranslucent> MakeCubeGroup() {
    CubeGroup<kTranslucent> group{};
    group.cube_count = 2;

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        const auto value = static_cast<float>(vertex) - 7.5f;
        group.pos[0][vertex] = value * 0.75f;
        group.pos[1][vertex] = value * value * 0.125f - 2.0f;
        group.pos[2][vertex] = value * -0.5f + 1.25f;
    }

    constexpr float kNormals[6][3] = {
        {1.0f, 0.25f, -0.5f},  {-0.5f, 1.0f, 0.125f},  {0.25f, -0.75f, 1.0f},
        {-1.0f, -0.25f, 0.5f}, {0.5f, -1.0f, -0.125f}, {-0.25f, 0.75f, -1.0f},
    };
    constexpr float kTangents[6][3] = {
        {0.75f, 0.5f, -0.25f},  {-0.25f, 0.75f, 0.5f},  {0.5f, -0.25f, 0.75f},
        {-0.75f, -0.5f, 0.25f}, {0.25f, -0.75f, -0.5f}, {-0.5f, 0.25f, -0.75f},
    };

    for (uint32_t cube = 0; cube < 2; ++cube) {
        group.cube_attr[cube].quad_count = 6;
        group.cube_attr[cube].quad_count_after_culling = 3;
        for (uint32_t quad = 0; quad < 6; ++quad) {
            const auto attr =
                CubeGroup<kTranslucent>::GetQuadAttrIndex(cube, quad);
            const auto cube_sign = cube == 0 ? 1.0f : -1.0f;
            for (uint32_t axis = 0; axis < 3; ++axis) {
                group.normal[axis][attr] = kNormals[quad][axis] * cube_sign;
                group.tangent[axis][attr] = kTangents[quad][axis] * cube_sign;
                if constexpr (kTranslucent) {
                    group.center[axis][attr] =
                        static_cast<float>(attr + axis * 3) * 0.375f - 1.5f;
                }
            }
            group.plane_d[attr] = (static_cast<float>(quad) - 2.5f) * 0.2f;
            group.winding_sign[attr] = ((cube + quad) % 3 == 0) ? -1.0f : 1.0f;
            group.tangent[3][attr] =
                ((cube + quad) % 3 == 0)
                    ? 0.0f
                    : (((cube + quad) % 3 == 1) ? 1.0f : -1.0f);
        }
    }
    return group;
}

template <bool kTranslucent>
TransformFixture<Sse41CubeGroup<kTranslucent>> MakeSse41Fixture(
    const TransformFixture<CubeGroup<kTranslucent>>& fixture) {
    TransformFixture<Sse41CubeGroup<kTranslucent>> result{};
    result.state = fixture.state;
    result.group.cube_count = fixture.group.cube_count;

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            result.group.pos[axis][vertex] =
                fixture.group.pos[axis][vertex];
        }
    }

    for (uint32_t cube = 0; cube < 2; ++cube) {
        result.group.cube_attr[cube].quad_count =
            fixture.group.cube_attr[cube].quad_count;
        result.group.cube_attr[cube].quad_count_after_culling =
            fixture.group.cube_attr[cube].quad_count_after_culling;
        for (uint32_t quad = 0; quad < 6; ++quad) {
            const auto source =
                CubeGroup<kTranslucent>::GetQuadAttrIndex(cube, quad);
            const auto target =
                Sse41CubeGroup<kTranslucent>::GetQuadAttrIndex(cube, quad);
            for (uint32_t axis = 0; axis < 3; ++axis) {
                result.group.normal[axis][target] =
                    fixture.group.normal[axis][source];
                result.group.tangent[axis][target] =
                    fixture.group.tangent[axis][source];
                if constexpr (kTranslucent) {
                    result.group.center[axis][target] =
                        fixture.group.center[axis][source];
                }
            }
            result.group.plane_d[target] = fixture.group.plane_d[source];
            result.group.winding_sign[target] =
                fixture.group.winding_sign[source];
            result.group.tangent[3][target] =
                fixture.group.tangent[3][source];
        }
    }
    return result;
}

template <bool kTranslucent>
TransformPairFixture<Avx2CubeGroup<kTranslucent>> MakeAvx2Fixture(
    const TransformFixture<CubeGroup<kTranslucent>>& fixture) {
    TransformPairFixture<Avx2CubeGroup<kTranslucent>> result{};
    result.state = fixture.state;

    for (uint32_t cube = 0; cube < 2; ++cube) {
        auto& destination = result.groups[cube];
        destination.cube_count = 1;
        destination.cube_attr[0].quad_count =
            fixture.group.cube_attr[cube].quad_count;
        destination.cube_attr[0].quad_count_after_culling =
            fixture.group.cube_attr[cube].quad_count_after_culling;
        for (uint32_t vertex = 0; vertex < 8; ++vertex) {
            for (uint32_t axis = 0; axis < 3; ++axis) {
                destination.pos[axis][vertex] =
                    fixture.group.pos[axis][cube * 8 + vertex];
            }
        }

        for (uint32_t quad = 0; quad < 6; ++quad) {
            const auto source =
                CubeGroup<kTranslucent>::GetQuadAttrIndex(cube, quad);
            const auto target =
                Avx2CubeGroup<kTranslucent>::GetQuadAttrIndex(0, quad);
            for (uint32_t axis = 0; axis < 3; ++axis) {
                destination.normal[axis][target] =
                    fixture.group.normal[axis][source];
                destination.tangent[axis][target] =
                    fixture.group.tangent[axis][source];
                if constexpr (kTranslucent) {
                    destination.center[axis][target] =
                        fixture.group.center[axis][source];
                }
            }
            destination.plane_d[target] = fixture.group.plane_d[source];
            destination.winding_sign[target] =
                fixture.group.winding_sign[source];
            destination.tangent[3][target] =
                fixture.group.tangent[3][source];
        }
    }
    return result;
}

gfx::renderer::RenderBoneState MakeBoneState(bool uniform_scale) {
    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    state.pose[0][0] = 1.5f;
    state.pose[0][1] = 0.25f;
    state.pose[0][2] = -0.125f;
    state.pose[1][0] = -0.375f;
    state.pose[1][1] = 0.75f;
    state.pose[1][2] = 0.2f;
    state.pose[2][0] = 0.3f;
    state.pose[2][1] = -0.2f;
    state.pose[2][2] = 2.0f;
    state.pose[3][0] = 3.0f;
    state.pose[3][1] = -2.0f;
    state.pose[3][2] = 1.0f;

    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.625f;
    state.normal[0][1] = 0.2f;
    state.normal[0][2] = -0.1f;
    state.normal[1][0] = -0.3f;
    state.normal[1][1] = 1.25f;
    state.normal[1][2] = 0.15f;
    state.normal[2][0] = 0.125f;
    state.normal[2][1] = -0.25f;
    state.normal[2][2] = 0.5f;

    state.facing_coeff[0] = 0.625f;
    state.facing_coeff[1] = -0.375f;
    state.facing_coeff[2] = 0.875f;
    state.facing_coeff[3] = 0.25f;
    state.depth_z[0] = 0.5f;
    state.depth_z[1] = -0.25f;
    state.depth_z[2] = 1.75f;
    state.depth_z[3] = 0.75f;
    state.depth_w[0] = -0.125f;
    state.depth_w[1] = 0.25f;
    state.depth_w[2] = 0.375f;
    state.depth_w[3] = 2.0f;
    state.tangent_orientation = -1.0f;
    state.uniform_scale = uniform_scale;
    return state;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
YSM_NOINLINE void TransformScalar(
    const Group& group, const gfx::renderer::RenderBoneState& state,
    gfx::renderer::cube::CubeOutput<Group>& output) noexcept {
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, group, state, output);
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
YSM_NOINLINE void TransformSse41(
    const Group& group, const gfx::renderer::RenderBoneState& state,
    gfx::renderer::cube::CubeOutput<Group>& output) noexcept {
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::SSE41>{}, group, state, output);
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
YSM_TARGET_AVX2 YSM_NOINLINE void TransformAvx2(
    const Group& group, const gfx::renderer::RenderBoneState& state,
    gfx::renderer::cube::CubeOutput<Group>& output) noexcept {
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::AVX2>{}, group, state, output);
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
YSM_TARGET_AVX512 YSM_NOINLINE void TransformAvx512(
    const Group& group, const gfx::renderer::RenderBoneState& state,
    gfx::renderer::cube::CubeOutput<Group>& output) noexcept {
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::AVX512>{}, group, state, output);
}

template <typename Group>
void RunTransformBenchmark(benchmark::State& state,
                           TransformFunction<Group> transform,
                           const TransformFixture<Group>* fixture) {
    gfx::renderer::cube::CubeOutput<Group> output{};
    for (auto _ : state) {
        transform(fixture->group, fixture->state, output);
        benchmark::DoNotOptimize(output);
    }
    state.SetItemsProcessed(state.iterations() * fixture->group.cube_count);
}

template <typename Group>
void RunTransformPairBenchmark(benchmark::State& state,
                               TransformFunction<Group> transform,
                               const TransformPairFixture<Group>* fixture) {
    std::array<gfx::renderer::cube::CubeOutput<Group>, 2> output{};
    for (auto _ : state) {
        transform(fixture->groups[0], fixture->state, output[0]);
        transform(fixture->groups[1], fixture->state, output[1]);
        benchmark::DoNotOptimize(output);
    }
    state.SetItemsProcessed(state.iterations() * fixture->groups.size());
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group, typename Sse41Group, typename Avx2Group>
void RegisterScenario(std::string_view name,
                      const TransformFixture<Group>& fixture,
                      const TransformFixture<Sse41Group>& sse41_fixture,
                      const TransformPairFixture<Avx2Group>& avx2_fixture) {
    const auto scalar_name = "CubeTransform/" + std::string(name) + "/Scalar";
    benchmark::RegisterBenchmark(
        scalar_name, RunTransformBenchmark<Group>,
        &TransformScalar<kCulling, kIris, kHasPbr, kPosOnly, Group>, &fixture)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);

    const auto sse41_name = "CubeTransform/" + std::string(name) + "/SSE41";
    benchmark::RegisterBenchmark(
        sse41_name, RunTransformBenchmark<Sse41Group>,
        &TransformSse41<kCulling, kIris, kHasPbr, kPosOnly, Sse41Group>,
        &sse41_fixture)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);

    if (SupportsAvx2()) {
        const auto avx2_name = "CubeTransform/" + std::string(name) + "/AVX2";
        benchmark::RegisterBenchmark(
            avx2_name, RunTransformPairBenchmark<Avx2Group>,
            &TransformAvx2<kCulling, kIris, kHasPbr, kPosOnly, Avx2Group>,
            &avx2_fixture)
            ->UseRealTime()
            ->Unit(benchmark::kNanosecond);
    }

    if (SupportsAvx512()) {
        const auto avx512_name =
            "CubeTransform/" + std::string(name) + "/AVX512";
        benchmark::RegisterBenchmark(
            avx512_name, RunTransformBenchmark<Group>,
            &TransformAvx512<kCulling, kIris, kHasPbr, kPosOnly, Group>,
            &fixture)
            ->UseRealTime()
            ->Unit(benchmark::kNanosecond);
    }
}

bool NearlyEqual(float left, float right) {
    return std::abs(left - right) <= 1.0e-5f;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
bool ValidateAvx512Scenario(std::string_view name,
                            const TransformFixture<Group>& fixture) {
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, fixture.group, fixture.state, expected);
    TransformAvx512<kCulling, kIris, kHasPbr, kPosOnly>(fixture.group,
                                                        fixture.state, actual);

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            if (!NearlyEqual(expected.pos[axis][vertex],
                             actual.pos[axis][vertex])) {
                std::fprintf(stderr,
                             "%.*s failed position validation at axis %u, "
                             "vertex %u\n",
                             static_cast<int>(name.size()), name.data(), axis,
                             vertex);
                return false;
            }
        }
    }

    for (uint32_t cube = 0; cube < fixture.group.cube_count; ++cube) {
        for (uint32_t quad = 0; quad < fixture.group.cube_attr[cube].quad_count;
             ++quad) {
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
            if (expected.back_face[attr] != actual.back_face[attr] ||
                expected.normal[attr] != actual.normal[attr]) {
                std::fprintf(stderr,
                             "%.*s failed face validation at cube %u, quad "
                             "%u\n",
                             static_cast<int>(name.size()), name.data(), cube,
                             quad);
                return false;
            }
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                if (expected.tangent[attr] != actual.tangent[attr]) {
                    std::fprintf(stderr,
                                 "%.*s failed tangent validation at cube %u, "
                                 "quad %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 cube, quad);
                    return false;
                }
            }
            if constexpr (Group::kTranslucent) {
                if (!NearlyEqual(expected.face_depth[attr],
                                 actual.face_depth[attr])) {
                    std::fprintf(stderr,
                                 "%.*s failed depth validation at cube %u, "
                                 "quad %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 cube, quad);
                    return false;
                }
            }
        }
    }
    return true;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group, typename Sse41Group>
bool ValidateSse41Scenario(
    std::string_view name, const TransformFixture<Group>& fixture,
    const TransformFixture<Sse41Group>& sse41_fixture) {
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Sse41Group> actual{};
    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, fixture.group, fixture.state, expected);
    TransformSse41<kCulling, kIris, kHasPbr, kPosOnly>(
        sse41_fixture.group, sse41_fixture.state, actual);

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            if (!NearlyEqual(expected.pos[axis][vertex],
                             actual.pos[axis][vertex])) {
                std::fprintf(stderr,
                             "%.*s failed SSE4.1 position validation at "
                             "axis %u, vertex %u\n",
                             static_cast<int>(name.size()), name.data(), axis,
                             vertex);
                return false;
            }
        }
    }

    for (uint32_t cube = 0; cube < 2; ++cube) {
        for (uint32_t quad = 0;
             quad < fixture.group.cube_attr[cube].quad_count; ++quad) {
            const auto expected_attr = Group::GetQuadAttrIndex(cube, quad);
            const auto actual_attr = Sse41Group::GetQuadAttrIndex(cube, quad);
            if (expected.back_face[expected_attr] !=
                    actual.back_face[actual_attr] ||
                expected.normal[expected_attr] != actual.normal[actual_attr]) {
                std::fprintf(stderr,
                             "%.*s failed SSE4.1 face validation at cube %u, "
                             "quad %u\n",
                             static_cast<int>(name.size()), name.data(), cube,
                             quad);
                return false;
            }
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                if (expected.tangent[expected_attr] !=
                    actual.tangent[actual_attr]) {
                    std::fprintf(
                        stderr,
                        "%.*s failed SSE4.1 tangent validation at cube %u, "
                        "quad %u\n",
                        static_cast<int>(name.size()), name.data(), cube,
                        quad);
                    return false;
                }
            }
            if constexpr (Group::kTranslucent) {
                if (!NearlyEqual(expected.face_depth[expected_attr],
                                 actual.face_depth[actual_attr])) {
                    std::fprintf(
                        stderr,
                        "%.*s failed SSE4.1 depth validation at cube %u, "
                        "quad %u\n",
                        static_cast<int>(name.size()), name.data(), cube,
                        quad);
                    return false;
                }
            }
        }
    }
    return true;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group, typename Avx2Group>
bool ValidateAvx2Scenario(
    std::string_view name, const TransformFixture<Group>& fixture,
    const TransformPairFixture<Avx2Group>& avx2_fixture) {
    gfx::renderer::cube::CubeOutput<Group> expected{};
    std::array<gfx::renderer::cube::CubeOutput<Avx2Group>, 2> actual{};
    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, fixture.group, fixture.state, expected);
    for (uint32_t cube = 0; cube < 2; ++cube) {
        TransformAvx2<kCulling, kIris, kHasPbr, kPosOnly>(
            avx2_fixture.groups[cube], avx2_fixture.state, actual[cube]);
    }

    for (uint32_t cube = 0; cube < 2; ++cube) {
        for (uint32_t vertex = 0; vertex < 8; ++vertex) {
            for (uint32_t axis = 0; axis < 3; ++axis) {
                if (!NearlyEqual(expected.pos[axis][cube * 8 + vertex],
                                 actual[cube].pos[axis][vertex])) {
                    std::fprintf(stderr,
                                 "%.*s failed AVX2 position validation at "
                                 "axis %u, cube %u, vertex %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 axis, cube, vertex);
                    return false;
                }
            }
        }

        for (uint32_t quad = 0;
             quad < fixture.group.cube_attr[cube].quad_count; ++quad) {
            const auto expected_attr = Group::GetQuadAttrIndex(cube, quad);
            const auto actual_attr = Avx2Group::GetQuadAttrIndex(0, quad);
            if (expected.back_face[expected_attr] !=
                    actual[cube].back_face[actual_attr] ||
                expected.normal[expected_attr] !=
                    actual[cube].normal[actual_attr]) {
                std::fprintf(stderr,
                             "%.*s failed AVX2 face validation at cube %u, "
                             "quad %u\n",
                             static_cast<int>(name.size()), name.data(), cube,
                             quad);
                return false;
            }
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                if (expected.tangent[expected_attr] !=
                    actual[cube].tangent[actual_attr]) {
                    std::fprintf(stderr,
                                 "%.*s failed AVX2 tangent validation at cube "
                                 "%u, quad %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 cube, quad);
                    return false;
                }
            }
            if constexpr (Group::kTranslucent) {
                if (!NearlyEqual(expected.face_depth[expected_attr],
                                 actual[cube].face_depth[actual_attr])) {
                    std::fprintf(stderr,
                                 "%.*s failed AVX2 depth validation at cube "
                                 "%u, quad %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 cube, quad);
                    return false;
                }
            }
        }
    }
    return true;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group, typename Sse41Group, typename Avx2Group>
bool ValidateScenario(
    std::string_view name, const TransformFixture<Group>& fixture,
    const TransformFixture<Sse41Group>& sse41_fixture,
    const TransformPairFixture<Avx2Group>& avx2_fixture) {
    if (!ValidateSse41Scenario<kCulling, kIris, kHasPbr, kPosOnly>(
            name, fixture, sse41_fixture)) {
        return false;
    }
    if (SupportsAvx2() &&
        !ValidateAvx2Scenario<kCulling, kIris, kHasPbr, kPosOnly>(
            name, fixture, avx2_fixture)) {
        return false;
    }
    return !SupportsAvx512() ||
           ValidateAvx512Scenario<kCulling, kIris, kHasPbr, kPosOnly>(
               name, fixture);
}

const TransformFixture<CubeGroup<false>> kOpaqueUniform{MakeCubeGroup<false>(),
                                                        MakeBoneState(true)};
const TransformFixture<CubeGroup<false>> kOpaqueNonUniform{
    MakeCubeGroup<false>(), MakeBoneState(false)};
const TransformFixture<CubeGroup<true>> kTranslucentNonUniform{
    MakeCubeGroup<true>(), MakeBoneState(false)};
const auto kSse41OpaqueUniform = MakeSse41Fixture(kOpaqueUniform);
const auto kSse41OpaqueNonUniform = MakeSse41Fixture(kOpaqueNonUniform);
const auto kSse41TranslucentNonUniform =
    MakeSse41Fixture(kTranslucentNonUniform);
const auto kAvx2OpaqueUniform = MakeAvx2Fixture(kOpaqueUniform);
const auto kAvx2OpaqueNonUniform = MakeAvx2Fixture(kOpaqueNonUniform);
const auto kAvx2TranslucentNonUniform =
    MakeAvx2Fixture(kTranslucentNonUniform);

}  // namespace

bool ValidateCubeTransformBenchmarks() {
    return ValidateScenario<true, false, false, false>("OpaqueBasicCulling",
                                                       kOpaqueNonUniform,
                                                       kSse41OpaqueNonUniform,
                                                       kAvx2OpaqueNonUniform) &&
           ValidateScenario<false, true, true, false>("OpaqueIrisPbrUniform",
                                                      kOpaqueUniform,
                                                      kSse41OpaqueUniform,
                                                      kAvx2OpaqueUniform) &&
           ValidateScenario<false, true, true, false>("OpaqueIrisPbrNonUniform",
                                                      kOpaqueNonUniform,
                                                      kSse41OpaqueNonUniform,
                                                      kAvx2OpaqueNonUniform) &&
           ValidateScenario<false, true, true, false>(
               "TranslucentIrisPbrNonUniform", kTranslucentNonUniform,
               kSse41TranslucentNonUniform,
               kAvx2TranslucentNonUniform);
}

void RegisterCubeTransformBenchmarks() {
    benchmark::AddCustomContext("cube_transform_cubes_per_iteration", "2");
    benchmark::AddCustomContext("cube_transform_quads_per_iteration", "12");
    RegisterScenario<true, false, false, false>("OpaqueBasicCulling",
                                                kOpaqueNonUniform,
                                                kSse41OpaqueNonUniform,
                                                kAvx2OpaqueNonUniform);
    RegisterScenario<false, true, true, false>("OpaqueIrisPbrUniform",
                                               kOpaqueUniform,
                                               kSse41OpaqueUniform,
                                               kAvx2OpaqueUniform);
    RegisterScenario<false, true, true, false>("OpaqueIrisPbrNonUniform",
                                               kOpaqueNonUniform,
                                               kSse41OpaqueNonUniform,
                                               kAvx2OpaqueNonUniform);
    RegisterScenario<false, true, true, false>("TranslucentIrisPbrNonUniform",
                                               kTranslucentNonUniform,
                                               kSse41TranslucentNonUniform,
                                               kAvx2TranslucentNonUniform);
}

}  // namespace ysm::benchmarking

#elif defined(YSM_ARM64)

namespace ysm::benchmarking {
namespace {

template <bool kTranslucent>
using CubeGroup = gfx::bake::CubeGroup<simd::Width::B128, kTranslucent>;

template <typename Group>
using TransformFunction = void (*)(const Group&,
                                   const gfx::renderer::RenderBoneState&,
                                   gfx::renderer::cube::CubeOutput<Group>&) noexcept;

template <typename Group>
struct TransformFixture {
    Group group;
    gfx::renderer::RenderBoneState state;
};

template <bool kTranslucent>
CubeGroup<kTranslucent> MakeNeonCubeGroup() {
    CubeGroup<kTranslucent> group{};
    group.cube_count = 2;

    for (uint32_t cube = 0; cube < group.cube_count; ++cube) {
        group.cube_attr[cube].quad_count = 6;
        group.cube_attr[cube].quad_count_after_culling = 4;
        for (uint32_t vertex = 0; vertex < 8; ++vertex) {
            const auto index = cube * 8 + vertex;
            group.pos[0][index] =
                static_cast<float>((vertex & 1U) != 0U) + cube * 1.25f;
            group.pos[1][index] =
                static_cast<float>((vertex & 2U) != 0U) - cube * 0.5f;
            group.pos[2][index] =
                static_cast<float>((vertex & 4U) != 0U) + cube * 0.75f;
        }
    }

    constexpr float kNormals[6][3] = {
        {1.0f, 0.25f, -0.5f},  {-0.5f, 1.0f, 0.125f},  {0.25f, -0.75f, 1.0f},
        {-1.0f, -0.25f, 0.5f}, {0.5f, -1.0f, -0.125f}, {-0.25f, 0.75f, -1.0f},
    };
    constexpr float kTangents[6][3] = {
        {0.75f, 0.5f, -0.25f},  {-0.25f, 0.75f, 0.5f},  {0.5f, -0.25f, 0.75f},
        {-0.75f, -0.5f, 0.25f}, {0.25f, -0.75f, -0.5f}, {-0.5f, 0.25f, -0.75f},
    };

    for (uint32_t cube = 0; cube < group.cube_count; ++cube) {
        for (uint32_t quad = 0; quad < 6; ++quad) {
            const auto attr =
                CubeGroup<kTranslucent>::GetQuadAttrIndex(cube, quad);
            for (uint32_t axis = 0; axis < 3; ++axis) {
                group.normal[axis][attr] = kNormals[quad][axis];
                group.tangent[axis][attr] = kTangents[quad][axis];
                if constexpr (kTranslucent) {
                    group.center[axis][attr] =
                        0.25f * static_cast<float>(axis + 1) +
                        0.5f * static_cast<float>(cube) +
                        0.125f * static_cast<float>(quad);
                }
            }
            group.plane_d[attr] = (static_cast<float>(quad) - 2.5f) * 0.2f;
            group.winding_sign[attr] = ((cube + quad) % 3 == 0) ? -1.0f : 1.0f;
            group.tangent[3][attr] =
                ((cube + quad) % 3 == 0)
                    ? 0.0f
                    : (((cube + quad) & 1U) != 0U ? -1.0f : 1.0f);
        }
    }
    return group;
}

gfx::renderer::RenderBoneState MakeNeonBoneState(bool uniform_scale) {
    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    state.pose[0][0] = 1.5f;
    state.pose[0][1] = 0.25f;
    state.pose[0][2] = -0.125f;
    state.pose[1][0] = -0.375f;
    state.pose[1][1] = 0.75f;
    state.pose[1][2] = 0.2f;
    state.pose[2][0] = 0.3f;
    state.pose[2][1] = -0.2f;
    state.pose[2][2] = 2.0f;
    state.pose[3][0] = 3.0f;
    state.pose[3][1] = -2.0f;
    state.pose[3][2] = 1.0f;

    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.625f;
    state.normal[0][1] = 0.2f;
    state.normal[0][2] = -0.1f;
    state.normal[1][0] = -0.3f;
    state.normal[1][1] = 1.25f;
    state.normal[1][2] = 0.15f;
    state.normal[2][0] = 0.125f;
    state.normal[2][1] = -0.25f;
    state.normal[2][2] = 0.5f;

    state.facing_coeff[0] = 0.625f;
    state.facing_coeff[1] = -0.375f;
    state.facing_coeff[2] = 0.875f;
    state.facing_coeff[3] = 0.25f;
    state.depth_z[0] = 0.5f;
    state.depth_z[1] = -0.25f;
    state.depth_z[2] = 1.75f;
    state.depth_z[3] = 0.75f;
    state.depth_w[0] = -0.125f;
    state.depth_w[1] = 0.25f;
    state.depth_w[2] = 0.375f;
    state.depth_w[3] = 2.0f;
    state.tangent_orientation = -1.0f;
    state.uniform_scale = uniform_scale;
    return state;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
YSM_NOINLINE void TransformScalar(
    const Group& group, const gfx::renderer::RenderBoneState& state,
    gfx::renderer::cube::CubeOutput<Group>& output) noexcept {
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, group, state, output);
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
YSM_NOINLINE void TransformNeon(
    const Group& group, const gfx::renderer::RenderBoneState& state,
    gfx::renderer::cube::CubeOutput<Group>& output) noexcept {
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::NEON>{}, group, state, output);
}

template <typename Group>
void RunTransformBenchmark(benchmark::State& state,
                           TransformFunction<Group> transform,
                           const TransformFixture<Group>* fixture) {
    gfx::renderer::cube::CubeOutput<Group> output{};
    for (auto _ : state) {
        transform(fixture->group, fixture->state, output);
        benchmark::DoNotOptimize(output);
    }
    state.SetItemsProcessed(state.iterations() * fixture->group.cube_count);
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
void RegisterNeonScenario(std::string_view name,
                          const TransformFixture<Group>& fixture) {
    const auto scalar_name = "CubeTransform/" + std::string(name) + "/Scalar";
    benchmark::RegisterBenchmark(
        scalar_name, RunTransformBenchmark<Group>,
        &TransformScalar<kCulling, kIris, kHasPbr, kPosOnly, Group>, &fixture)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);

    const auto neon_name = "CubeTransform/" + std::string(name) + "/NEON";
    benchmark::RegisterBenchmark(
        neon_name, RunTransformBenchmark<Group>,
        &TransformNeon<kCulling, kIris, kHasPbr, kPosOnly, Group>, &fixture)
        ->UseRealTime()
        ->Unit(benchmark::kNanosecond);
}

bool NearlyEqualNeon(float left, float right) {
    return std::abs(left - right) <= 1.0e-5f;
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
bool ValidateNeonScenario(std::string_view name,
                          const TransformFixture<Group>& fixture) {
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, fixture.group, fixture.state, expected);
    TransformNeon<kCulling, kIris, kHasPbr, kPosOnly>(fixture.group,
                                                      fixture.state, actual);

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            if (!NearlyEqualNeon(expected.pos[axis][vertex],
                                 actual.pos[axis][vertex])) {
                std::fprintf(stderr,
                             "%.*s failed NEON position validation at axis "
                             "%u, vertex %u\n",
                             static_cast<int>(name.size()), name.data(), axis,
                             vertex);
                return false;
            }
        }
    }

    for (uint32_t cube = 0; cube < fixture.group.cube_count; ++cube) {
        for (uint32_t quad = 0; quad < fixture.group.cube_attr[cube].quad_count;
             ++quad) {
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
            if (expected.back_face[attr] != actual.back_face[attr] ||
                expected.normal[attr] != actual.normal[attr]) {
                std::fprintf(stderr,
                             "%.*s failed NEON face validation at cube %u, "
                             "quad %u\n",
                             static_cast<int>(name.size()), name.data(), cube,
                             quad);
                return false;
            }
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                if (expected.tangent[attr] != actual.tangent[attr]) {
                    std::fprintf(stderr,
                                 "%.*s failed NEON tangent validation at cube "
                                 "%u, quad %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 cube, quad);
                    return false;
                }
            }
            if constexpr (Group::kTranslucent) {
                if (!NearlyEqualNeon(expected.face_depth[attr],
                                     actual.face_depth[attr])) {
                    std::fprintf(stderr,
                                 "%.*s failed NEON depth validation at cube "
                                 "%u, quad %u\n",
                                 static_cast<int>(name.size()), name.data(),
                                 cube, quad);
                    return false;
                }
            }
        }
    }
    return true;
}

const TransformFixture<CubeGroup<false>> kNeonOpaqueUniform{
    MakeNeonCubeGroup<false>(), MakeNeonBoneState(true)};
const TransformFixture<CubeGroup<false>> kNeonOpaqueNonUniform{
    MakeNeonCubeGroup<false>(), MakeNeonBoneState(false)};
const TransformFixture<CubeGroup<true>> kNeonTranslucentNonUniform{
    MakeNeonCubeGroup<true>(), MakeNeonBoneState(false)};

}  // namespace

bool ValidateCubeTransformBenchmarks() {
    return ValidateNeonScenario<true, false, false, false>(
               "OpaqueBasicCulling", kNeonOpaqueNonUniform) &&
           ValidateNeonScenario<false, true, true, false>(
               "OpaqueIrisPbrUniform", kNeonOpaqueUniform) &&
           ValidateNeonScenario<false, true, true, false>(
               "OpaqueIrisPbrNonUniform", kNeonOpaqueNonUniform) &&
           ValidateNeonScenario<false, true, true, false>(
               "TranslucentIrisPbrNonUniform", kNeonTranslucentNonUniform);
}

void RegisterCubeTransformBenchmarks() {
    benchmark::AddCustomContext("cube_transform_cubes_per_iteration", "2");
    benchmark::AddCustomContext("cube_transform_quads_per_iteration", "12");
    RegisterNeonScenario<true, false, false, false>("OpaqueBasicCulling",
                                                    kNeonOpaqueNonUniform);
    RegisterNeonScenario<false, true, true, false>("OpaqueIrisPbrUniform",
                                                   kNeonOpaqueUniform);
    RegisterNeonScenario<false, true, true, false>("OpaqueIrisPbrNonUniform",
                                                   kNeonOpaqueNonUniform);
    RegisterNeonScenario<false, true, true, false>(
        "TranslucentIrisPbrNonUniform", kNeonTranslucentNonUniform);
}

}  // namespace ysm::benchmarking

#else
#error "Unsupported benchmark architecture"
#endif

#include <cstdint>
#include <limits>

#include <gtest/gtest.h>

#include "cpu.h"
#include "renderer/buffer/quad_ref.h"
#include "renderer/cube/transform.h"

namespace ysm::test {
namespace {

uint8_t PackedByte(uint32_t value, uint32_t index) {
    return static_cast<uint8_t>(value >> (index * 8));
}

#ifdef YSM_X64
bool SupportsAvx512CubeTransform() {
    const auto features = cpu_features::GetX86Info().features;
    return features.avx512f && features.avx512bw && features.avx512vl;
}

bool SupportsAvx2CubeTransform() {
    const auto features = cpu_features::GetX86Info().features;
    return features.avx2 && features.fma3;
}
#endif

template <bool kTranslucent>
auto MakeB128CubeGroup() {
    using Group = gfx::bake::CubeGroup<simd::Width::B128, kTranslucent>;
    Group group{};
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
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
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
auto MakeAvx2CubeGroup() {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, kTranslucent>;
    Group group{};
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 6;
    group.cube_attr[0].quad_count_after_culling = 3;

    for (uint32_t vertex = 0; vertex < 8; ++vertex) {
        const auto value = static_cast<float>(vertex) - 3.5f;
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

    for (uint32_t quad = 0; quad < 6; ++quad) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            group.normal[axis][quad] = kNormals[quad][axis];
            group.tangent[axis][quad] = kTangents[quad][axis];
            if constexpr (kTranslucent) {
                group.center[axis][quad] =
                    static_cast<float>(quad + axis * 3) * 0.375f - 1.5f;
            }
        }
        group.plane_d[quad] = (static_cast<float>(quad) - 2.5f) * 0.2f;
        group.winding_sign[quad] = quad % 3 == 0 ? -1.0f : 1.0f;
        group.tangent[3][quad] =
            quad % 3 == 0 ? 0.0f : (quad % 3 == 1 ? 1.0f : -1.0f);
    }
    return group;
}

template <bool kTranslucent>
auto MakeAvx512CubeGroup() {
    using Group = gfx::bake::CubeGroup<simd::Width::B512, kTranslucent>;
    Group group{};
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
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
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

gfx::renderer::RenderBoneState MakeSimdBoneState(bool uniform_scale) {
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

template <typename Group, typename SimdTag>
void ExpectSingleQuadFastPath(SimdTag tag) {
    constexpr float kPositionSentinel = 12345.0f;
    constexpr uint32_t kAttributeSentinel = 0xdeadbeef;
    constexpr float kDepthSentinel = 54321.0f;

    Group group{};
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 1;
    group.cube_attr[0].quad_count_after_culling = 1;
    for (uint32_t vertex = 0; vertex < 4; ++vertex) {
        group.pos[0][vertex] = static_cast<float>(vertex) + 0.25f;
        group.pos[1][vertex] = static_cast<float>(vertex) * -0.5f;
        group.pos[2][vertex] = static_cast<float>(vertex) * 0.75f - 1.0f;
    }
    group.normal[0][0] = 1.0f;
    group.normal[1][0] = 0.5f;
    group.plane_d[0] = 0.25f;
    group.winding_sign[0] = 1.0f;
    group.tangent[1][0] = 1.0f;
    group.tangent[3][0] = -1.0f;
    if constexpr (Group::kTranslucent) {
        group.center[0][0] = 1.0f;
        group.center[1][0] = 2.0f;
        group.center[2][0] = 3.0f;
    }

    auto state = MakeSimdBoneState(false);
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    for (auto& axis : expected.pos) {
        axis.fill(kPositionSentinel);
    }
    for (auto& axis : actual.pos) {
        axis.fill(kPositionSentinel);
    }
    expected.normal.fill(kAttributeSentinel);
    actual.normal.fill(kAttributeSentinel);
    expected.tangent.fill(kAttributeSentinel);
    actual.tangent.fill(kAttributeSentinel);
    expected.face_depth.fill(kDepthSentinel);
    actual.face_depth.fill(kDepthSentinel);
    expected.back_face.fill(true);
    actual.back_face.fill(true);

    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<false, true, true, false>(
        tag, group, state, actual);

    for (uint32_t vertex = 0; vertex < 4; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_NEAR(actual.pos[axis][vertex],
                        expected.pos[axis][vertex], 0.00001f);
        }
    }
    EXPECT_EQ(actual.back_face[0], expected.back_face[0]);
    EXPECT_EQ(actual.normal[0], expected.normal[0]);
    EXPECT_EQ(actual.tangent[0], expected.tangent[0]);
    if constexpr (Group::kTranslucent) {
        EXPECT_NEAR(actual.face_depth[0], expected.face_depth[0],
                    0.00001f);
    }

    for (uint32_t vertex = 4; vertex < 8 * Group::kCubeGroupCapacity;
         ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_FLOAT_EQ(actual.pos[axis][vertex], kPositionSentinel);
        }
    }
    for (uint32_t attr = 1; attr < Group::kQuadAttrCapacity; ++attr) {
        EXPECT_TRUE(actual.back_face[attr]);
        EXPECT_EQ(actual.normal[attr], kAttributeSentinel);
        EXPECT_EQ(actual.tangent[attr], kAttributeSentinel);
        EXPECT_FLOAT_EQ(actual.face_depth[attr], kDepthSentinel);
    }
}

template <typename SimdTag>
void ExpectSingleCubeFastPath(SimdTag tag) {
    using Group = gfx::bake::CubeGroup<simd::Width::B128, true>;
    constexpr float kPositionSentinel = 12345.0f;
    constexpr uint32_t kAttributeSentinel = 0xdeadbeef;
    constexpr float kDepthSentinel = 54321.0f;

    auto group = MakeB128CubeGroup<true>();
    group.cube_count = 1;
    const auto state = MakeSimdBoneState(false);

    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    for (auto& axis : actual.pos) {
        axis.fill(kPositionSentinel);
    }
    actual.normal.fill(kAttributeSentinel);
    actual.tangent.fill(kAttributeSentinel);
    actual.face_depth.fill(kDepthSentinel);
    actual.back_face.fill(true);

    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<false, true, true, false>(
        tag, group, state, actual);

    for (uint32_t vertex = 0; vertex < 8; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_NEAR(actual.pos[axis][vertex],
                        expected.pos[axis][vertex], 0.00001f);
        }
    }
    for (uint32_t attr = 0; attr < 6; ++attr) {
        EXPECT_EQ(actual.back_face[attr], expected.back_face[attr]);
        EXPECT_EQ(actual.normal[attr], expected.normal[attr]);
        EXPECT_EQ(actual.tangent[attr], expected.tangent[attr]);
        EXPECT_NEAR(actual.face_depth[attr], expected.face_depth[attr],
                    0.00001f);
    }

    for (uint32_t vertex = 8; vertex < 16; ++vertex) {
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_FLOAT_EQ(actual.pos[axis][vertex], kPositionSentinel);
        }
    }
    for (uint32_t attr = 8; attr < Group::kQuadAttrCapacity; ++attr) {
        EXPECT_TRUE(actual.back_face[attr]);
        EXPECT_EQ(actual.normal[attr], kAttributeSentinel);
        EXPECT_EQ(actual.tangent[attr], kAttributeSentinel);
        EXPECT_FLOAT_EQ(actual.face_depth[attr], kDepthSentinel);
    }
}

#ifdef YSM_X64
template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
void ExpectSse41MatchesGeneric(const Group& group,
                               const gfx::renderer::RenderBoneState& state) {
    constexpr uint32_t kTangentSentinel = 0xdeadbeef;
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    expected.tangent.fill(kTangentSentinel);
    actual.tangent.fill(kTangentSentinel);

    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::SSE41>{}, group, state, actual);

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        SCOPED_TRACE(testing::Message() << "vertex " << vertex);
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_NEAR(actual.pos[axis][vertex], expected.pos[axis][vertex],
                        0.00001f);
        }
    }

    bool saw_front_face = false;
    bool saw_back_face = false;
    for (uint32_t cube = 0; cube < 2; ++cube) {
        for (uint32_t quad = 0; quad < group.cube_attr[cube].quad_count;
             ++quad) {
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
            SCOPED_TRACE(testing::Message()
                         << "cube " << cube << ", quad " << quad);
            EXPECT_EQ(actual.back_face[attr], expected.back_face[attr]);
            EXPECT_EQ(actual.normal[attr], expected.normal[attr]);
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                EXPECT_EQ(actual.tangent[attr], expected.tangent[attr]);
            }
            if constexpr (Group::kTranslucent) {
                EXPECT_NEAR(actual.face_depth[attr], expected.face_depth[attr],
                            0.00001f);
            }
            saw_back_face |= actual.back_face[attr];
            saw_front_face |= !actual.back_face[attr];
        }
    }
    EXPECT_TRUE(saw_front_face);
    EXPECT_TRUE(saw_back_face);

    if constexpr (!(kIris && kHasPbr && !kPosOnly)) {
        for (const auto tangent : actual.tangent) {
            EXPECT_EQ(tangent, kTangentSentinel);
        }
    }
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
void ExpectAvx2MatchesGeneric(const Group& group,
                              const gfx::renderer::RenderBoneState& state) {
    constexpr uint32_t kTangentSentinel = 0xdeadbeef;
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    expected.tangent.fill(kTangentSentinel);
    actual.tangent.fill(kTangentSentinel);

    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::AVX2>{}, group, state, actual);

    for (uint32_t vertex = 0; vertex < 8; ++vertex) {
        SCOPED_TRACE(testing::Message() << "vertex " << vertex);
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_NEAR(actual.pos[axis][vertex], expected.pos[axis][vertex],
                        0.00001f);
        }
    }

    bool saw_front_face = false;
    bool saw_back_face = false;
    for (uint32_t quad = 0; quad < group.cube_attr[0].quad_count; ++quad) {
        SCOPED_TRACE(testing::Message() << "quad " << quad);
        EXPECT_EQ(actual.back_face[quad], expected.back_face[quad]);
        EXPECT_EQ(actual.normal[quad], expected.normal[quad]);
        if constexpr (kIris && kHasPbr && !kPosOnly) {
            EXPECT_EQ(actual.tangent[quad], expected.tangent[quad]);
        }
        if constexpr (Group::kTranslucent) {
            EXPECT_NEAR(actual.face_depth[quad], expected.face_depth[quad],
                        0.00001f);
        }
        saw_back_face |= actual.back_face[quad];
        saw_front_face |= !actual.back_face[quad];
    }
    EXPECT_TRUE(saw_front_face);
    EXPECT_TRUE(saw_back_face);

    if constexpr (!(kIris && kHasPbr && !kPosOnly)) {
        for (const auto tangent : actual.tangent) {
            EXPECT_EQ(tangent, kTangentSentinel);
        }
    }
}

template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
void ExpectAvx512MatchesGeneric(const Group& group,
                                const gfx::renderer::RenderBoneState& state) {
    constexpr uint32_t kTangentSentinel = 0xdeadbeef;
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    expected.tangent.fill(kTangentSentinel);
    actual.tangent.fill(kTangentSentinel);

    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::AVX512>{}, group, state, actual);

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        SCOPED_TRACE(testing::Message() << "vertex " << vertex);
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_NEAR(actual.pos[axis][vertex], expected.pos[axis][vertex],
                        0.00001f);
        }
    }

    bool saw_front_face = false;
    bool saw_back_face = false;
    for (uint32_t cube = 0; cube < 2; ++cube) {
        for (uint32_t quad = 0; quad < group.cube_attr[cube].quad_count;
             ++quad) {
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
            SCOPED_TRACE(testing::Message()
                         << "cube " << cube << ", quad " << quad);
            EXPECT_EQ(actual.back_face[attr], expected.back_face[attr]);
            EXPECT_EQ(actual.normal[attr], expected.normal[attr]);
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                EXPECT_EQ(actual.tangent[attr], expected.tangent[attr]);
            }
            if constexpr (Group::kTranslucent) {
                EXPECT_NEAR(actual.face_depth[attr], expected.face_depth[attr],
                            0.00001f);
            }
            saw_back_face |= actual.back_face[attr];
            saw_front_face |= !actual.back_face[attr];
        }
    }
    EXPECT_TRUE(saw_front_face);
    EXPECT_TRUE(saw_back_face);

    if constexpr (!(kIris && kHasPbr && !kPosOnly)) {
        for (const auto tangent : actual.tangent) {
            EXPECT_EQ(tangent, kTangentSentinel);
        }
    }
}
#endif

#ifdef YSM_ARM64
template <bool kCulling, bool kIris, bool kHasPbr, bool kPosOnly,
          typename Group>
void ExpectNeonMatchesGeneric(const Group& group,
                              const gfx::renderer::RenderBoneState& state) {
    constexpr uint32_t kTangentSentinel = 0xdeadbeef;
    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    expected.tangent.fill(kTangentSentinel);
    actual.tangent.fill(kTangentSentinel);

    gfx::renderer::cube::Transform<false, kIris, kHasPbr, kPosOnly>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<kCulling, kIris, kHasPbr, kPosOnly>(
        simd::Tag<simd::Type::NEON>{}, group, state, actual);

    for (uint32_t vertex = 0; vertex < 16; ++vertex) {
        SCOPED_TRACE(testing::Message() << "vertex " << vertex);
        for (uint32_t axis = 0; axis < 3; ++axis) {
            EXPECT_NEAR(actual.pos[axis][vertex], expected.pos[axis][vertex],
                        0.00001f);
        }
    }

    bool saw_front_face = false;
    bool saw_back_face = false;
    for (uint32_t cube = 0; cube < 2; ++cube) {
        for (uint32_t quad = 0; quad < group.cube_attr[cube].quad_count;
             ++quad) {
            const auto attr = Group::GetQuadAttrIndex(cube, quad);
            SCOPED_TRACE(testing::Message()
                         << "cube " << cube << ", quad " << quad);
            EXPECT_EQ(actual.back_face[attr], expected.back_face[attr]);
            EXPECT_EQ(actual.normal[attr], expected.normal[attr]);
            if constexpr (kIris && kHasPbr && !kPosOnly) {
                EXPECT_EQ(actual.tangent[attr], expected.tangent[attr]);
            }
            if constexpr (Group::kTranslucent) {
                EXPECT_NEAR(actual.face_depth[attr], expected.face_depth[attr],
                            0.00001f);
            }
            saw_back_face |= actual.back_face[attr];
            saw_front_face |= !actual.back_face[attr];
        }
    }
    EXPECT_TRUE(saw_front_face);
    EXPECT_TRUE(saw_back_face);

    if constexpr (!(kIris && kHasPbr && !kPosOnly)) {
        for (const auto tangent : actual.tangent) {
            EXPECT_EQ(tangent, kTangentSentinel);
        }
    }
}
#endif

TEST(CubeTransformTest, GenericCubeTransform) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 4;
    for (uint32_t vertex = 0; vertex < 8; ++vertex) {
        group.pos[0][vertex] = static_cast<float>(vertex);
        group.pos[1][vertex] = static_cast<float>(vertex * 2);
        group.pos[2][vertex] = -static_cast<float>(vertex);
    }
    group.normal[2][0] = 1.0f;
    group.normal[2][1] = -1.0f;
    group.normal[0][2] = 1.0f;
    group.normal[2][3] = -1.0f;
    group.winding_sign[0] = 1.0f;
    group.winding_sign[1] = 1.0f;
    group.winding_sign[2] = 1.0f;
    group.winding_sign[3] = -1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    state.pose[0][0] = 2.0f;
    state.pose[1][1] = 3.0f;
    state.pose[2][2] = 4.0f;
    state.pose[3][0] = 5.0f;
    state.pose[3][1] = -2.0f;
    state.pose[3][2] = 1.0f;
    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.5f;
    state.normal[1][1] = 1.0f / 3.0f;
    state.normal[2][2] = 0.25f;
    state.uniform_scale = false;
    state.facing_coeff[2] = 1.0f;

    gfx::renderer::cube::CubeOutput<Group> output{};
    gfx::renderer::cube::Transform<false, false, false, false>(simd::GenericTag{},
                                                          group, state, output);

    EXPECT_FLOAT_EQ(output.pos[0][3], 11.0f);
    EXPECT_FLOAT_EQ(output.pos[1][3], 16.0f);
    EXPECT_FLOAT_EQ(output.pos[2][3], -11.0f);
    EXPECT_EQ(PackedByte(output.normal[0], 2), 0x7f);
    EXPECT_EQ(PackedByte(output.normal[1], 2), 0x7f);
    EXPECT_EQ(PackedByte(output.normal[2], 0), 0x81);
    EXPECT_FALSE(output.back_face[0]);
    EXPECT_TRUE(output.back_face[1]);
    EXPECT_TRUE(output.back_face[2]);
    EXPECT_FALSE(output.back_face[3]);
}

TEST(CubeTransformTest, GenericIrisTransform) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 4;
    group.normal[2][0] = 1.0f;
    group.normal[2][1] = -1.0f;
    group.normal[2][2] = 1.0f;
    group.normal[2][3] = -1.0f;
    group.tangent[0][0] = 1.0f;
    group.tangent[3][0] = -1.0f;
    group.tangent[1][1] = 1.0f;
    group.tangent[3][1] = -1.0f;
    group.tangent[0][2] = 1.0f;
    group.tangent[3][2] = 0.0f;
    group.tangent[0][3] = 1.0f;
    group.tangent[3][3] = 0.0f;
    for (uint32_t face = 0; face < 4; ++face) {
        group.winding_sign[face] = 1.0f;
    }

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    state.pose[0][0] = -2.0f;
    state.pose[1][1] = 3.0f;
    state.pose[2][2] = 4.0f;
    glm_mat3_identity(state.normal);
    state.normal[0][0] = -0.5f;
    state.normal[1][1] = 1.0f / 3.0f;
    state.normal[2][2] = 0.25f;
    state.facing_coeff[2] = 1.0f;
    state.tangent_orientation = -1.0f;
    state.uniform_scale = false;

    gfx::renderer::cube::CubeOutput<Group> output{};
    gfx::renderer::cube::Transform<false, true, true, false>(simd::GenericTag{},
                                                        group, state, output);

    EXPECT_EQ(PackedByte(output.normal[0], 2), 0x7f);
    EXPECT_EQ(PackedByte(output.normal[1], 2), 0x7f);
    EXPECT_EQ(PackedByte(output.tangent[0], 0), 0x81);
    EXPECT_EQ(PackedByte(output.tangent[0], 1), 0x00);
    EXPECT_EQ(PackedByte(output.tangent[0], 3), 0x7f);
    EXPECT_EQ(PackedByte(output.tangent[1], 1), 0x7f);
    EXPECT_EQ(PackedByte(output.tangent[1], 3), 0x81);
    EXPECT_EQ(PackedByte(output.tangent[2], 3), 0x7f);
    EXPECT_EQ(PackedByte(output.tangent[3], 3), 0x7f);
}

TEST(CubeTransformTest, NonPbrIrisStillNormalizesNormalButSkipsTangent) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 1;
    group.normal[0][0] = 1.0f;
    group.normal[1][0] = 1.0f;
    group.tangent[0][0] = 1.0f;
    group.tangent[3][0] = -1.0f;
    group.winding_sign[0] = 1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.5f;
    state.normal[1][1] = 0.25f;
    state.uniform_scale = false;
    state.facing_coeff[0] = 1.0f;

    gfx::renderer::cube::CubeOutput<Group> output{};
    output.tangent[0] = 0xdeadbeef;
    gfx::renderer::cube::Transform<false, true, false, false>(simd::GenericTag{},
                                                         group, state, output);

    EXPECT_EQ(PackedByte(output.normal[0], 0), 0x72);
    EXPECT_EQ(PackedByte(output.normal[0], 1), 0x39);
    EXPECT_EQ(output.tangent[0], 0xdeadbeef);
}

TEST(CubeTransformTest, PositionOnlyStillTransformsNormal) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 1;
    group.normal[0][0] = 1.0f;
    group.normal[1][0] = 1.0f;
    group.winding_sign[0] = 1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.5f;
    state.normal[1][1] = 0.25f;
    state.uniform_scale = false;
    state.facing_coeff[0] = 1.0f;

    gfx::renderer::cube::CubeOutput<Group> output{};
    gfx::renderer::cube::Transform<false, true, false, true>(simd::GenericTag{},
                                                        group, state, output);

    EXPECT_EQ(PackedByte(output.normal[0], 0), 0x72);
    EXPECT_EQ(PackedByte(output.normal[0], 1), 0x39);
}

TEST(CubeTransformTest, UsesPreNormalizedDirectionMatrixForIrisTangent) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 1;
    group.normal[0][0] = 1.0f;
    group.tangent[0][0] = 1.0f;
    group.tangent[3][0] = 1.0f;
    group.winding_sign[0] = 1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    state.tangent_orientation = 1.0f;
    state.pose[0][0] = 0.0f;
    state.pose[0][1] = 2.0f;
    state.pose[1][0] = -2.0f;
    state.pose[1][1] = 0.0f;
    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.0f;
    state.normal[0][1] = 1.0f;
    state.normal[1][0] = -1.0f;
    state.normal[1][1] = 0.0f;
    state.facing_coeff[0] = 1.0f;

    gfx::renderer::cube::CubeOutput<Group> output{};
    gfx::renderer::cube::Transform<false, true, true, false>(simd::GenericTag{},
                                                        group, state, output);

    EXPECT_EQ(PackedByte(output.normal[0], 1), 0x7f);
    EXPECT_EQ(PackedByte(output.tangent[0], 0), 0x00);
    EXPECT_EQ(PackedByte(output.tangent[0], 1), 0x7f);
    EXPECT_EQ(PackedByte(output.tangent[0], 3), 0x7f);
}

TEST(CubeTransformTest, NonUniformIrisTangentUsesPoseLinearPart) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 1;
    group.normal[2][0] = 1.0f;
    constexpr float kInverseSqrtTwo = 0.70710678118f;
    group.tangent[0][0] = kInverseSqrtTwo;
    group.tangent[1][0] = kInverseSqrtTwo;
    group.tangent[3][0] = 1.0f;
    group.winding_sign[0] = 1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    state.pose[0][0] = 2.0f;
    state.pose[1][1] = 3.0f;
    state.pose[2][2] = 4.0f;
    glm_mat3_identity(state.normal);
    state.normal[0][0] = 0.5f;
    state.normal[1][1] = 1.0f / 3.0f;
    state.normal[2][2] = 0.25f;
    state.uniform_scale = false;
    state.facing_coeff[2] = 1.0f;

    gfx::renderer::cube::CubeOutput<Group> output{};
    gfx::renderer::cube::Transform<false, true, true, false>(simd::GenericTag{},
                                                        group, state, output);

    EXPECT_EQ(PackedByte(output.tangent[0], 0), 0x46);
    EXPECT_EQ(PackedByte(output.tangent[0], 1), 0x6a);
    EXPECT_EQ(PackedByte(output.normal[0], 2), 0x7f);
}

TEST(CubeTransformTest, TranslucentDepth) {
    using Group = gfx::bake::CubeGroup<simd::Width::B256, true>;
    Group group;
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 1;
    group.normal[2][0] = 1.0f;
    group.winding_sign[0] = 1.0f;
    group.center[0][0] = 1.0f;
    group.center[1][0] = 2.0f;
    group.center[2][0] = 3.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    glm_mat3_identity(state.normal);
    state.facing_coeff[2] = 1.0f;
    state.depth_z[2] = 2.0f;
    state.depth_z[3] = 1.0f;
    state.depth_w[3] = 2.0f;

    gfx::renderer::cube::CubeOutput<Group> output{};
    gfx::renderer::cube::Transform<false, false, false, false>(simd::GenericTag{},
                                                          group, state, output);
    EXPECT_NEAR(output.face_depth[0], 3.5f, 0.0001f);

    using gfx::renderer::buffer::NdcDepthSortKey;
    EXPECT_LT(NdcDepthSortKey(1.0f), NdcDepthSortKey(0.0f));
    EXPECT_EQ(NdcDepthSortKey(0.0f), NdcDepthSortKey(-0.0f));
    EXPECT_LT(NdcDepthSortKey(-0.0f), NdcDepthSortKey(-1.0f));
    EXPECT_EQ(NdcDepthSortKey(std::numeric_limits<float>::infinity()),
              gfx::renderer::buffer::kInvalidDepthSortKey);
    EXPECT_EQ(NdcDepthSortKey(-std::numeric_limits<float>::infinity()),
              gfx::renderer::buffer::kInvalidDepthSortKey);
    EXPECT_EQ(NdcDepthSortKey(std::numeric_limits<float>::quiet_NaN()),
              gfx::renderer::buffer::kInvalidDepthSortKey);

    gfx::renderer::cube::CubeOutput<Group> pos_only_output{};
    gfx::renderer::cube::Transform<false, false, false, true>(
        simd::GenericTag{}, group, state, pos_only_output);
    EXPECT_NEAR(pos_only_output.face_depth[0], 3.5f, 0.0001f);
}

#ifdef YSM_X64
TEST(CubeTransformTest, Sse41MatchesNonIrisWithCullingEnabled) {
    const auto group = MakeB128CubeGroup<false>();
    const auto state = MakeSimdBoneState(false);
    ExpectSse41MatchesGeneric<true, false, false, false>(group, state);
}

TEST(CubeTransformTest, Sse41MatchesIrisPbrTransforms) {
    const auto group = MakeB128CubeGroup<false>();
    ExpectSse41MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
    ExpectSse41MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(true));
}

TEST(CubeTransformTest, Sse41MatchesIrisWithoutPbr) {
    const auto group = MakeB128CubeGroup<false>();
    ExpectSse41MatchesGeneric<false, true, false, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, Sse41MatchesTranslucentPositionOnly) {
    const auto group = MakeB128CubeGroup<true>();
    ExpectSse41MatchesGeneric<true, true, true, true>(
        group, MakeSimdBoneState(false));
    ExpectSse41MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, Sse41SingleQuadOnlyTransformsLiveData) {
    ExpectSingleQuadFastPath<
        gfx::bake::CubeGroup<simd::Width::B128, true>>(
        simd::Tag<simd::Type::SSE41>{});
}

TEST(CubeTransformTest, Sse41SingleCubeSkipsUnusedLanes) {
    ExpectSingleCubeFastPath(simd::Tag<simd::Type::SSE41>{});
}

TEST(CubeTransformTest, Sse41NormalizesInvalidDirectionsToZero) {
    using Group = gfx::bake::CubeGroup<simd::Width::B128, false>;
    Group group{};
    group.cube_count = 2;
    group.cube_attr[0].quad_count = 3;
    group.cube_attr[0].quad_count_after_culling = 3;
    group.cube_attr[1].quad_count = 1;
    group.cube_attr[1].quad_count_after_culling = 1;
    group.winding_sign.fill(1.0f);

    group.normal[0][1] = std::numeric_limits<float>::max();
    group.normal[0][2] = -std::numeric_limits<float>::max();
    group.normal[0][6] = std::numeric_limits<float>::max();
    group.tangent[0][1] = std::numeric_limits<float>::max();
    group.tangent[0][2] = -std::numeric_limits<float>::max();
    group.tangent[0][6] = std::numeric_limits<float>::max();
    group.plane_d.fill(1.0f);
    group.tangent[3][0] = 0.0f;
    group.tangent[3][1] = 1.0f;
    group.tangent[3][2] = -1.0f;
    group.tangent[3][6] = 1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    glm_mat3_identity(state.normal);
    state.facing_coeff[3] = 1.0f;
    state.tangent_orientation = -1.0f;
    state.uniform_scale = false;

    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::Tag<simd::Type::SSE41>{}, group, state, actual);

    for (const uint32_t attr : {0U, 1U, 2U, 6U}) {
        SCOPED_TRACE(testing::Message() << "attr " << attr);
        EXPECT_EQ(actual.back_face[attr], expected.back_face[attr]);
        EXPECT_EQ(actual.normal[attr], expected.normal[attr]);
        EXPECT_EQ(actual.tangent[attr], expected.tangent[attr]);
        EXPECT_EQ(actual.normal[attr], 0U);
        EXPECT_EQ(actual.tangent[attr] & 0x00ffffffU, 0U);
    }
}

TEST(CubeTransformTest, Avx2MatchesNonIrisWithCullingEnabled) {
    if (!SupportsAvx2CubeTransform()) {
        GTEST_SKIP() << "AVX2/FMA is unavailable";
    }

    const auto group = MakeAvx2CubeGroup<false>();
    const auto state = MakeSimdBoneState(false);
    ExpectAvx2MatchesGeneric<true, false, false, false>(group, state);
}

TEST(CubeTransformTest, Avx2MatchesIrisPbrTransforms) {
    if (!SupportsAvx2CubeTransform()) {
        GTEST_SKIP() << "AVX2/FMA is unavailable";
    }

    const auto group = MakeAvx2CubeGroup<false>();
    ExpectAvx2MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
    ExpectAvx2MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(true));
}

TEST(CubeTransformTest, Avx2MatchesIrisWithoutPbr) {
    if (!SupportsAvx2CubeTransform()) {
        GTEST_SKIP() << "AVX2/FMA is unavailable";
    }

    const auto group = MakeAvx2CubeGroup<false>();
    ExpectAvx2MatchesGeneric<false, true, false, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, Avx2MatchesTranslucentPositionOnly) {
    if (!SupportsAvx2CubeTransform()) {
        GTEST_SKIP() << "AVX2/FMA is unavailable";
    }

    const auto group = MakeAvx2CubeGroup<true>();
    ExpectAvx2MatchesGeneric<true, true, true, true>(
        group, MakeSimdBoneState(false));
    ExpectAvx2MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, Avx2SingleQuadOnlyTransformsLiveData) {
    if (!SupportsAvx2CubeTransform()) {
        GTEST_SKIP() << "AVX2/FMA is unavailable";
    }

    ExpectSingleQuadFastPath<
        gfx::bake::CubeGroup<simd::Width::B256, true>>(
        simd::Tag<simd::Type::AVX2>{});
}

TEST(CubeTransformTest, Avx2NormalizesInvalidDirectionsToZero) {
    if (!SupportsAvx2CubeTransform()) {
        GTEST_SKIP() << "AVX2/FMA is unavailable";
    }

    using Group = gfx::bake::CubeGroup<simd::Width::B256, false>;
    Group group{};
    group.cube_count = 1;
    group.cube_attr[0].quad_count = 3;
    group.cube_attr[0].quad_count_after_culling = 3;
    group.winding_sign.fill(1.0f);

    group.normal[0][1] = std::numeric_limits<float>::max();
    group.normal[0][2] = -std::numeric_limits<float>::max();
    group.tangent[0][1] = std::numeric_limits<float>::max();
    group.tangent[0][2] = -std::numeric_limits<float>::max();
    group.plane_d.fill(1.0f);
    group.tangent[3][0] = 0.0f;
    group.tangent[3][1] = 1.0f;
    group.tangent[3][2] = -1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    glm_mat3_identity(state.normal);
    state.facing_coeff[3] = 1.0f;
    state.tangent_orientation = -1.0f;
    state.uniform_scale = false;

    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::Tag<simd::Type::AVX2>{}, group, state, actual);

    for (uint32_t quad = 0; quad < 3; ++quad) {
        SCOPED_TRACE(testing::Message() << "quad " << quad);
        EXPECT_EQ(actual.back_face[quad], expected.back_face[quad]);
        EXPECT_EQ(actual.normal[quad], expected.normal[quad]);
        EXPECT_EQ(actual.tangent[quad], expected.tangent[quad]);
        EXPECT_EQ(actual.normal[quad], 0U);
        EXPECT_EQ(actual.tangent[quad] & 0x00ffffffU, 0U);
    }
}

TEST(CubeTransformTest, Avx512MatchesNonIrisWithCullingEnabled) {
    if (!SupportsAvx512CubeTransform()) {
        GTEST_SKIP() << "AVX-512F/BW/VL is unavailable";
    }

    const auto group = MakeAvx512CubeGroup<false>();
    const auto state = MakeSimdBoneState(false);
    ExpectAvx512MatchesGeneric<true, false, false, false>(group, state);
}

TEST(CubeTransformTest, Avx512MatchesIrisPbrTransforms) {
    if (!SupportsAvx512CubeTransform()) {
        GTEST_SKIP() << "AVX-512F/BW/VL is unavailable";
    }

    const auto group = MakeAvx512CubeGroup<false>();
    ExpectAvx512MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
    ExpectAvx512MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(true));
}

TEST(CubeTransformTest, Avx512MatchesIrisWithoutPbr) {
    if (!SupportsAvx512CubeTransform()) {
        GTEST_SKIP() << "AVX-512F/BW/VL is unavailable";
    }

    const auto group = MakeAvx512CubeGroup<false>();
    ExpectAvx512MatchesGeneric<false, true, false, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, Avx512MatchesTranslucentPositionOnly) {
    if (!SupportsAvx512CubeTransform()) {
        GTEST_SKIP() << "AVX-512F/BW/VL is unavailable";
    }

    const auto group = MakeAvx512CubeGroup<true>();
    ExpectAvx512MatchesGeneric<true, true, true, true>(
        group, MakeSimdBoneState(false));
    ExpectAvx512MatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, Avx512SingleQuadOnlyTransformsLiveData) {
    if (!SupportsAvx512CubeTransform()) {
        GTEST_SKIP() << "AVX-512F/BW/VL is unavailable";
    }

    ExpectSingleQuadFastPath<
        gfx::bake::CubeGroup<simd::Width::B512, true>>(
        simd::Tag<simd::Type::AVX512>{});
}
#endif

#ifdef YSM_ARM64
TEST(CubeTransformTest, NeonMatchesNonIrisWithCullingEnabled) {
    const auto group = MakeB128CubeGroup<false>();
    const auto state = MakeSimdBoneState(false);
    ExpectNeonMatchesGeneric<true, false, false, false>(group, state);
}

TEST(CubeTransformTest, NeonMatchesIrisPbrTransforms) {
    const auto group = MakeB128CubeGroup<false>();
    ExpectNeonMatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
    ExpectNeonMatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(true));
}

TEST(CubeTransformTest, NeonMatchesIrisWithoutPbr) {
    const auto group = MakeB128CubeGroup<false>();
    ExpectNeonMatchesGeneric<false, true, false, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, NeonMatchesTranslucentPositionOnly) {
    const auto group = MakeB128CubeGroup<true>();
    ExpectNeonMatchesGeneric<true, true, true, true>(
        group, MakeSimdBoneState(false));
    ExpectNeonMatchesGeneric<false, true, true, false>(
        group, MakeSimdBoneState(false));
}

TEST(CubeTransformTest, NeonSingleQuadOnlyTransformsLiveData) {
    ExpectSingleQuadFastPath<
        gfx::bake::CubeGroup<simd::Width::B128, true>>(
        simd::Tag<simd::Type::NEON>{});
}

TEST(CubeTransformTest, NeonSingleCubeSkipsUnusedLanes) {
    ExpectSingleCubeFastPath(simd::Tag<simd::Type::NEON>{});
}

TEST(CubeTransformTest, NeonNormalizesInvalidDirectionsToZero) {
    using Group = gfx::bake::CubeGroup<simd::Width::B128, false>;
    Group group{};
    group.cube_count = 2;
    group.cube_attr[0].quad_count = 3;
    group.cube_attr[0].quad_count_after_culling = 3;
    group.cube_attr[1].quad_count = 1;
    group.cube_attr[1].quad_count_after_culling = 1;
    group.winding_sign.fill(1.0f);

    group.normal[0][1] = std::numeric_limits<float>::max();
    group.normal[0][2] = -std::numeric_limits<float>::max();
    group.normal[0][6] = std::numeric_limits<float>::max();
    group.tangent[0][1] = std::numeric_limits<float>::max();
    group.tangent[0][2] = -std::numeric_limits<float>::max();
    group.tangent[0][6] = std::numeric_limits<float>::max();
    group.plane_d.fill(1.0f);
    group.tangent[3][0] = 0.0f;
    group.tangent[3][1] = 1.0f;
    group.tangent[3][2] = -1.0f;
    group.tangent[3][6] = 1.0f;

    gfx::renderer::RenderBoneState state{};
    glm_mat4_identity(state.pose);
    glm_mat3_identity(state.normal);
    state.facing_coeff[3] = 1.0f;
    state.tangent_orientation = -1.0f;
    state.uniform_scale = false;

    gfx::renderer::cube::CubeOutput<Group> expected{};
    gfx::renderer::cube::CubeOutput<Group> actual{};
    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::GenericTag{}, group, state, expected);
    gfx::renderer::cube::Transform<false, true, true, false>(
        simd::Tag<simd::Type::NEON>{}, group, state, actual);

    for (const uint32_t attr : {0U, 1U, 2U, 6U}) {
        SCOPED_TRACE(testing::Message() << "attr " << attr);
        EXPECT_EQ(actual.back_face[attr], expected.back_face[attr]);
        EXPECT_EQ(actual.normal[attr], expected.normal[attr]);
        EXPECT_EQ(actual.tangent[attr], expected.tangent[attr]);
        EXPECT_EQ(actual.normal[attr], 0U);
        EXPECT_EQ(actual.tangent[attr] & 0x00ffffffU, 0U);
    }
}
#endif

}  // namespace
}  // namespace ysm::test

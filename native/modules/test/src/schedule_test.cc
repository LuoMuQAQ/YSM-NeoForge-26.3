#include <algorithm>
#include <array>
#include <cstdint>
#include <initializer_list>
#include <memory>
#include <span>
#include <utility>
#include <vector>

#include <gtest/gtest.h>

#include "gfx/bake/baked_model.h"
#include "gfx/bake/baked_serializer.h"
#include "cpu.h"
#include "gfx/renderer/schedule.h"

namespace ysm::test {
namespace {

template <typename Group>
Group MakeGroup(
    uint32_t bone_index,
    std::initializer_list<std::pair<uint8_t, uint8_t>> quad_counts) {
    EXPECT_LE(quad_counts.size(), Group::kCubeGroupCapacity);
    Group group;
    group.bone_index = bone_index;
    group.cube_count = static_cast<uint32_t>(quad_counts.size());
    size_t cube_index = 0;
    for (const auto [full, culling] : quad_counts) {
        group.cube_attr[cube_index].quad_count = full;
        group.cube_attr[cube_index].quad_count_after_culling = culling;
        ++cube_index;
    }
    return group;
}

template <typename Group>
void SetBoneInfo(gfx::bake::BakedModelBones::BonePartitionInfo& bone_info,
                 std::span<const uint32_t> indices,
                 std::span<gfx::bake::BakedModelBones::CubeGroupInfo> group_info,
                 const std::vector<Group>& groups) {
    bone_info.cube_indices = indices;
    bone_info.cube_group_info = group_info;
    uint32_t cube_count = 0;
    uint32_t quad_count = 0;
    uint32_t quad_count_after_culling = 0;
    for (size_t i = 0; i < indices.size(); ++i) {
        const auto& group = groups[indices[i]];
        cube_count += group.cube_count;
        for (size_t cube_index = 0; cube_index < group.cube_count;
             ++cube_index) {
            const auto& cube = group.cube_attr[cube_index];
            quad_count += cube.quad_count;
            quad_count_after_culling += cube.quad_count_after_culling;
        }
        group_info[i] = {cube_count, quad_count, quad_count_after_culling};
    }
    bone_info.cube_count = cube_count;
    bone_info.full_vertex_count = quad_count * 4U;
    bone_info.culling_vertex_count = quad_count_after_culling * 4U;
}

std::shared_ptr<gfx::bake::BakedModel> MakeScheduleModel(
    bool corrupt_group_info = false) {
    using CutoutGroup = gfx::bake::CubeGroup<simd::Width::B128, false>;
    using TranslucentGroup = gfx::bake::CubeGroup<simd::Width::B128, true>;

    gfx::bake::BakedModelCubes<simd::Width::B128> cubes;
    cubes.cutout = {
        MakeGroup<CutoutGroup>(0, {{6, 3}}),
        MakeGroup<CutoutGroup>(0, {{2, 1}, {1, 1}}),
        MakeGroup<CutoutGroup>(1, {{1, 1}}),
    };
    cubes.cutout_no_culling = {
        MakeGroup<CutoutGroup>(0, {{4, 2}}),
        MakeGroup<CutoutGroup>(1, {{2, 1}}),
    };
    cubes.translucent = {
        MakeGroup<TranslucentGroup>(1, {{3, 2}}),
        MakeGroup<TranslucentGroup>(2, {{2, 1}}),
    };
    cubes.translucent_culling = {
        MakeGroup<TranslucentGroup>(0, {{6, 3}}),
        MakeGroup<TranslucentGroup>(2, {{4, 2}}),
    };

    gfx::bake::BakedModelBones bones;
    bones.sorted_bone_indices = {0, 1, 2};
    bones.list.resize(3);
    for (size_t i = 0; i < bones.list.size(); ++i) {
        auto& bone = bones.list[i];
        bone.solid = true;
        bone.subtree_end = static_cast<uint32_t>(i + 1);
    }
    bones.cube_indices_cache = {0, 1, 2, 0, 0, 1, 0, 1};
    bones.cube_group_info_cache.resize(bones.cube_indices_cache.size());
    const auto cache = std::span<const uint32_t>(bones.cube_indices_cache);
    auto group_info_cache = std::span(bones.cube_group_info_cache);
    size_t offset = 0;

    SetBoneInfo(bones.list[0].cutout, cache.subspan(offset, 2),
                group_info_cache.subspan(offset, 2), cubes.cutout);
    offset += 2;
    SetBoneInfo(bones.list[1].cutout, cache.subspan(offset, 1),
                group_info_cache.subspan(offset, 1), cubes.cutout);
    offset += 1;
    SetBoneInfo(bones.list[0].cutout_no_culling, cache.subspan(offset, 1),
                group_info_cache.subspan(offset, 1), cubes.cutout_no_culling);
    offset += 1;
    SetBoneInfo(bones.list[1].translucent, cache.subspan(offset, 1),
                group_info_cache.subspan(offset, 1), cubes.translucent);
    offset += 1;
    SetBoneInfo(bones.list[2].translucent, cache.subspan(offset, 1),
                group_info_cache.subspan(offset, 1), cubes.translucent);
    offset += 1;
    SetBoneInfo(bones.list[0].translucent_culling, cache.subspan(offset, 1),
                group_info_cache.subspan(offset, 1), cubes.translucent_culling);
    offset += 1;
    SetBoneInfo(bones.list[2].translucent_culling, cache.subspan(offset, 1),
                group_info_cache.subspan(offset, 1), cubes.translucent_culling);
    offset += 1;
    EXPECT_EQ(offset, cache.size());
    if (corrupt_group_info) {
        ++bones.cube_group_info_cache[0].quad_count;
    }

    return std::make_shared<gfx::bake::BakedModel>(
        gfx::bake::BakedModelInfo{.has_pbr = true}, std::move(bones),
                                               std::move(cubes));
}

std::shared_ptr<gfx::bake::BakedModel> MakeDenseScheduleModel() {
    using CutoutGroup = gfx::bake::CubeGroup<simd::Width::B128, false>;
    constexpr size_t kGroupCount = 15;

    gfx::bake::BakedModelCubes<simd::Width::B128> cubes;
    cubes.cutout.reserve(kGroupCount);
    for (size_t i = 0; i < kGroupCount; ++i) {
        const auto full = static_cast<uint8_t>(i % 6 + 1);
        const auto culling = static_cast<uint8_t>(i % 3 + 1);
        cubes.cutout.push_back(MakeGroup<CutoutGroup>(0, {{full, culling}}));
    }

    gfx::bake::BakedModelBones bones;
    bones.sorted_bone_indices = {0};
    bones.list.resize(1);
    bones.list[0].solid = true;
    bones.list[0].subtree_end = 1;
    bones.cube_indices_cache.resize(kGroupCount);
    for (size_t i = 0; i < kGroupCount; ++i) {
        bones.cube_indices_cache[i] = static_cast<uint32_t>(i);
    }
    bones.cube_group_info_cache.resize(kGroupCount);
    SetBoneInfo(bones.list[0].cutout, bones.cube_indices_cache,
                bones.cube_group_info_cache, cubes.cutout);

    return std::make_shared<gfx::bake::BakedModel>(gfx::bake::BakedModelInfo{},
                                              std::move(bones),
                                              std::move(cubes));
}

template <typename PartitionSelector>
std::vector<uint32_t> GatherIndices(const gfx::renderer::RenderSchedule& schedule,
                                    PartitionSelector selector) {
    std::vector<uint32_t> result;
    for (const auto& task : schedule.tasks) {
        const auto& indices = selector(task).cube_indices;
        result.insert(result.end(), indices.begin(), indices.end());
    }
    std::ranges::sort(result);
    return result;
}

}  // namespace

TEST(ScheduleTest, BuildSchedule) {
    auto model = MakeScheduleModel();
    gfx::renderer::RenderSchedule schedule;
    constexpr size_t kWorkerCount = 4;
    const std::vector<uint16_t> selected_bones{2, 0};
    const auto status =
        schedule.Update(model->Bones(), selected_bones, kWorkerCount);
    ASSERT_TRUE(status.ok()) << status;
    EXPECT_EQ(schedule.vertex_count, 64);
    EXPECT_EQ(schedule.translucent_vertex_count, 28);
    EXPECT_EQ(schedule.translucent_vertex_offset, 36);
    EXPECT_EQ(schedule.mode, gfx::renderer::RenderSchedulingMode::kInline);
    EXPECT_EQ(schedule.tasks.size(), kWorkerCount);

    EXPECT_EQ(GatherIndices(
                  schedule,
                  [](const auto& task) -> const auto& { return task.cutout; }),
              std::vector<uint32_t>({0, 1}));
    EXPECT_EQ(GatherIndices(schedule,
                            [](const auto& task) -> const auto& {
                                return task.cutout_no_culling;
                            }),
              std::vector<uint32_t>({0}));
    EXPECT_EQ(GatherIndices(schedule,
                            [](const auto& task) -> const auto& {
                                return task.translucent;
                            }),
              std::vector<uint32_t>({1}));
    EXPECT_EQ(GatherIndices(schedule,
                            [](const auto& task) -> const auto& {
                                return task.translucent_culling;
                            }),
              std::vector<uint32_t>({0, 1}));
    EXPECT_EQ(schedule.tasks[0].cutout.cube_indices.size(), 2);
    EXPECT_EQ(schedule.tasks[0].cutout_no_culling.cube_indices.size(), 1);
    EXPECT_EQ(schedule.tasks[0].translucent.cube_indices.size(), 1);
    EXPECT_EQ(schedule.tasks[0].translucent_culling.cube_indices.size(), 2);
    for (size_t worker = 1; worker < schedule.tasks.size(); ++worker) {
        EXPECT_TRUE(schedule.tasks[worker].cutout.cube_indices.empty());
        EXPECT_TRUE(
            schedule.tasks[worker].cutout_no_culling.cube_indices.empty());
        EXPECT_TRUE(schedule.tasks[worker].translucent.cube_indices.empty());
        EXPECT_TRUE(
            schedule.tasks[worker].translucent_culling.cube_indices.empty());
    }

    uint32_t opaque_offset = 0;
    const auto& active_task = schedule.tasks[0];
    EXPECT_EQ(active_task.cutout.vertex_offset, opaque_offset);
    opaque_offset += active_task.cutout.expected_vertex_count;
    EXPECT_EQ(active_task.cutout_no_culling.vertex_offset, opaque_offset);
    opaque_offset += active_task.cutout_no_culling.expected_vertex_count;
    EXPECT_EQ(opaque_offset, schedule.translucent_vertex_offset);

    uint32_t translucent_offset = 0;
    EXPECT_EQ(active_task.translucent.vertex_offset, translucent_offset);
    translucent_offset += active_task.translucent.expected_vertex_count;
    EXPECT_EQ(active_task.translucent_culling.vertex_offset,
              translucent_offset);
    translucent_offset +=
        active_task.translucent_culling.expected_vertex_count;
    EXPECT_EQ(translucent_offset, schedule.translucent_vertex_count);

    ASSERT_TRUE(schedule.Update(model->Bones(), {}, kWorkerCount).ok());
    EXPECT_EQ(schedule.vertex_count, 0);
    EXPECT_EQ(schedule.translucent_vertex_count, 0);
    EXPECT_EQ(schedule.translucent_vertex_offset, 0);
    for (const auto& task : schedule.tasks) {
        EXPECT_TRUE(task.cutout.cube_indices.empty());
        EXPECT_TRUE(task.cutout_no_culling.cube_indices.empty());
        EXPECT_TRUE(task.translucent.cube_indices.empty());
        EXPECT_TRUE(task.translucent_culling.cube_indices.empty());
    }
    const std::array<uint16_t, 1> out_of_range{3};
    EXPECT_EQ(
        schedule.Update(model->Bones(), out_of_range, kWorkerCount).code(),
        absl::StatusCode::kInvalidArgument);
    const auto corrupt_cache =
        gfx::bake::SerializeBakedModel(*MakeScheduleModel(true));
    EXPECT_FALSE(gfx::bake::ReadBakedModel(corrupt_cache).ok());

    auto dense_model = MakeDenseScheduleModel();
    gfx::renderer::RenderSchedule dense_schedule;
    const std::array<uint16_t, 1> dense_bones{0};
    ASSERT_TRUE(
        dense_schedule.Update(dense_model->Bones(), dense_bones, kWorkerCount)
            .ok());
    EXPECT_EQ(dense_schedule.mode,
              gfx::renderer::RenderSchedulingMode::kSerialPrewake);
    EXPECT_EQ(dense_schedule.tasks.size(), kWorkerCount);
    const auto& prefix = dense_model->Bones().list[0].cutout.cube_group_info;
    uint32_t vertex_offset = 0;
    for (size_t worker = 0; worker < dense_schedule.tasks.size(); ++worker) {
        constexpr size_t kGroupCount = 15;
        const auto begin = kGroupCount * worker / dense_schedule.tasks.size();
        const auto end =
            kGroupCount * (worker + 1) / dense_schedule.tasks.size();
        const auto& partition = dense_schedule.tasks[worker].cutout;
        EXPECT_EQ(partition.cube_indices.size(), end - begin);
        for (size_t i = begin; i < end; ++i) {
            EXPECT_EQ(partition.cube_indices[i - begin], i);
        }
        const auto begin_quads =
            begin == 0 ? 0U : prefix[begin - 1].quad_count_after_culling;
        const auto end_quads =
            end == 0 ? 0U : prefix[end - 1].quad_count_after_culling;
        EXPECT_EQ(partition.expected_vertex_count,
                  (end_quads - begin_quads) * 4U);
        EXPECT_EQ(partition.vertex_offset, vertex_offset);
        vertex_offset += partition.expected_vertex_count;
    }
    EXPECT_EQ(vertex_offset, 120);
    EXPECT_EQ(dense_schedule.vertex_count, 120);
    EXPECT_EQ(dense_schedule.translucent_vertex_count, 0);
    EXPECT_EQ(dense_schedule.translucent_vertex_offset, 120);
}

TEST(ScheduleTest, BuildsAllPartitionsAcrossWorkers) {
    auto model = MakeScheduleModel();
    gfx::renderer::RenderSchedule schedule;
    const std::array<uint16_t, 3> selected_bones{0, 1, 2};

    ASSERT_TRUE(schedule.Update(model->Bones(), selected_bones, 2).ok());
    ASSERT_EQ(schedule.mode, gfx::renderer::RenderSchedulingMode::kSerialLateWake);
    ASSERT_EQ(schedule.tasks.size(), 2);
    EXPECT_EQ(schedule.vertex_count, 80);
    EXPECT_EQ(schedule.translucent_vertex_count, 40);
    EXPECT_EQ(schedule.translucent_vertex_offset, 40);

    const auto& first = schedule.tasks[0];
    EXPECT_EQ(first.cutout.cube_indices, std::vector<uint32_t>({0}));
    EXPECT_EQ(first.cutout.vertex_offset, 0);
    EXPECT_EQ(first.cutout.expected_vertex_count, 12);
    EXPECT_TRUE(first.cutout_no_culling.cube_indices.empty());
    EXPECT_EQ(first.cutout_no_culling.vertex_offset, 24);
    EXPECT_EQ(first.cutout_no_culling.expected_vertex_count, 0);
    EXPECT_EQ(first.translucent.cube_indices, std::vector<uint32_t>({0}));
    EXPECT_EQ(first.translucent.vertex_offset, 0);
    EXPECT_EQ(first.translucent.expected_vertex_count, 12);
    EXPECT_EQ(first.translucent_culling.cube_indices,
              std::vector<uint32_t>({0}));
    EXPECT_EQ(first.translucent_culling.vertex_offset, 20);
    EXPECT_EQ(first.translucent_culling.expected_vertex_count, 12);

    const auto& second = schedule.tasks[1];
    EXPECT_EQ(second.cutout.cube_indices, std::vector<uint32_t>({1, 2}));
    EXPECT_EQ(second.cutout.vertex_offset, 12);
    EXPECT_EQ(second.cutout.expected_vertex_count, 12);
    EXPECT_EQ(second.cutout_no_culling.cube_indices,
              std::vector<uint32_t>({0}));
    EXPECT_EQ(second.cutout_no_culling.vertex_offset, 24);
    EXPECT_EQ(second.cutout_no_culling.expected_vertex_count, 16);
    EXPECT_EQ(second.translucent.cube_indices, std::vector<uint32_t>({1}));
    EXPECT_EQ(second.translucent.vertex_offset, 12);
    EXPECT_EQ(second.translucent.expected_vertex_count, 8);
    EXPECT_EQ(second.translucent_culling.cube_indices,
              std::vector<uint32_t>({1}));
    EXPECT_EQ(second.translucent_culling.vertex_offset, 32);
    EXPECT_EQ(second.translucent_culling.expected_vertex_count, 8);

    auto dense_model = MakeDenseScheduleModel();
    const std::array<uint16_t, 1> dense_bones{0};
    ASSERT_TRUE(schedule.Update(dense_model->Bones(), dense_bones, 2).ok());
    for (const auto& task : schedule.tasks) {
        EXPECT_TRUE(task.cutout_no_culling.cube_indices.empty());
        EXPECT_EQ(task.cutout_no_culling.expected_vertex_count, 0);
        EXPECT_TRUE(task.translucent.cube_indices.empty());
        EXPECT_EQ(task.translucent.expected_vertex_count, 0);
        EXPECT_TRUE(task.translucent_culling.cube_indices.empty());
        EXPECT_EQ(task.translucent_culling.expected_vertex_count, 0);
    }
}

TEST(ScheduleTest, SelectsExecutionModeAtMeasuredBoundaries) {
    using Mode = gfx::renderer::RenderSchedulingMode;

    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(1, 1000, 1000),
              Mode::kInline);

    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(2, 4, 6),
              Mode::kInline);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(2, 4, 7),
              Mode::kSerialLateWake);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(2, 64, 64),
              Mode::kWorkerReadySpin);

    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(3, 6, 6),
              Mode::kInline);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(3, 6, 7),
              Mode::kSerialLateWake);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(3, 96, 96),
              Mode::kWorkerReadySpin);

    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(4, 6, 6),
              Mode::kInline);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(4, 7, 6),
              Mode::kSerialPrewake);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(6, 6, 7),
              Mode::kSerialPrewake);

    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(7, 6, 8),
              Mode::kInline);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(7, 7, 8),
              Mode::kSerialPrewake);
    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(7, 224, 16),
              Mode::kWorkerReadySpin);

    EXPECT_EQ(gfx::renderer::DetermineRenderSchedulingMode(65, 2080, 2080),
              Mode::kSerialPrewake);
}

TEST(ScheduleTest, MapsRenderBonesToUpdateWorkers) {
    constexpr size_t kWorkerCount = 7;
    constexpr size_t kRenderBoneCount = kWorkerCount * 32;
    gfx::bake::BakedModelBones bones;
    bones.list.resize(kRenderBoneCount + 6);
    std::vector<uint16_t> render_bone_indices(kRenderBoneCount);
    for (size_t index = 0; index < render_bone_indices.size(); ++index) {
        render_bone_indices[index] = static_cast<uint16_t>(index + 3);
    }

    gfx::renderer::RenderSchedule schedule;
    ASSERT_TRUE(
        schedule.Update(bones, render_bone_indices, kWorkerCount).ok());
    ASSERT_EQ(schedule.mode,
              gfx::renderer::RenderSchedulingMode::kWorkerReadySpin);
    ASSERT_EQ(schedule.bone_update_owners.size(), bones.list.size());
    EXPECT_EQ(schedule.bone_update_owners[0], UINT8_MAX);
    EXPECT_EQ(schedule.bone_update_owners[1], UINT8_MAX);
    EXPECT_EQ(schedule.bone_update_owners[2], UINT8_MAX);
    for (size_t worker_index = 0; worker_index < kWorkerCount;
         ++worker_index) {
        const auto [begin, end] = gfx::renderer::DetermineTaskRange(
            worker_index, kWorkerCount, render_bone_indices.size());
        for (size_t index = begin; index < end; ++index) {
            EXPECT_EQ(schedule.bone_update_owners[render_bone_indices[index]],
                      worker_index);
        }
    }
    EXPECT_EQ(schedule.bone_update_owners[kRenderBoneCount + 3], UINT8_MAX);
    EXPECT_EQ(schedule.bone_update_owners[kRenderBoneCount + 4], UINT8_MAX);
    EXPECT_EQ(schedule.bone_update_owners[kRenderBoneCount + 5], UINT8_MAX);

    ASSERT_TRUE(schedule
                    .Update(bones, std::span(render_bone_indices).first(1),
                            kWorkerCount)
                    .ok());
    EXPECT_EQ(schedule.mode, gfx::renderer::RenderSchedulingMode::kInline);
    EXPECT_TRUE(schedule.bone_update_owners.empty());
}

}  // namespace ysm::test

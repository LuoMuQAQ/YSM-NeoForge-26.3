#include <array>
#include <cstdint>
#include <limits>
#include <span>
#include <type_traits>

#include <absl/status/statusor.h>
#include <java/array.h>
#include <java/entry.h>
#include <java/opaque_ptr.h>
#include <gfx/renderer/model_state.h>

#include "err.h"

namespace ysm::lib::render {
namespace {
constexpr size_t kPartitionCount = 4;
constexpr size_t kModelStateInfoSize = 5;

struct PackedVertexRange {
    uint32_t vertex_offset;
    uint32_t expected_vertex_count;
};
static_assert(sizeof(PackedVertexRange) == sizeof(jint) * 2);
static_assert(std::is_standard_layout_v<PackedVertexRange>);

struct PackedCubeInfoHeader {
    std::array<uint16_t, kPartitionCount> partition_cube_counts;
};
static_assert(sizeof(PackedCubeInfoHeader) == sizeof(jint) * 2);
static_assert(std::is_standard_layout_v<PackedCubeInfoHeader>);

struct PackedBoneInfo {
    PackedVertexRange cutout;
    PackedVertexRange cutout_no_culling;
    PackedVertexRange translucent;
    PackedVertexRange translucent_culling;
};
static_assert(sizeof(PackedBoneInfo) == sizeof(jint) * 8);
static_assert(std::is_standard_layout_v<PackedBoneInfo>);

struct PartitionLayout {
    std::array<uint64_t, kPartitionCount> vertex_counts;
    std::array<uint64_t, kPartitionCount> vertex_offsets;
};

std::array<uint32_t, kPartitionCount> GetBoneVertexCounts(
    const gfx::bake::BakedModelBones::BoneInfo& bone) {
    return {
        bone.cutout.culling_vertex_count,
        bone.cutout_no_culling.full_vertex_count,
        bone.translucent.full_vertex_count,
        bone.translucent_culling.culling_vertex_count,
    };
}

std::array<uint32_t, kPartitionCount> GetBoneCubeCounts(
    const gfx::bake::BakedModelBones::BoneInfo& bone) {
    return {
        bone.cutout.cube_count,
        bone.cutout_no_culling.cube_count,
        bone.translucent.cube_count,
        bone.translucent_culling.cube_count,
    };
}

absl::StatusOr<uint64_t> GetRenderCubeCount(
    const gfx::renderer::ModelState& state,
    std::span<const uint16_t> render_bone_indices) {
    const auto& bones = state.Model()->Bones();
    uint64_t result = 0;
    for (const auto bone_index : render_bone_indices) {
        YSM_ASSERT(bone_index < bones.list.size(),
                   absl::InternalError(
                       "Render bone index is inconsistent with BakedModel."));
        for (const auto count : GetBoneCubeCounts(bones.list[bone_index])) {
            YSM_ASSERT(count <= std::numeric_limits<uint16_t>::max(),
                       absl::ResourceExhaustedError(
                           "Bone partition cube count exceeds uint16_t."));
            result += count;
        }
    }
    return result;
}

absl::StatusOr<PartitionLayout> GetPartitionLayout(
    const gfx::renderer::ModelState& state,
    std::span<const uint16_t> render_bone_indices) {
    const auto& bones = state.Model()->Bones();
    PartitionLayout result{};
    for (const auto bone_index : render_bone_indices) {
        YSM_ASSERT(bone_index < bones.list.size(),
                   absl::InternalError(
                       "Render bone index is inconsistent with BakedModel."));
        const auto counts = GetBoneVertexCounts(bones.list[bone_index]);
        for (size_t partition = 0; partition < counts.size(); ++partition) {
            result.vertex_counts[partition] += counts[partition];
        }
    }

    const auto& schedule = state.Schedule();
    YSM_ASSERT(
        result.vertex_counts[0] + result.vertex_counts[1] ==
                schedule.translucent_vertex_offset &&
            result.vertex_counts[2] + result.vertex_counts[3] ==
                schedule.translucent_vertex_count &&
            static_cast<uint64_t>(schedule.translucent_vertex_offset) +
                    schedule.translucent_vertex_count ==
                schedule.vertex_count,
        absl::InternalError(
            "ModelState schedule is inconsistent with BakedModel."));

    result.vertex_offsets = {
        0,
        result.vertex_counts[0],
        schedule.translucent_vertex_offset,
        static_cast<uint64_t>(schedule.translucent_vertex_offset) +
            result.vertex_counts[2],
    };
    return result;
}
}  // namespace

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/render/NativeModelState;nCalculateRenderBoneInfo(J[I)Z",
    (state_ptr, dst_array_obj)) {
    YSM_DECLARE_OR_RETURN(
        state, java::CastOpaquePtr<gfx::renderer::ModelState>(state_ptr));
    YSM_ASSERT(state->IsValid(),
               absl::FailedPreconditionError("ModelState is invalid."));

    const auto render_bone_indices = state->PoseView().render_bone_indices;
    constexpr size_t kIntsPerBone = sizeof(PackedBoneInfo) / sizeof(jint);
    YSM_ASSERT(static_cast<uint64_t>(render_bone_indices.size()) *
                       kIntsPerBone <=
                   static_cast<uint64_t>(std::numeric_limits<jint>::max()),
               absl::InvalidArgumentError("Render bone count out of range."));

    YSM_DECLARE_OR_RETURN(layout,
                          GetPartitionLayout(*state, render_bone_indices));
    YSM_DECLARE_OR_RETURN(
        dst_array,
        java::CriticalIntArray<false>::Get(
            env, dst_array_obj, 0,
            static_cast<jint>(kIntsPerBone * render_bone_indices.size())));
    std::span dst{reinterpret_cast<PackedBoneInfo*>(dst_array.data()),
                  render_bone_indices.size()};

    const auto& bones = state->Model()->Bones();
    auto vertex_offsets = layout.vertex_offsets;
    for (size_t index = 0; index < render_bone_indices.size(); ++index) {
        const auto counts =
            GetBoneVertexCounts(bones.list[render_bone_indices[index]]);
        auto& packed = dst[index];
        std::array<PackedVertexRange*, kPartitionCount> partitions{
            &packed.cutout,
            &packed.cutout_no_culling,
            &packed.translucent,
            &packed.translucent_culling,
        };
        for (size_t partition = 0; partition < partitions.size();
             ++partition) {
            partitions[partition]->vertex_offset =
                static_cast<uint32_t>(vertex_offsets[partition]);
            partitions[partition]->expected_vertex_count = counts[partition];
             }
        for (size_t partition = 0; partition < counts.size(); ++partition) {
            vertex_offsets[partition] += counts[partition];
        }
    }
    return OkStatus();
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/render/NativeModelState;nCalculateRenderCubeInfo(J[I)[I",
    (state_ptr, dst_array_obj)) {
    YSM_DECLARE_OR_RETURN(
        state, java::CastOpaquePtr<gfx::renderer::ModelState>(state_ptr));
    YSM_ASSERT(state->IsValid(),
               absl::FailedPreconditionError("ModelState is invalid."));

    const auto render_bone_indices = state->PoseView().render_bone_indices;
    constexpr size_t kHeaderIntsPerBone =
        sizeof(PackedCubeInfoHeader) / sizeof(jint);
    constexpr size_t kIntsPerCube = sizeof(PackedVertexRange) / sizeof(jint);
    YSM_DECLARE_OR_RETURN(render_cube_count,
                          GetRenderCubeCount(*state, render_bone_indices));
    const auto header_int_count =
        static_cast<uint64_t>(render_bone_indices.size()) *
        kHeaderIntsPerBone + 1;
    const auto cube_int_count = render_cube_count * kIntsPerCube;
    YSM_ASSERT(header_int_count + cube_int_count <=
                   static_cast<uint64_t>(std::numeric_limits<jint>::max()),
               absl::ResourceExhaustedError(
                   "Render cube info exceeds Java array capacity."));

    YSM_DECLARE_OR_RETURN(layout,
                          GetPartitionLayout(*state, render_bone_indices));

    YSM_DECLARE_OR_RETURN(
        dst_array,
        java::CriticalIntArray<false>::Get(
            env, dst_array_obj));

    if (auto required_size = static_cast<jint>(header_int_count + cube_int_count);
        dst_array.size() < required_size) {
        // env->DeleteLocalRef(dst_array_obj);
        dst_array_obj = env->NewIntArray(required_size);
        YSM_ASSIGN_OR_RETURN(
            dst_array,
            java::CriticalIntArray<false>::Get(
                env, dst_array_obj));
        }

    // All per-bone headers precede cube ranges. Ranges are ordered by render
    // bone, then cutout, cutout-no-culling, translucent and translucent-culling.
    dst_array.data()[0] = render_cube_count;
    std::span headers{
        reinterpret_cast<PackedCubeInfoHeader*>(dst_array.data() + 1),
        render_bone_indices.size()};
    std::span dst{reinterpret_cast<PackedVertexRange*>(
                      dst_array.data() + header_int_count),
                  static_cast<size_t>(render_cube_count)};

    return state->Model()->VisitCubes([&](const auto& cubes) -> absl::StatusOr<jintArray> {
        const auto& bones = state->Model()->Bones();
        auto vertex_offsets = layout.vertex_offsets;
        size_t output_index = 0;
        const auto copy_partition = [&](const auto& partition_info,
                                        const auto& groups, bool culling,
                                        uint64_t& vertex_offset) {
            size_t partition_cube_count = 0;
            for (const auto group_index : partition_info.cube_indices) {
                if (group_index >= groups.size()) {
                    return false;
                }
                const auto& group = groups[group_index];
                for (uint32_t group_cube_index = 0;
                     group_cube_index < group.cube_count; ++group_cube_index) {
                    const auto& cube = group.cube_attr[group_cube_index];
                    const auto expected_vertex_count =
                        (culling ? cube.quad_count_after_culling :
                                   cube.quad_count) *
                        4U;
                    if (output_index >= dst.size()) {
                        return false;
                    }
                    dst[output_index++] = {
                        static_cast<uint32_t>(vertex_offset),
                        expected_vertex_count,
                    };
                    vertex_offset += expected_vertex_count;
                    ++partition_cube_count;
                }
            }
            if (partition_cube_count != partition_info.cube_count) {
                return false;
            }
            return true;
        };
        const auto error_supplier = []
            { return absl::InternalError("Illegal cube renge"); };

        for (size_t bone_index = 0;
             bone_index < render_bone_indices.size(); ++bone_index) {
            const auto& bone =
                bones.list[render_bone_indices[bone_index]];
            std::array<const gfx::bake::BakedModelBones::BonePartitionInfo*,
                       kPartitionCount>
                partition_infos{
                    &bone.cutout,
                    &bone.cutout_no_culling,
                    &bone.translucent,
                    &bone.translucent_culling,
                };
            const auto cube_counts = GetBoneCubeCounts(bone);
            for (size_t partition = 0; partition < kPartitionCount;
                 ++partition) {
                headers[bone_index].partition_cube_counts[partition] =
                    static_cast<uint16_t>(cube_counts[partition]);
            }

            YSM_ASSERT(copy_partition(
                *partition_infos[0], cubes.cutout, true, vertex_offsets[0]),
                error_supplier());
            YSM_ASSERT(copy_partition(
                *partition_infos[1], cubes.cutout_no_culling, false, vertex_offsets[1]),
                error_supplier());
            YSM_ASSERT(copy_partition(
                *partition_infos[2], cubes.translucent, false, vertex_offsets[2]),
                error_supplier());
            YSM_ASSERT(copy_partition(
                *partition_infos[3], cubes.translucent_culling, true, vertex_offsets[3]),
                error_supplier());
        }
        YSM_ASSERT(output_index == dst.size(),
                   absl::InternalError(
                       "Cube count is inconsistent with BakedModel."));
        return dst_array_obj;
    });
}
}  // namespace ysm::lib::render

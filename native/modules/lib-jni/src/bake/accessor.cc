#include <cstdint>

#include <gfx/bake/baked_model.h>
#include <gfx/bake/baked_serializer.h>
#include <bitpack.h>
#include <cpu.h>
#include <java/array.h>
#include <java/buffer.h>
#include <java/entry.h>
#include <log.h>

#include "java/opaque_ptr.h"

namespace ysm::lib::bake {

namespace {
YSM_BIT_PACK(PackedBakedModelInfo, jlong,
             YSM_BIT_FIELD(bone_count, uint16_t, 16),
             YSM_BIT_FIELD(gui_no_shadow, uint8_t, 1),
             YSM_BIT_FIELD(has_pbr, uint8_t, 1));

#pragma pack(push, 1)

struct PackedBoneInfo {
    struct PackedBonePartitionInfo {
        uint16_t cube_count;
        uint8_t full_vertex_count;
        uint8_t culling_vertex_count;
    };
    PackedBonePartitionInfo cutout;
    PackedBonePartitionInfo cutout_no_culling;
    PackedBonePartitionInfo translucent;
    PackedBonePartitionInfo translucent_culling;
};

struct alignas(4) PackedCubeData {
    struct PackedQuadData {
        std::array<float, 3> normal;
        std::array<float, 4> tangent;
        std::array<std::array<float, 2>, 4> uv;
        std::array<uint8_t, 4> vertex_index;
        float plane_d;
        float winding_sign;
    };

    std::array<std::array<float, 3>, 8> pos;
    std::array<PackedQuadData, 6> quad_data;
    uint8_t quad_count = 0;
    uint8_t quad_count_after_culling = 0;
};

#pragma pack(pop)
}  // namespace


YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/render/NativeBakedModel;nGetInfo(J)J",
    (baked_model_handle), -1) {
    YSM_DECLARE_OR_RETURN(baked_model,
        java::CastOpaquePtr<ysm::gfx::bake::BakedModel>(baked_model_handle));
    auto& info = baked_model->Info();
    return PackedBakedModelInfo::pack(baked_model->Bones().list.size(),
                                      info.gui_no_shadow, info.has_pbr);
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/render/NativeBakedModel;nGetBoneInfo(JII[I)Z",
    (baked_model_handle, bone_index, bone_count, dst_array_obj)) {
    YSM_DECLARE_OR_RETURN(baked_model,
        java::CastOpaquePtr<ysm::gfx::bake::BakedModel>(baked_model_handle));
    auto& info = baked_model->Bones();

    YSM_ASSERT(bone_index >= 0,
        absl::InvalidArgumentError("Bone index out of range"));
    YSM_ASSERT(static_cast<uint64_t>(bone_count) + bone_index <= info.list.size(),
        absl::InvalidArgumentError("Bone index out of range"));

    YSM_DECLARE_OR_RETURN(dst_array, java::CriticalIntArray<false>::Get(
        env, dst_array_obj, 0,
        (sizeof(PackedBoneInfo) / sizeof(jint)) * bone_count));
    std::span dst{reinterpret_cast<PackedBoneInfo*>(dst_array.data()),
        static_cast<size_t>(bone_count)};
    constinit static auto func = [](auto&& info, auto&& packed_info) {
        packed_info.cube_count =
            static_cast<uint16_t>(info.cube_count);
        packed_info.full_vertex_count =
            static_cast<uint8_t>(info.full_vertex_count);
        packed_info.culling_vertex_count =
            static_cast<uint8_t>(info.culling_vertex_count);
    };
    for (auto i = 0; i < bone_count; ++i) {
        auto& packed = dst[i];
        auto& bone_info = info.list[i + bone_index];
        func(bone_info.cutout, packed.cutout);
        func(bone_info.cutout_no_culling, packed.cutout_no_culling);
        func(bone_info.translucent, packed.translucent);
        func(bone_info.translucent_culling, packed.translucent_culling);
    }
    return OkStatus();
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/render/NativeBakedModel;nGetCubeData(JIIII[F)Z",
    (baked_model_handle, bone_index, bone_partition, cube_index, cube_count, dst_array_obj)) {
    YSM_DECLARE_OR_RETURN(baked_model,
        java::CastOpaquePtr<ysm::gfx::bake::BakedModel>(baked_model_handle));
    const auto& bones = baked_model->Bones();
    YSM_ASSERT(bone_index >= 0 &&
            static_cast<size_t>(bone_index) < bones.list.size(),
        absl::InvalidArgumentError("Bone index out of range"));
    YSM_ASSERT(bone_partition >= 0 && bone_partition < 4,
        absl::InvalidArgumentError("Bone partition out of range"));
    YSM_ASSERT(cube_index >= 0 && cube_count >= 0,
        absl::InvalidArgumentError("Cube index out of range"));

    const auto& bone_info = bones.list[bone_index];
    const auto* partition_info = &bone_info.cutout;
    switch (bone_partition) {
        case 1:
            partition_info = &bone_info.cutout_no_culling;
            break;
        case 2:
            partition_info = &bone_info.translucent;
            break;
        case 3:
            partition_info = &bone_info.translucent_culling;
            break;
        default:
            break;
    }
    YSM_ASSERT(static_cast<uint64_t>(cube_index) + cube_count <=
            partition_info->cube_count,
        absl::InvalidArgumentError("Cube index out of range"));

    static_assert(sizeof(PackedCubeData) % sizeof(jfloat) == 0);
    constexpr size_t kFloatCount = sizeof(PackedCubeData) / sizeof(jfloat);
    YSM_ASSERT(static_cast<uint64_t>(cube_count) * kFloatCount <= INT32_MAX,
        absl::InvalidArgumentError("Cube count out of range"));

    return baked_model->VisitCubes([&](auto& cubes) {
        YSM_DECLARE_OR_RETURN(dst_array, java::CriticalFloatArray<false>::Get(
            env, dst_array_obj, 0,
            static_cast<jint>(kFloatCount * cube_count)));
        std::span dst{reinterpret_cast<PackedCubeData*>(dst_array.data()),
            static_cast<size_t>(cube_count)};

        size_t skip_count = static_cast<size_t>(cube_index);
        size_t output_index = 0;
        auto copy_partition = [&](const auto& partition, const auto& groups) {
            for (const auto group_index : partition.cube_indices) {
                if (output_index == dst.size()) {
                    return;
                }

                const auto& group = groups[group_index];
                if (skip_count >= group.cube_count) {
                    skip_count -= group.cube_count;
                    continue;
                }

                for (auto group_cube_index =
                         static_cast<uint32_t>(skip_count);
                     group_cube_index < group.cube_count &&
                     output_index < dst.size();
                     ++group_cube_index) {
                    skip_count = 0;
                    auto& packed = dst[output_index++];
                    packed = {};

                    const auto vertex_offset = group_cube_index * 8U;
                    for (size_t vertex = 0; vertex < packed.pos.size();
                         ++vertex) {
                        for (size_t axis = 0; axis < packed.pos[vertex].size();
                             ++axis) {
                            packed.pos[vertex][axis] =
                                group.pos[axis][vertex_offset + vertex];
                        }
                    }

                    const auto& cube_attr = group.cube_attr[group_cube_index];
                    packed.quad_count = cube_attr.quad_count;
                    packed.quad_count_after_culling =
                        cube_attr.quad_count_after_culling;
                    for (uint32_t quad_index = 0;
                         quad_index < cube_attr.quad_count; ++quad_index) {
                        const auto attr_index = group.GetQuadAttrIndex(
                            group_cube_index, quad_index);
                        auto& quad = packed.quad_data[quad_index];
                        for (size_t axis = 0; axis < quad.normal.size();
                             ++axis) {
                            quad.normal[axis] = group.normal[axis][attr_index];
                        }
                        for (size_t axis = 0; axis < quad.tangent.size();
                             ++axis) {
                            quad.tangent[axis] =
                                group.tangent[axis][attr_index];
                        }
                        for (size_t vertex = 0; vertex < quad.uv.size();
                             ++vertex) {
                            for (size_t axis = 0; axis < quad.uv[vertex].size();
                                 ++axis) {
                                quad.uv[vertex][axis] =
                                    cube_attr.quad_attr[quad_index].uv[vertex][axis];
                            }
                        }
                        for (size_t vertex = 0;
                             vertex < quad.vertex_index.size(); ++vertex) {
                            quad.vertex_index[vertex] = static_cast<uint8_t>(
                                group.vertex_index[vertex][attr_index] -
                                vertex_offset);
                        }
                        quad.plane_d = group.plane_d[attr_index];
                        quad.winding_sign = group.winding_sign[attr_index];
                    }
                }
            }
        };

        switch (bone_partition) {
            case 0:
                copy_partition(*partition_info, cubes.cutout);
                break;
            case 1:
                copy_partition(*partition_info, cubes.cutout_no_culling);
                break;
            case 2:
                copy_partition(*partition_info, cubes.translucent);
                break;
            case 3:
                copy_partition(*partition_info, cubes.translucent_culling);
                break;
            default:
                break;
        }

        return OkStatus();
    });
}
}  // namespace ysm::lib::bake

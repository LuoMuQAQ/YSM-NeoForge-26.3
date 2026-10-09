#include "gfx/bake/baked_serializer.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>
#include <utility>
#include <vector>

#include <flatbuffers/flatbuffers.h>
#include <flatbuffers/verifier.h>

#include "fbs/baked_model_generated.h"

namespace ysm::gfx::bake {
namespace {
template <typename T, size_t Rows, size_t Columns>
flatbuffers::Offset<flatbuffers::Vector<T>> CreateArrayVector(
    flatbuffers::FlatBufferBuilder& builder,
    const std::array<std::array<T, Columns>, Rows>& values) {
    std::vector<T> flattened;
    flattened.reserve(Rows * Columns);
    for (const auto& row : values) {
        flattened.insert(flattened.end(), row.begin(), row.end());
    }
    return builder.CreateVector(flattened);
}

template <simd::Width kWidth, bool kTranslucent>
flatbuffers::Offset<fb::CubeGroup> SerializeGroup(
    flatbuffers::FlatBufferBuilder& builder,
    const CubeGroup<kWidth, kTranslucent>& group, bool has_pbr) {
    std::vector<flatbuffers::Offset<fb::CubeAttr>> cube_attrs;
    cube_attrs.reserve(group.cube_count);
    for (size_t cube_index = 0; cube_index < group.cube_count; ++cube_index) {
        const auto& attr = group.cube_attr[cube_index];
        std::vector<float> uv;
        std::vector<float> mid_uv;
        uv.reserve(attr.quad_count * 8ULL);
        mid_uv.reserve(attr.quad_count * 2ULL);
        for (size_t quad = 0; quad < attr.quad_count; ++quad) {
            for (const auto& vertex_uv : attr.quad_attr[quad].uv) {
                uv.push_back(vertex_uv[0]);
                uv.push_back(vertex_uv[1]);
            }
            mid_uv.push_back(attr.quad_attr[quad].mid_uv[0]);
            mid_uv.push_back(attr.quad_attr[quad].mid_uv[1]);
        }
        cube_attrs.push_back(fb::CreateCubeAttr(
            builder, attr.quad_count, attr.quad_count_after_culling,
            builder.CreateVector(uv), builder.CreateVector(mid_uv)));
    }
    auto center = flatbuffers::Offset<flatbuffers::Vector<float>>{};
    if constexpr (kTranslucent) {
        center = CreateArrayVector(builder, group.center);
    }
    auto tangent = flatbuffers::Offset<flatbuffers::Vector<float>>{};
    if (has_pbr) {
        tangent = CreateArrayVector(builder, group.tangent);
    }
    return fb::CreateCubeGroup(
        builder, CreateArrayVector(builder, group.pos), center,
        CreateArrayVector(builder, group.normal),
        builder.CreateVector(group.plane_d.data(), group.plane_d.size()),
        builder.CreateVector(group.winding_sign.data(),
                             group.winding_sign.size()),
        tangent, CreateArrayVector(builder, group.vertex_index),
        builder.CreateVector(cube_attrs), group.cube_count, group.bone_index);
}

flatbuffers::Offset<fb::BonePartitionInfo> SerializeBonePartition(
    flatbuffers::FlatBufferBuilder& builder,
    const BakedModelBones::BonePartitionInfo& bone) {
    std::vector<fb::CubeGroupInfo> group_info;
    group_info.reserve(bone.cube_group_info.size());
    for (const auto& info : bone.cube_group_info) {
        group_info.emplace_back(info.cube_count, info.quad_count,
                                info.quad_count_after_culling);
    }
    return fb::CreateBonePartitionInfo(
        builder,
        builder.CreateVector(bone.cube_indices.data(),
                             bone.cube_indices.size()),
        bone.full_vertex_count, bone.culling_vertex_count, bone.cube_count,
        builder.CreateVectorOfStructs(group_info));
}

template <simd::Width kWidth, bool kTranslucent>
flatbuffers::Offset<flatbuffers::Vector<flatbuffers::Offset<fb::CubeGroup>>>
SerializeGroups(flatbuffers::FlatBufferBuilder& builder,
                const std::vector<CubeGroup<kWidth, kTranslucent>>& groups,
                bool has_pbr) {
    std::vector<flatbuffers::Offset<fb::CubeGroup>> cube_groups;
    cube_groups.reserve(groups.size());
    for (const auto& group : groups) {
        cube_groups.push_back(SerializeGroup(builder, group, has_pbr));
    }
    return builder.CreateVector(cube_groups);
}

template <simd::Width kWidth>
BufferManaged Serialize(const BakedModelInfo& info,
                        const BakedModelBones& bones,
                        const BakedModelCubes<kWidth>& cubes) {
    flatbuffers::FlatBufferBuilder builder;
    std::vector<flatbuffers::Offset<fb::BoneInfo>> bone_info;
    bone_info.reserve(bones.list.size());
    for (const auto& bone : bones.list) {
        const auto cutout = SerializeBonePartition(builder, bone.cutout);
        const auto cutout_no_culling =
            SerializeBonePartition(builder, bone.cutout_no_culling);
        const auto translucent =
            SerializeBonePartition(builder, bone.translucent);
        const auto translucent_culling =
            SerializeBonePartition(builder, bone.translucent_culling);
        bone_info.push_back(fb::CreateBoneInfo(
            builder, cutout, cutout_no_culling, translucent,
            translucent_culling, bone.solid,
            builder.CreateVector(bone.pivot.data(), bone.pivot.size()),
            bone.parent_index, bone.subtree_end, bone.depth));
    }
    const auto serialized_bones = builder.CreateVector(bone_info);
    const auto sorted_bone_indices = builder.CreateVector(
        bones.sorted_bone_indices.data(), bones.sorted_bone_indices.size());
    const auto cutout = SerializeGroups(builder, cubes.cutout, info.has_pbr);
    const auto cutout_no_culling =
        SerializeGroups(builder, cubes.cutout_no_culling, info.has_pbr);
    const auto translucent =
        SerializeGroups(builder, cubes.translucent, info.has_pbr);
    const auto translucent_culling =
        SerializeGroups(builder, cubes.translucent_culling, info.has_pbr);
    const auto serialized_cubes = fb::CreateCubeData(
        builder, cutout, cutout_no_culling, translucent, translucent_culling);
    builder.Finish(fb::CreateBakedModel(builder, serialized_bones,
                                        sorted_bone_indices, serialized_cubes,
                                        info.gui_no_shadow, info.has_pbr));
    return BufferManaged(
        BufferViewR(builder.GetBufferPointer(), builder.GetSize()));
}

template <typename T, size_t Rows, size_t Columns>
bool CopyArrayVector(const flatbuffers::Vector<T>* source,
                     std::array<std::array<T, Columns>, Rows>& destination) {
    if (source == nullptr || source->size() != Rows * Columns) {
        return false;
    }
    for (size_t row = 0; row < Rows; ++row) {
        std::copy_n(source->begin() + row * Columns, Columns,
                    destination[row].begin());
    }
    return true;
}

template <simd::Width kWidth, bool kTranslucent>
absl::Status ReadGroups(
    const flatbuffers::Vector<flatbuffers::Offset<fb::CubeGroup>>& source,
    std::vector<CubeGroup<kWidth, kTranslucent>>& destination,
    size_t bone_count, bool has_pbr) {
    using Group = CubeGroup<kWidth, kTranslucent>;
    destination.resize(source.size());
    for (size_t group_index = 0; group_index < destination.size();
         ++group_index) {
        const auto* input = source.Get(group_index);
        auto& output = destination[group_index];
        const bool tangent_valid =
            has_pbr
                ? CopyArrayVector(input == nullptr ? nullptr : input->tangent(),
                                  output.tangent)
                : input != nullptr && input->tangent() == nullptr;
        if (input == nullptr || input->cube_count() == 0 ||
            input->cube_count() > Group::kCubeGroupCapacity ||
            input->bone_index() >= bone_count ||
            input->cube_attr() == nullptr ||
            input->cube_attr()->size() != input->cube_count() ||
            !CopyArrayVector(input->pos(), output.pos) ||
            !CopyArrayVector(input->normal(), output.normal) ||
            input->plane_d() == nullptr ||
            input->plane_d()->size() != output.plane_d.size() ||
            input->winding_sign() == nullptr ||
            input->winding_sign()->size() != output.winding_sign.size() ||
            !tangent_valid ||
            !CopyArrayVector(input->vertex_index(), output.vertex_index)) {
            return DataCorruption();
        }
        std::copy(input->plane_d()->begin(), input->plane_d()->end(),
                  output.plane_d.begin());
        std::copy(input->winding_sign()->begin(), input->winding_sign()->end(),
                  output.winding_sign.begin());
        if constexpr (kTranslucent) {
            if (!CopyArrayVector(input->center(), output.center)) {
                return DataCorruption();
            }
        } else if (input->center() != nullptr && !input->center()->empty()) {
            return DataCorruption();
        }
        output.cube_count = input->cube_count();
        output.bone_index = input->bone_index();
        for (size_t cube_index = 0; cube_index < output.cube_count;
             ++cube_index) {
            const auto* attr = input->cube_attr()->Get(cube_index);
            if (attr == nullptr || attr->quad_count() == 0 ||
                attr->quad_count() > 6 ||
                attr->quad_count_after_culling() > attr->quad_count() ||
                attr->uv() == nullptr ||
                attr->uv()->size() != attr->quad_count() * 8ULL ||
                attr->mid_uv() == nullptr ||
                attr->mid_uv()->size() != attr->quad_count() * 2ULL) {
                return DataCorruption();
            }
            auto& cube_attr = output.cube_attr[cube_index];
            cube_attr.quad_count = attr->quad_count();
            cube_attr.quad_count_after_culling =
                attr->quad_count_after_culling();
            for (size_t quad = 0; quad < cube_attr.quad_count; ++quad) {
                auto& quad_attr = cube_attr.quad_attr[quad];
                const auto attr_index =
                    Group::GetQuadAttrIndex(cube_index, quad);
                for (size_t vertex = 0; vertex < 4; ++vertex) {
                    const auto vertex_index =
                        output.vertex_index[vertex][attr_index];
                    if (vertex_index < cube_index * 8 ||
                        vertex_index >= (cube_index + 1) * 8) {
                        return DataCorruption();
                    }
                    quad_attr.uv[vertex][0] =
                        attr->uv()->Get(quad * 8 + vertex * 2);
                    quad_attr.uv[vertex][1] =
                        attr->uv()->Get(quad * 8 + vertex * 2 + 1);
                }
                quad_attr.mid_uv[0] = attr->mid_uv()->Get(quad * 2);
                quad_attr.mid_uv[1] = attr->mid_uv()->Get(quad * 2 + 1);
            }
        }
    }
    return OkStatus();
}

template <typename Group>
absl::Status ReadBonePartition(
    const fb::BonePartitionInfo& input,
    BakedModelBones::BonePartitionInfo& destination,
    std::span<uint32_t> indices_cache,
    std::span<BakedModelBones::CubeGroupInfo> group_info_cache,
    size_t& cache_offset, const std::vector<Group>& groups, size_t bone_index) {
    if (input.cube_indices() == nullptr || input.cube_group_info() == nullptr ||
        input.cube_group_info()->size() != input.cube_indices()->size() ||
        input.cube_indices()->size() > indices_cache.size() - cache_offset) {
        return DataCorruption();
    }
    auto indices =
        indices_cache.subspan(cache_offset, input.cube_indices()->size());
    auto group_info =
        group_info_cache.subspan(cache_offset, input.cube_indices()->size());
    std::copy(input.cube_indices()->begin(), input.cube_indices()->end(),
              indices.begin());

    uint64_t cube_count = 0;
    uint64_t quad_count = 0;
    uint64_t quad_count_after_culling = 0;
    for (size_t group_index = 0; group_index < indices.size(); ++group_index) {
        const auto index = indices[group_index];
        if (index >= groups.size() || groups[index].bone_index != bone_index ||
            (group_index != 0 && index <= indices[group_index - 1])) {
            return DataCorruption();
        }
        const auto& group = groups[index];
        cube_count += group.cube_count;
        for (size_t cube = 0; cube < group.cube_count; ++cube) {
            quad_count += group.cube_attr[cube].quad_count;
            quad_count_after_culling +=
                group.cube_attr[cube].quad_count_after_culling;
        }
        if (cube_count > std::numeric_limits<uint32_t>::max() ||
            quad_count > std::numeric_limits<uint32_t>::max() ||
            quad_count_after_culling > std::numeric_limits<uint32_t>::max()) {
            return DataCorruption();
        }
        const auto* cached = input.cube_group_info()->Get(group_index);
        if (cached == nullptr || cached->cube_count() != cube_count ||
            cached->quad_count() != quad_count ||
            cached->quad_count_after_culling() != quad_count_after_culling) {
            return DataCorruption();
        }
        group_info[group_index] = {
            static_cast<uint32_t>(cube_count),
            static_cast<uint32_t>(quad_count),
            static_cast<uint32_t>(quad_count_after_culling),
        };
    }
    if (input.cube_count() != cube_count ||
        input.full_vertex_count() != quad_count * 4ULL ||
        input.culling_vertex_count() != quad_count_after_culling * 4ULL) {
        return DataCorruption();
    }
    destination.cube_indices = indices;
    destination.cube_group_info = group_info;
    destination.cube_count = input.cube_count();
    destination.full_vertex_count = input.full_vertex_count();
    destination.culling_vertex_count = input.culling_vertex_count();
    cache_offset += indices.size();
    return OkStatus();
}

template <simd::Width kWidth>
absl::StatusOr<std::unique_ptr<BakedModel>> ReadForWidth(
    const fb::BakedModel& input) {
    if (input.bones() == nullptr || input.sorted_bone_indices() == nullptr ||
        input.cubes() == nullptr) {
        return DataCorruption();
    }
    const auto& input_cubes = *input.cubes();
    if (input_cubes.cutout() == nullptr ||
        input_cubes.cutout_no_culling() == nullptr ||
        input_cubes.translucent() == nullptr ||
        input_cubes.translucent_culling() == nullptr) {
        return DataCorruption();
    }
    const size_t bone_count = input.bones()->size();
    if (input.sorted_bone_indices()->size() != bone_count) {
        return DataCorruption();
    }
    BakedModelInfo info{
        .gui_no_shadow = input.gui_no_shadow(),
        .has_pbr = input.has_pbr(),
    };
    BakedModelBones bones;
    BakedModelCubes<kWidth> cubes;
    bones.sorted_bone_indices.resize(bone_count);
    bones.list.resize(bone_count);

    std::vector<bool> seen_original_indices(bone_count);
    for (size_t sorted = 0; sorted < bone_count; ++sorted) {
        const auto original = input.sorted_bone_indices()->Get(sorted);
        if (original >= bone_count || seen_original_indices[original]) {
            return DataCorruption();
        }
        seen_original_indices[original] = true;
        bones.sorted_bone_indices[sorted] = original;
    }

    size_t index_count = 0;
    for (size_t i = 0; i < bone_count; ++i) {
        const auto* bone = input.bones()->Get(i);
        if (bone == nullptr || bone->cutout() == nullptr ||
            bone->cutout_no_culling() == nullptr ||
            bone->translucent() == nullptr ||
            bone->translucent_culling() == nullptr ||
            bone->pivot() == nullptr || bone->pivot()->size() != 3 ||
            bone->subtree_end() <= i || bone->subtree_end() > bone_count ||
            (bone->parent_index() != UINT32_MAX && bone->parent_index() >= i)) {
            return DataCorruption();
        }
        auto& output_bone = bones.list[i];
        output_bone.solid = bone->solid();
        output_bone.parent_index = bone->parent_index();
        output_bone.subtree_end = bone->subtree_end();
        const auto expected_depth =
            output_bone.parent_index == UINT32_MAX
                ? 0ULL
                : static_cast<uint64_t>(
                      bones.list[output_bone.parent_index].depth) +
                      1ULL;
        if (bone->depth() != expected_depth) {
            return DataCorruption();
        }
        output_bone.depth = bone->depth();
        std::copy_n(bone->pivot()->begin(), 3, output_bone.pivot.begin());
        if (std::ranges::any_of(output_bone.pivot, [](float value) {
                return !std::isfinite(value);
            })) {
            return DataCorruption();
        }
        for (const auto* partition :
             {bone->cutout(), bone->cutout_no_culling(), bone->translucent(),
              bone->translucent_culling()}) {
            if (partition->cube_indices() == nullptr ||
                partition->cube_indices()->size() >
                    std::numeric_limits<size_t>::max() - index_count) {
                return DataCorruption();
            }
            index_count += partition->cube_indices()->size();
        }
    }
    std::vector<uint32_t> open_bones;
    open_bones.reserve(bone_count);
    for (uint32_t i = 0; i < bone_count; ++i) {
        const auto parent = bones.list[i].parent_index;
        while (!open_bones.empty() && open_bones.back() != parent) {
            if (bones.list[open_bones.back()].subtree_end != i) {
                return DataCorruption();
            }
            open_bones.pop_back();
        }
        if (parent != UINT32_MAX &&
            (open_bones.empty() || open_bones.back() != parent)) {
            return DataCorruption();
        }
        open_bones.push_back(i);
    }
    while (!open_bones.empty()) {
        if (bones.list[open_bones.back()].subtree_end != bone_count) {
            return DataCorruption();
        }
        open_bones.pop_back();
    }
    YSM_RETURN_IF_ERROR(ReadGroups(*input_cubes.cutout(), cubes.cutout,
                                    bone_count, info.has_pbr));
    YSM_RETURN_IF_ERROR(ReadGroups(*input_cubes.cutout_no_culling(),
                                    cubes.cutout_no_culling, bone_count,
                                    info.has_pbr));
    YSM_RETURN_IF_ERROR(ReadGroups(*input_cubes.translucent(),
                                    cubes.translucent, bone_count,
                                    info.has_pbr));
    YSM_RETURN_IF_ERROR(ReadGroups(*input_cubes.translucent_culling(),
                                    cubes.translucent_culling, bone_count,
                                    info.has_pbr));

    bones.cube_indices_cache.resize(index_count);
    bones.cube_group_info_cache.resize(index_count);
    auto indices_cache = std::span(bones.cube_indices_cache);
    auto group_info_cache = std::span(bones.cube_group_info_cache);
    size_t offset = 0;
    for (size_t i = 0; i < bone_count; ++i) {
        const auto& input_bone = *input.bones()->Get(i);
        auto& output_bone = bones.list[i];
        YSM_RETURN_IF_ERROR(ReadBonePartition(
            *input_bone.cutout(), output_bone.cutout, indices_cache,
            group_info_cache, offset, cubes.cutout, i));
        YSM_RETURN_IF_ERROR(ReadBonePartition(
            *input_bone.cutout_no_culling(), output_bone.cutout_no_culling,
            indices_cache, group_info_cache, offset, cubes.cutout_no_culling,
            i));
        YSM_RETURN_IF_ERROR(ReadBonePartition(
            *input_bone.translucent(), output_bone.translucent, indices_cache,
            group_info_cache, offset, cubes.translucent, i));
        YSM_RETURN_IF_ERROR(ReadBonePartition(
            *input_bone.translucent_culling(), output_bone.translucent_culling,
            indices_cache, group_info_cache, offset, cubes.translucent_culling,
            i));
    }
    if (offset != index_count) {
        return DataCorruption();
    }
    return std::make_unique<BakedModel>(info, std::move(bones),
                                        std::move(cubes));
}
}  // namespace

BufferManaged SerializeBakedModel(const BakedModel& model) {
    BufferManaged output;
    model.VisitCubes(
        [&](const auto& cubes) {
            output = Serialize(model.Info(), model.Bones(), cubes);
        });
    return output;
}

absl::StatusOr<std::unique_ptr<BakedModel>> ReadBakedModel(
    BufferViewR baked_model_data) {
    flatbuffers::Verifier verifier(baked_model_data.data(),
                                   baked_model_data.size());
    if (!fb::VerifyBakedModelBuffer(verifier)) {
        return DataCorruption();
    }

    const auto* model = fb::GetBakedModel(baked_model_data.data());
    return magic_enum::enum_switch(
        [&](auto kType) { return ReadForWidth<simd::width<kType>()>(*model); },
        simd::kSupported);
}
}  // namespace ysm::gfx::bake

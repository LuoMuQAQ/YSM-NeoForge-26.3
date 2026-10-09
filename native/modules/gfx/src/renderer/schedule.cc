#include "gfx/renderer/schedule.h"

#include <algorithm>
#include <cstdint>
#include <limits>
#include <span>
#include <utility>
#include <vector>

#include "err.h"
#include "profile.h"

namespace ysm::gfx::renderer {
namespace {
struct PartitionGroupCounts {
    size_t cutout = 0;
    size_t cutout_no_culling = 0;
    size_t translucent = 0;
    size_t translucent_culling = 0;
};

struct InlineThreshold {
    size_t bones;
    size_t cube_groups;
};

constexpr InlineThreshold InlineThresholdFor(size_t worker_count) noexcept {
    if (worker_count == 2) {
        return {4, 6};
    }
    if (worker_count < 7) {
        return {6, 6};
    }
    return {6, 8};
}

template <bool kCulling>
uint32_t VertexCount(const bake::BakedModelBones::BonePartitionInfo& bone,
                     size_t begin, size_t end) noexcept {
    const auto count_at = [&](size_t position) {
        if (position == 0) {
            return 0U;
        }
        const auto& info = bone.cube_group_info[position - 1];
        if constexpr (kCulling) {
            return info.quad_count_after_culling;
        } else {
            return info.quad_count;
        }
    };
    return (count_at(end) - count_at(begin)) * 4U;
}

template <bool kCulling>
class PartitionCursor {
    using TaskPartition = RenderTask::Partition RenderTask::*;

public:
    PartitionCursor(size_t group_count, TaskPartition task_partition,
                    std::span<RenderTask> tasks)
        : group_count_(group_count),
          task_partition_(task_partition),
          tasks_(tasks) {
        Move(0);
    }

    bool Append(
        const bake::BakedModelBones::BonePartitionInfo& bone) {
        size_t bone_group_begin = 0;
        while (bone_group_begin < bone.cube_indices.size()) {
            while (scheduled_group_count_ >= task_end_ &&
                   worker_ + 1 < tasks_.size()) {
                Move(worker_ + 1);
            }
            if (scheduled_group_count_ >= task_end_) [[unlikely]] {
                return false;
            }

            const auto group_count_for_task =
                std::min(bone.cube_indices.size() - bone_group_begin,
                         task_end_ - scheduled_group_count_);
            const auto bone_group_end = bone_group_begin + group_count_for_task;
            const auto vertex_count =
                VertexCount<kCulling>(bone, bone_group_begin, bone_group_end);
            auto& output = tasks_[worker_].*task_partition_;
            if (vertex_count > std::numeric_limits<uint32_t>::max() -
                                   output.expected_vertex_count) [[unlikely]] {
                return false;
            }

            const auto selected = bone.cube_indices.subspan(
                bone_group_begin, group_count_for_task);
            output.cube_indices.insert(output.cube_indices.end(),
                                       selected.begin(), selected.end());
            output.expected_vertex_count += vertex_count;
            scheduled_group_count_ += group_count_for_task;
            bone_group_begin = bone_group_end;
        }
        return true;
    }

    bool Finish(uint64_t& vertex_offset) {
        if (scheduled_group_count_ != group_count_) [[unlikely]] {
            return false;
        }
        while (worker_ + 1 < tasks_.size()) {
            Move(worker_ + 1);
        }
        for (auto& task : tasks_) {
            auto& output = task.*task_partition_;
            if (vertex_offset > std::numeric_limits<uint32_t>::max()) [[unlikely]] {
                return false;
            }
            output.vertex_offset = static_cast<uint32_t>(vertex_offset);
            vertex_offset += output.expected_vertex_count;
        }
        if (vertex_offset > std::numeric_limits<uint32_t>::max()) {
            return false;
        }
        return true;
    }

private:
    void Move(size_t worker) {
        worker_ = worker;
        const auto [begin, end] =
            DetermineTaskRange(worker_, tasks_.size(), group_count_);
        auto& output = tasks_[worker_].*task_partition_;
        output.cube_indices.clear();
        output.vertex_offset = 0;
        output.expected_vertex_count = 0;
        output.cube_indices.reserve(end - begin);
        task_end_ = end;
    }

    size_t group_count_;
    TaskPartition task_partition_;
    std::span<RenderTask> tasks_;
    size_t worker_ = 0;
    size_t scheduled_group_count_ = 0;
    size_t task_end_ = 0;
};
}  // namespace

RenderSchedulingMode DetermineRenderSchedulingMode(
    size_t worker_count, size_t render_bone_count,
    size_t cube_group_count) noexcept {
    if (worker_count <= 1) {
        return RenderSchedulingMode::kInline;
    }
    const auto inline_threshold = InlineThresholdFor(worker_count);
    if (render_bone_count <= inline_threshold.bones &&
        cube_group_count <= inline_threshold.cube_groups) {
        return RenderSchedulingMode::kInline;
    }
    constexpr size_t kMaxWorkerReadyWorkers = 64;
    if (worker_count <= kMaxWorkerReadyWorkers &&
        render_bone_count >= worker_count * 32) {
        return RenderSchedulingMode::kWorkerReadySpin;
    }
    if (worker_count <= 3) {
        return RenderSchedulingMode::kSerialLateWake;
    }
    return RenderSchedulingMode::kSerialPrewake;
}

absl::Status RenderSchedule::Update(
    const bake::BakedModelBones& bones,
    std::span<const uint16_t> render_bone_indices, size_t worker_count) {
    YSM_PROFILE_ZONE("YSM/C++/RenderSchedule.Update");
    YSM_ASSERT(worker_count != 0,
               absl::InternalError("Renderer has no workers."));
    YSM_ASSERT(worker_count <=
                   static_cast<size_t>(std::numeric_limits<uint16_t>::max()) +
                       1,
               absl::InvalidArgumentError("Too many renderer workers."));
    vertex_count = 0;
    translucent_vertex_count = 0;
    translucent_vertex_offset = 0;

    const auto bone_count = bones.list.size();

    PartitionGroupCounts group_counts;
    static constinit auto accumulate = [](const auto& partition, size_t& count) {
        if (partition.cube_group_info.size() !=
            partition.cube_indices.size()) [[unlikely]] {
            return false;
            }
        count += partition.cube_indices.size();
        return true;
    };

    for (const auto bone_index : render_bone_indices) {
        YSM_ASSERT(
            bone_index < bone_count,
            absl::InvalidArgumentError("Render bone index out of range."));
        const auto& bone = bones.list[bone_index];
        YSM_ASSERT(accumulate(bone.cutout,
                              group_counts.cutout) &&
                   accumulate(bone.cutout_no_culling,
                              group_counts.cutout_no_culling) &&
                   accumulate(bone.translucent,
                              group_counts.translucent) &&
                   accumulate(bone.translucent_culling,
                              group_counts.translucent_culling),
                   absl::DataLossError(
                       "Inconsistent cube group cache in baked model."));
    }
    auto cube_group_count =
        group_counts.cutout + group_counts.cutout_no_culling +
        group_counts.translucent + group_counts.translucent_culling;
    YSM_PROFILE_VALUE(cube_group_count);
    mode = DetermineRenderSchedulingMode(worker_count, render_bone_indices.size(),
                                         cube_group_count);
    tasks.resize(worker_count);
    auto scheduled_tasks = mode != RenderSchedulingMode::kInline
                               ? std::span{tasks}
                               : std::span{tasks.data(), 1};
    PartitionCursor<true> cutout(group_counts.cutout, &RenderTask::cutout,
                                 scheduled_tasks);
    PartitionCursor<false> cutout_no_culling(
        group_counts.cutout_no_culling, &RenderTask::cutout_no_culling,
        scheduled_tasks);
    PartitionCursor<false> translucent(group_counts.translucent,
                                       &RenderTask::translucent,
                                       scheduled_tasks);
    PartitionCursor<true> translucent_culling(
        group_counts.translucent_culling, &RenderTask::translucent_culling,
        scheduled_tasks);

    const bool worker_ready = mode == RenderSchedulingMode::kWorkerReadySpin;
    if (worker_ready) {
        bone_update_owners.assign(bones.list.size(), UINT8_MAX);
    } else {
        bone_update_owners.clear();
    }

    size_t bone_worker = 0;
    size_t bone_worker_end =
        worker_ready
            ? DetermineTaskRange(0, worker_count, render_bone_indices.size())
                  .second
            : 0;

    constinit static auto error_supplier = [] {
        return absl::InternalError("Failed to update schedule");
    };

    for (size_t render_index = 0; render_index < render_bone_indices.size();
         ++render_index) {
        const auto bone_index = render_bone_indices[render_index];
        if (worker_ready) {
            while (render_index >= bone_worker_end &&
                   bone_worker + 1 < worker_count) {
                ++bone_worker;
                bone_worker_end =
                    DetermineTaskRange(bone_worker, worker_count,
                                       render_bone_indices.size())
                        .second;
            }
            bone_update_owners[bone_index] =
                static_cast<uint8_t>(bone_worker);
        }

        const auto& bone = bones.list[bone_index];
        YSM_ASSERT(cutout.Append(bone.cutout), error_supplier());
        YSM_ASSERT(cutout_no_culling.Append(bone.cutout_no_culling), error_supplier());
        YSM_ASSERT(translucent.Append(bone.translucent), error_supplier());
        YSM_ASSERT(translucent_culling.Append(bone.translucent_culling), error_supplier());
    }

    uint64_t opaque_vertex_count = 0;
    uint64_t translucent_vertex_count64 = 0;
    YSM_ASSERT(cutout.Finish(opaque_vertex_count), error_supplier());
    YSM_ASSERT(cutout_no_culling.Finish(opaque_vertex_count), error_supplier());
    YSM_ASSERT(translucent.Finish(translucent_vertex_count64), error_supplier());
    YSM_ASSERT(translucent_culling.Finish(translucent_vertex_count64), error_supplier());
    YSM_ASSERT(opaque_vertex_count + translucent_vertex_count64 <=
                   std::numeric_limits<uint32_t>::max(),
               absl::ResourceExhaustedError(
                   "Render schedule vertex count exceeds uint32_t."));

    vertex_count =
        static_cast<uint32_t>(opaque_vertex_count + translucent_vertex_count64);
    translucent_vertex_count = translucent_vertex_count64;
    translucent_vertex_offset = static_cast<uint32_t>(opaque_vertex_count);

    return OkStatus();
}
}  // namespace ysm::gfx::renderer

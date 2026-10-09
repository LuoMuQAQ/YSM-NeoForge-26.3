#pragma once

#include <cstddef>
#include <cstdint>
#include <span>
#include <vector>

#include "gfx/bake/baked_model.h"

namespace ysm::gfx::renderer {
enum class RenderSchedulingMode : uint8_t {
    kInline,
    kSerialLateWake,
    kSerialPrewake,
    kWorkerReadySpin,
};

[[nodiscard]] RenderSchedulingMode DetermineRenderSchedulingMode(
    size_t worker_count, size_t render_bone_count,
    size_t cube_group_count) noexcept;

struct RenderTask {
    struct Partition {
        std::vector<std::uint32_t> cube_indices;
        uint32_t vertex_offset = 0;
        uint32_t expected_vertex_count = 0;
    };

    Partition cutout;
    Partition cutout_no_culling;
    Partition translucent;
    Partition translucent_culling;
};

struct RenderSchedule {
    uint32_t vertex_count = 0;
    uint32_t translucent_vertex_count = 0;
    uint32_t translucent_vertex_offset = 0;
    RenderSchedulingMode mode = RenderSchedulingMode::kInline;
    std::vector<RenderTask> tasks;
    std::vector<uint8_t> bone_update_owners;

    absl::Status Update(const bake::BakedModelBones& bones,
                        std::span<const uint16_t> render_bone_indices,
                        size_t worker_count);
};

inline std::pair<size_t, size_t> DetermineTaskRange(size_t index, size_t workers,
                                             size_t tasks) noexcept {
    const auto split = [&](size_t worker) {
        return tasks / workers * worker + tasks % workers * worker / workers;
    };
    return {split(index), split(index + 1)};
}
}  // namespace ysm::gfx::renderer

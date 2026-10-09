#pragma once

#include <cstdint>
#include <cglm/vec3.h>

namespace ysm::gfx::renderer::vertex {
#pragma pack(push, 1)
struct Iris55Vertex {
    static constexpr size_t kSize = 55;

    vec3 pos{};
    uint32_t color = 0;
    vec2 tex_uv{};

    uint32_t overlay = 0;
    uint32_t light = 0;

    uint32_t normal = 0;

    // iris extended
    uint16_t entity_id[3]{};
    vec2 mid_tex_uv{};
    uint32_t tangent = 0;

    [[maybe_unused]] const uint16_t padding_ = 0;

    void SetVertex(float x, float y, float z, const vec2 uv) noexcept {
        pos[0] = x;
        pos[1] = y;
        pos[2] = z;
        tex_uv[0] = uv[0];
        tex_uv[1] = uv[1];
    }

    void SetColor(uint32_t value) noexcept {
        color = value;
    }

    void SetOverlay(uint32_t value) noexcept {
        overlay = value;
    }

    void SetLight(uint32_t value) noexcept {
        light = value;
    }

    void SetNormal(uint32_t value) noexcept {
        normal = value;
    }

    void SetIrisEntityId(uint64_t value) noexcept {
        std::memcpy(entity_id, &value, 6);
    }

    void SetIrisMidUV(const vec2 value) noexcept {
        mid_tex_uv[0] = value[0];
        mid_tex_uv[1] = value[1];
    }

    void SetIrisTangent(uint32_t value) noexcept {
        tangent = value;
    }
};
#pragma pack(pop)
}

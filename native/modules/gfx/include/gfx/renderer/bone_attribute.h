#pragma once

#include <cstddef>
#include <type_traits>

#include <cglm/vec3.h>

namespace ysm::gfx::renderer {
struct BoneAttribute {
    static constexpr size_t kFloatCount = 14;
    static constexpr size_t kSize = kFloatCount * sizeof(float);

    vec3 rotation{};
    vec3 position{};
    vec3 scale{};
    float cubes_hidden = 0.0f;
    float children_hidden = 0.0f;
    float locator_sequence = 0.0f;
    float color = 16777215.0f;
    float transparency_glow = 65535.0f;
};

static_assert(std::is_standard_layout_v<BoneAttribute>);
static_assert(std::is_trivially_copyable_v<BoneAttribute>);
static_assert(alignof(BoneAttribute) == alignof(float));
static_assert(offsetof(BoneAttribute, rotation) == 0);
static_assert(offsetof(BoneAttribute, position) == 3 * sizeof(float));
static_assert(offsetof(BoneAttribute, scale) == 6 * sizeof(float));
static_assert(offsetof(BoneAttribute, cubes_hidden) == 9 * sizeof(float));
static_assert(offsetof(BoneAttribute, children_hidden) == 10 * sizeof(float));
static_assert(offsetof(BoneAttribute, locator_sequence) == 11 * sizeof(float));
static_assert(offsetof(BoneAttribute, color) == 12 * sizeof(float));
static_assert(offsetof(BoneAttribute, transparency_glow) == 13 * sizeof(float));
static_assert(sizeof(BoneAttribute) == BoneAttribute::kSize);
}  // namespace ysm::gfx::renderer

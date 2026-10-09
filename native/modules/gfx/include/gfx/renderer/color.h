#pragma once

#include <cstdint>

namespace ysm::gfx::renderer {

#pragma pack(push, 1)
union Color {
    struct {
        uint8_t r;
        uint8_t g;
        uint8_t b;
        uint8_t a;
    } components;
    uint32_t packed;
};
static_assert(sizeof(Color) == 4);
static_assert(std::is_trivial_v<Color>);
#pragma pack(pop)

}
#pragma once

#include <cstdint>

namespace ysm::gfx::renderer {
enum class VertexKind : uint16_t {
    kFallback,
    kVanilla,
    kIris56,
    kIris56Ar,
    kIris55,
    kIris54
};
}
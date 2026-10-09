#pragma once

#include <cglm/types.h>

namespace ysm::benchmarking {
void GlmMat4MulAvx(const mat4 left, const mat4 right,
                   mat4 destination) noexcept;
}  // namespace ysm::benchmarking

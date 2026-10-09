#include "cglm_avx.h"

#include <cglm/mat4.h>

namespace ysm::benchmarking {
#ifdef YSM_X64
void GlmMat4MulAvx(const mat4 left, const mat4 right,
                                mat4 destination) noexcept {
    glm_mat4_mul_avx(left, right, destination);
}
#endif
}  // namespace ysm::benchmarking

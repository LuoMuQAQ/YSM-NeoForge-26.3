#include <mimalloc.h>
#include <bit>

#include <java/buffer.h>
#include <java/entry.h>

namespace ysm::lib::buffer {
static_assert(sizeof(jlong) == sizeof(void*));

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/buffer/NativeHeapBuffer;nAlloc(II)J",
    (size, alignment)) {
    if (alignment <= 0) {
        return reinterpret_cast<jlong>(mi_malloc(size));
    } else {
        return reinterpret_cast<jlong>(mi_aligned_alloc(alignment, size));
    }
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/buffer/NativeHeapBuffer;nFree(J)V",
    (addr)) {
    mi_free(reinterpret_cast<void*>(addr));
    return OkStatus();
}
}  // namespace ysm::lib::buffer

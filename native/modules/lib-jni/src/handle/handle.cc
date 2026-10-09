#include <java/entry.h>

#include "java/opaque_ptr.h"

namespace ysm::lib::handle {
YSM_JNI_ENTRY("Lcom/elfmcys/ysm/natives/NativeObject;nDestroy(J)V", (ptr)) {
    java::DestroyOpaquePtr(ptr);
    return OkStatus();
}
}

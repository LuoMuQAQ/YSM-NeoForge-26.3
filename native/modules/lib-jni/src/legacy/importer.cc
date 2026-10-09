#include <absl/status/status.h>

#include <cstdint>

#include <java/buffer.h>
#include <java/entry.h>

#include <legacy/v3.h>

#include "result_bridge.h"
#include "result_owner.h"

namespace ysm::legacy::java {
namespace {
absl::StatusOr<jobject> Import(JNIEnv_* env, jobject source,
                               jlong source_flags) {
    if (source == nullptr) {
        return NewFailureResult(
            env, absl::InvalidArgumentError("Legacy source buffer is null"));
    }
    YSM_DECLARE_OR_RETURN(
        bytes, ::ysm::java::BufferInput<true>::Get(env, source, source_flags));
    auto result = ::ysm::legacy::v3::Import(bytes);
    if (!result.ok()) {
        return NewFailureResult(env, result.status());
    }
    return NewSuccessResult(env, std::move(result).value());
}

absl::Status Release(JNIEnv_*, jlong handle) {
    if (handle == 0) {
        return absl::InvalidArgumentError("Legacy result handle is zero");
    }
    auto* owner =
        reinterpret_cast<ResultOwner*>(static_cast<std::intptr_t>(handle));
    delete owner;
    return absl::OkStatus();
}
}  // namespace

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/legacy/NativeLegacyImporter;nImport(Ljava/lang/"
    "Object;J)Lcom/elfmcys/ysm/natives/legacy/NativeLegacyImportResult;",
    (source, source_flags)) {
    return Import(env, source, source_flags);
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/legacy/NativeLegacyImporter;nRelease(J)V",
    (handle)) {
    return Release(env, handle);
}
}  // namespace ysm::legacy::java

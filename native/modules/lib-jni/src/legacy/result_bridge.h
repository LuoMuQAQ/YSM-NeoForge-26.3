#pragma once

#include <absl/status/statusor.h>
#include <jni.h>

#include <string_view>

#include <legacy/v3.h>

namespace ysm::legacy::java {
absl::StatusOr<jobject> NewFailureResult(JNIEnv_* env,
                                         const absl::Status& status);

absl::StatusOr<jobject> NewSuccessResult(JNIEnv_* env, v3::ImportResult result);
}  // namespace ysm::legacy::java

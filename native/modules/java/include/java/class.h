#pragma once

#include <jni.h>

#include "c_string_view.h"
#include "err.h"

namespace ysm::java {
absl::StatusOr<jclass> FindClass(JNIEnv_* env, CStringView class_name);
}

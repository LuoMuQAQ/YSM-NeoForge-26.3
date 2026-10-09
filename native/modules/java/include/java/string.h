#pragma once

#include <jni.h>

#include "c_string_view.h"
#include "err.h"
#include "fs.h"
#include "java/ref.h"

namespace ysm::java {
jclass StrType(JNIEnv_* env);

Ref<jstring> U8ToStr(JNIEnv_* env, CStringView str);

std::string StrToU8(JNIEnv_* env, jstring str);

fs::path StrToPath(JNIEnv_* env, jstring str);

Ref<jstring> PathToStr(JNIEnv_* env, const fs::path& path);
}  // namespace ysm::java

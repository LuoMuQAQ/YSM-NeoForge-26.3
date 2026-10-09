#pragma once

#include <jni.h>
#include <type_traits>

namespace ysm::java {
template <typename T>
concept JavaType =
    std::is_same_v<T, jboolean> || std::is_same_v<T, jbyte> ||
    std::is_same_v<T, jchar> || std::is_same_v<T, jshort> ||
    std::is_same_v<T, int> || std::is_same_v<T, jint> ||
    std::is_same_v<T, jlong> || std::is_same_v<T, jfloat> ||
    std::is_same_v<T, jdouble> || std::is_assignable_v<jobject&, T>;

template <typename T>
concept JavaObjectType = std::is_assignable_v<jobject&, T>;
}  // namespace ysm::java

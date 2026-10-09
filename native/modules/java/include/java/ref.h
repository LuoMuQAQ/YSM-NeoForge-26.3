#pragma once

#include <absl/functional/any_invocable.h>
#include <jni.h>
#include <memory>

#include "java/type.h"

namespace ysm::java {
namespace internal {
struct LocalRefDeleter {
    JNIEnv_* const env{};

    void operator()(jobject obj) const noexcept {
        if (obj && env) [[likely]] {
            env->DeleteLocalRef(obj);
        }
    }
};
}  // namespace internal

template <JavaObjectType T>
using Ref =
    std::unique_ptr<std::remove_pointer_t<T>, internal::LocalRefDeleter>;

// template<typename T>
// using SharedRef = std::shared_ptr<std::remove_pointer_t<T>>;

template <JavaObjectType T>
Ref<T> LocalRef(JNIEnv_* env, T obj) {
    if (obj == nullptr) [[unlikely]] {
        return Ref<T>(nullptr, {});
    }

    return Ref<T>(obj, {env});
}

template <JavaObjectType T>
Ref<T> DummyRef(T obj) {
    if (obj == nullptr) [[unlikely]] {
        return Ref<T>(nullptr, {});
    }
    return Ref<T>(obj, {});
}

/*
void ReleaseGlobalRef(jobject obj);

template<JavaObject T>
Ref<T> ToGlobalRefUnsafe(JNIEnv_ *env, T obj) {
    if (obj == nullptr) [[unlikely]] {
        return Ref<T>(nullptr, [](jobject) {});
    }
    auto global_ref = reinterpret_cast<T>(env->NewGlobalRef(obj));
    return Ref<T>(global_ref, [](jobject obj) {
        ReleaseGlobalRef(obj);
    });
}

template<typename RefType, JavaObject T = typename RefType::element_type*>
Ref<T> ToGlobalRef(JNIEnv_ *env, const RefType& obj) {
    return ToGlobalRefUnsafe(env, obj.get());
}

template<JavaObject T>
SharedRef<T> ToSharedRefUnsafe(JNIEnv_ *env, T obj) {
    if (obj == nullptr) [[unlikely]] {
        return nullptr;
    }
    auto global_ref = reinterpret_cast<T>(env->NewGlobalRef(obj));
    return SharedRef<T>(global_ref, [](jobject obj) {
        ReleaseGlobalRef(obj);
    });
}

template<typename RefType, JavaObject T = typename RefType::element_type*>
SharedRef<T> ToSharedRef(JNIEnv_ *env, const RefType &obj) {
    return ToSharedRefUnsafe(env, obj.get());
}
*/
}  // namespace ysm::java

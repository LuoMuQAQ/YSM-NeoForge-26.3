#include "java/class.h"

#include <absl/synchronization/mutex.h>
#include <absl/container/flat_hash_map.h>

namespace ysm::java {
namespace {
absl::flat_hash_map<std::string, jclass> g_class_cache;
absl::Mutex g_lock;
}

absl::StatusOr<jclass> FindClass(JNIEnv_* env, CStringView class_name) {
    {
        absl::ReaderMutexLock reader_lock(g_lock);
        auto iter = g_class_cache.find(class_name);
        if (iter != g_class_cache.end()) {
            return iter->second;
        }
    }

    absl::WriterMutexLock writer_lock(g_lock);
    if (auto iter = g_class_cache.find(class_name);
        iter != g_class_cache.end()) {
        return iter->second;
    }
    auto clazz = env->FindClass(class_name.c_str());
    if (clazz == nullptr) [[unlikely]] {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return absl::NotFoundError(
            std::format("Class not found: {}", class_name));
    }

    auto class_ref = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    env->DeleteLocalRef(clazz);
    g_class_cache.emplace(class_name, class_ref);
    return class_ref;
}
}  // namespace ysm::java

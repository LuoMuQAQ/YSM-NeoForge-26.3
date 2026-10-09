#include <java/entry.h>

#include <log.h>

#include "cpu.h"

using namespace ysm;

constexpr auto kJniVersion = JNI_VERSION_1_8;

jint JNI_OnLoad(JavaVM* vm, void*) {
    InitializeLogging();

    JNIEnv_* env = nullptr;
    auto result = vm->GetEnv(reinterpret_cast<void**>(&env), kJniVersion);
    if (result != JNI_OK || env == nullptr) {
        YSM_LOG(ERROR, "Failed to acquire JNI environment: {}", result);
        return JNI_ERR;
    }

    if (auto status = java::BindEntry(env); !status.ok()) [[unlikely]] {
        YSM_LOG(ERROR, "Failed to bind native function: {}"sv,
                status.message());
        return JNI_ERR;
    }

    InitCpuInfo();

    return kJniVersion;
}

void JNI_OnUnload(JavaVM* vm, void*) {}

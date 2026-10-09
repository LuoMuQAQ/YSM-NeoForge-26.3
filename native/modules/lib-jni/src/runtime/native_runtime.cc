#include <java/entry.h>

#include <cstdint>
#include <mutex>
#include <optional>

#include <bitpack.h>
#include <log.h>

#include "cpu.h"
#include "profile.h"

namespace ysm::lib::runtime {
namespace {
constexpr uint8_t kConfigVersion = 1;

YSM_BIT_PACK(PackedJavaConfig, jlong,
             YSM_BIT_FIELD(version, uint8_t, 8),
             YSM_BIT_FIELD(log_level, uint8_t, 3));
YSM_BIT_PACK(PackedNativeConfig, jlong,
             YSM_BIT_FIELD(version, uint8_t, 8),
             YSM_BIT_FIELD(tracy_enabled, uint8_t, 1),
             YSM_BIT_FIELD(debug_logging_enabled, uint8_t, 1));

std::mutex g_initialize_mutex;
std::optional<jlong> g_initialized_java_config;

absl::StatusOr<jlong> NativeConfig() {
    return PackedNativeConfig::pack(kConfigVersion, YSM_ENABLE_TRACY != 0,
                                    YSM_ENABLE_DEBUG_LOG != 0);
}
}  // namespace

YSM_JNI_ENTRY("Lcom/elfmcys/ysm/natives/NativeRuntime;nInitialize(J)J",
              (java_config), jlong{0}) {
    const auto [version, log_level] =
        PackedJavaConfig::unpack(java_config);
    YSM_ASSERT(version == kConfigVersion,
               absl::FailedPreconditionError(
                   "Unsupported Java runtime config version"));
    YSM_DECLARE_OR_RETURN(canonical_java_config,
                          PackedJavaConfig::pack(version, log_level));
    YSM_DECLARE_OR_RETURN(native_config, NativeConfig());

    std::scoped_lock lock(g_initialize_mutex);
    if (g_initialized_java_config.has_value()) {
        YSM_ASSERT(*g_initialized_java_config == canonical_java_config,
                   absl::FailedPreconditionError(
                       "Native runtime was initialized with different config"));
        return native_config;
    }

    YSM_RETURN_IF_ERROR(ConfigureLogLevel(static_cast<int>(log_level)));
    g_initialized_java_config = canonical_java_config;
    YSM_LOG(INFO,
            "Native runtime initialized: log level {}, tracy {}, debug logging {}",
            LogLevelName(static_cast<LogLevel>(log_level)),
            YSM_ENABLE_TRACY != 0, YSM_ENABLE_DEBUG_LOG != 0);
    return native_config;
}
}  // namespace ysm::lib::runtime

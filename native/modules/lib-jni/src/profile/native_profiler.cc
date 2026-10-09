#include <java/entry.h>

#include <cstdint>

#include "java/opaque_ptr.h"
#include "java/string.h"
#include "profile.h"

namespace ysm::lib::profile {
namespace {
constexpr char kFrameName[] = "YSM/Frame/GameRenderer.render";
}  // namespace

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/NativeProfiler;nCreateSourceLocation(Ljava/lang/"
    "String;Ljava/lang/String;Ljava/lang/String;I)J",
    (name, function, file, line), jlong{0}) {
    YSM_ASSERT(name && function && file,
               absl::InvalidArgumentError("Profile source location is null"));
    YSM_ASSERT(line >= 0,
               absl::InvalidArgumentError("Profile source line is negative"));
    return java::MakeOpaquePtr<ysm::profile::SourceLocation>(
        java::StrToU8(env, name), java::StrToU8(env, function),
        java::StrToU8(env, file), static_cast<uint32_t>(line));
}

YSM_JNI_ENTRY("Lcom/elfmcys/ysm/natives/NativeProfiler;nBeginZone(J)J",
              (source_location_handle), jlong{0}) {
    YSM_DECLARE_OR_RETURN(source_location,
                          java::CastOpaquePtr<ysm::profile::SourceLocation>(
                              source_location_handle));
    return static_cast<jlong>(YSM_PROFILE_ZONE_BEGIN(*source_location));
}

YSM_JNI_ENTRY("Lcom/elfmcys/ysm/natives/NativeProfiler;nEndZone(J)V",
              (token)) {
    YSM_ASSERT(YSM_PROFILE_ZONE_END(static_cast<uint64_t>(token)),
               absl::InvalidArgumentError("Invalid Java profile zone token"));
    return OkStatus();
}

YSM_JNI_ENTRY("Lcom/elfmcys/ysm/natives/NativeProfiler;nBeginFrame()J", (),
              jlong{0}) {
    return static_cast<jlong>(YSM_PROFILE_FRAME_BEGIN(kFrameName));
}

YSM_JNI_ENTRY("Lcom/elfmcys/ysm/natives/NativeProfiler;nEndFrame()V", ()) {
    YSM_PROFILE_FRAME_END(kFrameName);
    return OkStatus();
}
}  // namespace ysm::lib::profile

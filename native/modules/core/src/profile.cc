#include "profile.h"

#if YSM_ENABLE_TRACY
#include <tracy/TracyC.h>
#endif

namespace ysm::profile {
uint64_t BeginZone(const SourceLocation& source_location) noexcept {
#if YSM_ENABLE_TRACY
    const auto context =
        ___tracy_emit_zone_begin(&source_location.data_, true);
    if (context.active == 0) {
        return 0;
    }
    return (static_cast<uint64_t>(context.id) << 1) | 1;
#else
    static_cast<void>(source_location);
    return 0;
#endif
}

bool EndZone(uint64_t token) noexcept {
#if YSM_ENABLE_TRACY
    if (token == 0) {
        return true;
    }
    if ((token & 1) == 0 || (token >> 33) != 0) [[unlikely]] {
        return false;
    }
    const TracyCZoneCtx context{
        .id = static_cast<uint32_t>(token >> 1),
        .active = 1,
    };
    ___tracy_emit_zone_end(context);
#else
    static_cast<void>(token);
#endif
    return true;
}

bool BeginFrame(const char* name) noexcept {
#if YSM_ENABLE_TRACY
    if (!TracyCIsConnected) {
        return false;
    }
    TracyCFrameMarkStart(name);
    return true;
#else
    static_cast<void>(name);
    return false;
#endif
}

void EndFrame(const char* name) noexcept {
#if YSM_ENABLE_TRACY
    TracyCFrameMarkEnd(name);
#else
    static_cast<void>(name);
#endif
}
}  // namespace ysm::profile

#pragma once

#include <cstdint>
#include <string>
#include <utility>

#ifndef YSM_ENABLE_TRACY
#define YSM_ENABLE_TRACY 0
#endif

#if YSM_ENABLE_TRACY
#include <tracy/TracyC.h>
#include <tracy/Tracy.hpp>
#endif

namespace ysm::profile {
class SourceLocation final {
   public:
#if YSM_ENABLE_TRACY
    SourceLocation(std::string name, std::string function, std::string file,
                   uint32_t line, uint32_t color = 0) noexcept
        : name_(std::move(name)),
          function_(std::move(function)),
          file_(std::move(file)),
          data_{name_.c_str(), function_.c_str(), file_.c_str(), line, color} {}
#else
    SourceLocation(std::string, std::string, std::string, uint32_t,
                   uint32_t = 0) noexcept {}
#endif

    SourceLocation(const SourceLocation&) = delete;
    SourceLocation& operator=(const SourceLocation&) = delete;
    SourceLocation(SourceLocation&&) = delete;
    SourceLocation& operator=(SourceLocation&&) = delete;

   private:
    friend uint64_t BeginZone(const SourceLocation&) noexcept;

#if YSM_ENABLE_TRACY
    std::string name_;
    std::string function_;
    std::string file_;
    ___tracy_source_location_data data_;
#endif
};

[[nodiscard]] uint64_t BeginZone(const SourceLocation& source_location) noexcept;
[[nodiscard]] bool EndZone(uint64_t token) noexcept;
[[nodiscard]] bool BeginFrame(const char* name) noexcept;
void EndFrame(const char* name) noexcept;
}  // namespace ysm::profile

#if YSM_ENABLE_TRACY
#define YSM_PROFILE_ZONE(name) ZoneScopedN(name)
#define YSM_PROFILE_VALUE(value) ZoneValue(value)
#define YSM_PROFILE_ZONE_BEGIN(source_location) \
    (::ysm::profile::BeginZone(source_location))
#define YSM_PROFILE_ZONE_END(token) (::ysm::profile::EndZone(token))
#define YSM_PROFILE_FRAME_BEGIN(name) (::ysm::profile::BeginFrame(name))
#define YSM_PROFILE_FRAME_END(name) (::ysm::profile::EndFrame(name))
#else
#define YSM_PROFILE_ZONE(name) (static_cast<void>(sizeof(name)))
#define YSM_PROFILE_VALUE(value) (static_cast<void>(sizeof(value)))
#define YSM_PROFILE_ZONE_BEGIN(source_location) \
    (static_cast<void>(sizeof(source_location)), uint64_t{0})
#define YSM_PROFILE_ZONE_END(token) \
    (static_cast<void>(sizeof(token)), true)
#define YSM_PROFILE_FRAME_BEGIN(name) \
    (static_cast<void>(sizeof(name)), false)
#define YSM_PROFILE_FRAME_END(name) (static_cast<void>(sizeof(name)))
#endif

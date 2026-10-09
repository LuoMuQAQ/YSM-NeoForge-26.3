#pragma once

#include <cstddef>
#include <format>
#include <string>
#include <string_view>

#include <absl/log/absl_log.h>
#include <absl/log/log_sink.h>
#include <absl/status/status.h>

#define YSM_LOG(severity, ...) \
    ABSL_LOG(severity) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)
#define YSM_PLOG(severity, ...) \
    ABSL_PLOG(severity) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)

#define YSM_LOG_IF(severity, condition, ...) \
    ABSL_LOG_IF(severity, condition) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)

#define YSM_PLOG_IF(severity, condition, ...) \
    ABSL_PLOG_IF(severity, condition) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)

#ifndef YSM_ENABLE_DEBUG_LOG
#ifdef YSM_DEBUG
#define YSM_ENABLE_DEBUG_LOG 1
#else
#define YSM_ENABLE_DEBUG_LOG 0
#endif
#endif

#if YSM_ENABLE_DEBUG_LOG
#define YSM_LOG_DEBUG(...) \
    ABSL_VLOG(1) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)
#define YSM_LOG_TRACE(...) \
    ABSL_VLOG(2) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)
#define YSM_LOG_DEBUG_IF(condition, ...) \
    ABSL_VLOG_IF(1, condition) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)
#define YSM_LOG_DEBUG_EVERY_N_SEC(n_seconds, ...) \
    ABSL_VLOG_EVERY_N_SEC(1, n_seconds) \
        .ToSinkOnly(::ysm::log_internal::GetLogSink()) \
        << std::format(__VA_ARGS__)
#define YSM_LOG_DEBUG_ENABLED() ABSL_VLOG_IS_ON(1)
#define YSM_LOG_TRACE_ENABLED() ABSL_VLOG_IS_ON(2)
#else
#define YSM_LOG_DEBUG(...) \
    static_cast<void>(sizeof(std::format(__VA_ARGS__)))
#define YSM_LOG_TRACE(...) \
    static_cast<void>(sizeof(std::format(__VA_ARGS__)))
#define YSM_LOG_DEBUG_IF(condition, ...)       \
    do {                                       \
        static_cast<void>(sizeof(condition));  \
        YSM_LOG_DEBUG(__VA_ARGS__);            \
    } while (false)
#define YSM_LOG_DEBUG_EVERY_N_SEC(n_seconds, ...) \
    do {                                          \
        static_cast<void>(sizeof(n_seconds));     \
        YSM_LOG_DEBUG(__VA_ARGS__);               \
    } while (false)
#define YSM_LOG_DEBUG_ENABLED() false
#define YSM_LOG_TRACE_ENABLED() false
#endif

namespace ysm {
namespace log_internal {
absl::LogSink* GetLogSink();
}

class [[nodiscard]] LoggingScope final {
public:
    explicit LoggingScope(std::string name, bool no_source = false);
    explicit LoggingScope(std::string_view name, bool no_source = false);
    explicit LoggingScope(const char* name, bool no_source = false);
    ~LoggingScope() noexcept;

    LoggingScope(const LoggingScope&) = delete;
    LoggingScope& operator=(const LoggingScope&) = delete;
    LoggingScope(LoggingScope&&) = delete;
    LoggingScope& operator=(LoggingScope&&) = delete;

private:
    std::string owned_name_;
    std::string_view name_;
    std::size_t depth_;
};

enum class LogLevel : int {
    kAll,
    kTrace,
    kDebug,
    kInfo,
    kWarning,
    kError,
    kFatal,
    kOff,
};

void InitializeLogging();
absl::Status ConfigureLogLevel(int level);
std::string_view LogLevelName(LogLevel level);
}  // namespace ysm

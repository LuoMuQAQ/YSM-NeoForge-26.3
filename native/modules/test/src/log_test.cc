#include <array>
#include <regex>

#include <absl/base/log_severity.h>
#include <absl/log/globals.h>
#include <gtest/gtest.h>

#include <log.h>

namespace ysm {
namespace {
struct LogLevelExpectation {
    LogLevel level;
    absl::LogSeverityAtLeast severity;
    bool debug_enabled;
    bool trace_enabled;
};

TEST(LogTest, MapsSupportedLevelsToAbseilConfiguration) {
    constexpr std::array kExpectations{
        LogLevelExpectation{LogLevel::kAll,
                            absl::LogSeverityAtLeast::kInfo, true, true},
        LogLevelExpectation{LogLevel::kTrace,
                            absl::LogSeverityAtLeast::kInfo, true, true},
        LogLevelExpectation{LogLevel::kDebug,
                            absl::LogSeverityAtLeast::kInfo, true, false},
        LogLevelExpectation{LogLevel::kInfo,
                            absl::LogSeverityAtLeast::kInfo, false, false},
        LogLevelExpectation{LogLevel::kWarning,
                            absl::LogSeverityAtLeast::kWarning, false, false},
        LogLevelExpectation{LogLevel::kError,
                            absl::LogSeverityAtLeast::kError, false, false},
        LogLevelExpectation{LogLevel::kFatal,
                            absl::LogSeverityAtLeast::kFatal, false, false},
        LogLevelExpectation{LogLevel::kOff,
                            absl::LogSeverityAtLeast::kInfinity, false, false},
    };

    for (const auto& expectation : kExpectations) {
        ASSERT_TRUE(
            ConfigureLogLevel(static_cast<int>(expectation.level)).ok());
        EXPECT_EQ(absl::MinLogLevel(), expectation.severity);
        EXPECT_EQ(absl::StderrThreshold(), expectation.severity);
        EXPECT_EQ(YSM_LOG_DEBUG_ENABLED(), expectation.debug_enabled);
        EXPECT_EQ(YSM_LOG_TRACE_ENABLED(), expectation.trace_enabled);
    }
}

TEST(LogTest, RejectsInvalidLevelWithoutChangingConfiguration) {
    ASSERT_TRUE(ConfigureLogLevel(static_cast<int>(LogLevel::kDebug)).ok());

    auto status = ConfigureLogLevel(100);

    EXPECT_EQ(status.code(), absl::StatusCode::kInvalidArgument);
    EXPECT_EQ(absl::MinLogLevel(), absl::LogSeverityAtLeast::kInfo);
    EXPECT_TRUE(YSM_LOG_DEBUG_ENABLED());
}

TEST(LogTest, DoesNotEvaluateDisabledDebugArguments) {
    ASSERT_TRUE(ConfigureLogLevel(static_cast<int>(LogLevel::kInfo)).ok());
    int value = 0;

    YSM_LOG_DEBUG("Side effect: {}", ++value);

    EXPECT_EQ(value, 0);
}
}  // namespace
}  // namespace ysm

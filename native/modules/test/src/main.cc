#include <gtest/gtest.h>

#include <log.h>

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ysm::InitializeLogging();
    auto status =
        ysm::ConfigureLogLevel(static_cast<int>(ysm::LogLevel::kDebug));
    if (!status.ok()) {
        YSM_LOG(ERROR, "Failed to enable native test logging: {}",
                status.message());
        return 1;
    }
    YSM_LOG_DEBUG("Native unit tests started with Debug logging enabled");
    return RUN_ALL_TESTS();
}

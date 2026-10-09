#include <gtest/gtest.h>

#include <array>
#include <cstdint>

#include <v3/codec/dirty_zstd.h>

namespace ysm::legacy::v3::codec {
namespace {
TEST(LegacyDirtyZstdTest, NormalizesEveryHistoricalBlockType) {
    struct Vector {
        std::array<std::uint8_t, 3> dirty;
        std::array<std::uint8_t, 3> standard;
        std::size_t payload_size;
        bool last;
    };

    constexpr std::array vectors{
        Vector{{0xE0, 0xE9, 0xD4}, {0x01, 0x00, 0x00}, 0, true},
        Vector{{0x21, 0xAC, 0xF7}, {0x2A, 0x1A, 0x09}, 1, false},
        Vector{{0x81, 0x16, 0x2B}, {0xFD, 0xFF, 0x0F}, 0x1FFFF, true},
        Vector{{0x61, 0x43, 0x7E}, {0x50, 0x55, 0x0D}, 0x1AAAA, false},
    };

    for (const auto& vector : vectors) {
        const auto result = NormalizeDirtyZstdBlockHeader(vector.dirty);
        ASSERT_TRUE(result.ok()) << result.status();
        EXPECT_EQ(result->bytes, vector.standard);
        EXPECT_EQ(result->payload_size, vector.payload_size);
        EXPECT_EQ(result->last, vector.last);
    }
}

TEST(LegacyDirtyZstdTest, RejectsReservedBlockType) {
    constexpr std::array<std::uint8_t, 3> kReserved{0x40, 0xE9, 0xD4};
    const auto result = NormalizeDirtyZstdBlockHeader(kReserved);
    ASSERT_FALSE(result.ok());
    EXPECT_EQ(result.status().code(), absl::StatusCode::kDataLoss);
}
}  // namespace
}  // namespace ysm::legacy::v3::codec

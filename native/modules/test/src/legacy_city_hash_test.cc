#include <gtest/gtest.h>

#include <array>
#include <cstdint>
#include <span>
#include <vector>

#include <v3/codec/modified_city_hash.h>

namespace ysm::legacy::v3::codec {
namespace {
struct HistoricalVector {
    std::size_t length;
    std::uint64_t hash;
};

constexpr std::uint64_t kSeed = 11409194399050398761ULL;
constexpr std::array kVectors{
    HistoricalVector{0, 8042932239147081391ULL},
    HistoricalVector{1, 14662329999069294316ULL},
    HistoricalVector{3, 803572144813152280ULL},
    HistoricalVector{4, 15713305407892776351ULL},
    HistoricalVector{7, 13844931084561017689ULL},
    HistoricalVector{8, 16340748013193004988ULL},
    HistoricalVector{16, 17737655933971482886ULL},
    HistoricalVector{17, 10788145213889431210ULL},
    HistoricalVector{32, 17947090291158778479ULL},
    HistoricalVector{33, 1688872210173529929ULL},
    HistoricalVector{63, 9538431262500756175ULL},
    HistoricalVector{64, 4565498937205348967ULL},
    HistoricalVector{65, 11260580286896762533ULL},
    HistoricalVector{127, 15616308421445669944ULL},
    HistoricalVector{128, 10089192292417589676ULL},
    HistoricalVector{1024, 10644972106623026718ULL},
    HistoricalVector{8191, 7064877764580148889ULL},
};

std::vector<std::uint8_t> Input(std::size_t length) {
    std::vector<std::uint8_t> bytes(length);
    for (std::size_t index = 0; index < length; ++index) {
        bytes[index] = static_cast<std::uint8_t>(index * 131U + 17U);
    }
    return bytes;
}

TEST(LegacyCityHashTest, MatchesFrozenHistoricalVectors) {
    for (const auto& vector : kVectors) {
        const auto bytes = Input(vector.length);
        EXPECT_EQ(ModifiedCityHash64WithSeed(bytes, kSeed), vector.hash)
            << vector.length;
    }
}

}  // namespace
}  // namespace ysm::legacy::v3::codec

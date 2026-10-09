#include <gtest/gtest.h>

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <random>
#include <span>
#include <vector>

#include <v3/codec/deobfuscator.h>
#include <v3/codec/modified_city_hash.h>

namespace ysm::legacy::v3::codec {
namespace {
constexpr std::uint64_t kXorSeed = 14994677486147417473ULL;

std::vector<Byte> Obfuscate(BufferViewR bytes, BufferFixedViewR<56> password) {
    std::mt19937_64 generator(ModifiedCityHash64WithSeed(password, kXorSeed));
    auto result = std::vector<Byte>(bytes.begin(), bytes.end());
    std::uint64_t random = 0;
    std::uint8_t available = 0;
    for (auto& value : result) {
        if (available == 0) {
            random = generator();
            available = 8;
        }
        value ^= static_cast<Byte>(random);
        random >>= 8U;
        --available;
    }
    return result;
}

std::vector<Byte> DeobfuscateInChunks(
    BufferViewR obfuscated, BufferFixedViewR<56> password,
    std::span<const std::size_t> chunk_sizes) {
    Deobfuscator deobfuscator(password);
    std::vector<Byte> result;
    std::size_t offset = 0;
    std::size_t chunk_index = 0;
    while (offset != obfuscated.size()) {
        const auto requested = chunk_sizes[chunk_index++ % chunk_sizes.size()];
        const auto size = std::min(requested, obfuscated.size() - offset);
        auto chunk = std::vector<Byte>(obfuscated.begin() + offset,
                                       obfuscated.begin() + offset + size);
        const auto payload = deobfuscator.Apply(chunk);
        EXPECT_GE(payload.data(), chunk.data());
        EXPECT_LE(payload.data(), chunk.data() + chunk.size());
        result.insert(result.end(), payload.begin(), payload.end());
        offset += size;
    }
    return result;
}

TEST(LegacyDeobfuscatorTest, PreservesStateAcrossArbitraryChunkBoundaries) {
    BufferFixed<56> password{};
    for (std::size_t index = 0; index < password.size(); ++index) {
        password[index] = static_cast<Byte>(index * 19U + 5U);
    }

    constexpr std::size_t kSaltSize = 13;
    std::vector<Byte> plaintext{static_cast<Byte>(kSaltSize), 0};
    for (std::size_t index = 0; index < kSaltSize; ++index) {
        plaintext.push_back(static_cast<Byte>(0xa0U + index));
    }
    std::vector<Byte> expected(257);
    for (std::size_t index = 0; index < expected.size(); ++index) {
        expected[index] = static_cast<Byte>(index * 73U + 11U);
    }
    plaintext.insert(plaintext.end(), expected.begin(), expected.end());
    const auto obfuscated = Obfuscate(plaintext, password);

    constexpr std::array<std::size_t, 1> kByteChunks{1};
    constexpr std::array<std::size_t, 6> kMixedChunks{1, 2, 7, 8, 31, 64};
    constexpr std::array<std::size_t, 1> kSingleChunk{1024};
    EXPECT_EQ(DeobfuscateInChunks(obfuscated, password, kByteChunks), expected);
    EXPECT_EQ(DeobfuscateInChunks(obfuscated, password, kMixedChunks),
              expected);
    EXPECT_EQ(DeobfuscateInChunks(obfuscated, password, kSingleChunk),
              expected);
}

TEST(LegacyDeobfuscatorTest, MatchesScalarReferenceAcrossBulkAlignments) {
    BufferFixed<56> password{};
    for (std::size_t index = 0; index < password.size(); ++index) {
        password[index] = static_cast<Byte>(index * 29U + 17U);
    }

    constexpr std::array<std::size_t, 6> kSaltSizes{0, 1, 7, 8, 9, 1023};
    constexpr std::array<std::size_t, 8> kChunkSizes{2,  7,  8,  9,
                                                     15, 16, 17, 8128};
    for (const auto salt_size : kSaltSizes) {
        const auto encoded_salt =
            static_cast<std::uint16_t>(salt_size | 0xa400U);
        std::vector<Byte> plaintext{
            static_cast<Byte>(encoded_salt),
            static_cast<Byte>(encoded_salt >> 8U),
        };
        for (std::size_t index = 0; index < salt_size; ++index) {
            plaintext.push_back(static_cast<Byte>(index * 31U + 3U));
        }
        std::vector<Byte> expected(8192 + 13);
        for (std::size_t index = 0; index < expected.size(); ++index) {
            expected[index] = static_cast<Byte>(index * 73U + 11U);
        }
        plaintext.insert(plaintext.end(), expected.begin(), expected.end());
        const auto obfuscated = Obfuscate(plaintext, password);

        for (const auto chunk_size : kChunkSizes) {
            const std::array chunks{chunk_size};
            EXPECT_EQ(DeobfuscateInChunks(obfuscated, password, chunks),
                      expected)
                << "salt_size=" << salt_size << " chunk_size=" << chunk_size;
        }
    }
}
}  // namespace
}  // namespace ysm::legacy::v3::codec

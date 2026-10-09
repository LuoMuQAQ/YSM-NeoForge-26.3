#include <gtest/gtest.h>

#include <array>
#include <cstddef>
#include <cstdint>
#include <vector>

#include <cpu.h>

#include <v3/codec/chacha.h>
#include <v3/codec/chacha_internal.h>

namespace ysm::legacy::v3::codec {
namespace {
internal::ChaChaState MakeState(std::uint64_t counter) {
    internal::ChaChaState state{
        0x61707865U, 0x3320646eU, 0x79622d32U, 0x6b206574U,
        0x03020100U, 0x07060504U, 0x0b0a0908U, 0x0f0e0d0cU,
        0x13121110U, 0x17161514U, 0x1b1a1918U, 0x1f1e1d1cU,
        0,           0,           0x43424140U, 0x47464544U};
    state[12] = static_cast<std::uint32_t>(counter);
    state[13] = static_cast<std::uint32_t>(counter >> 32U);
    return state;
}

std::vector<Byte> MakeBytes(std::size_t size, std::uint32_t salt = 0) {
    std::vector<Byte> bytes(size);
    for (std::size_t index = 0; index < bytes.size(); ++index) {
        bytes[index] = static_cast<Byte>((index * 131U + salt * 17U) & 0xffU);
    }
    return bytes;
}

void ExpectMatchesScalar(simd::Type implementation) {
    constexpr std::array<std::uint32_t, 3> kRounds{10, 20, 30};
    constexpr std::array<std::size_t, 14> kSizes{
        0, 1, 63, 64, 65, 255, 256, 257, 511, 512, 513, 767, 768, 1025};
    constexpr std::array<std::uint64_t, 2> kCounters{0x00000000fffffffaULL,
                                                     0xfffffffffffffffaULL};

    for (const auto rounds : kRounds) {
        for (const auto size : kSizes) {
            for (const auto counter : kCounters) {
                auto expected_state = MakeState(counter);
                auto actual_state = expected_state;
                auto expected = MakeBytes(size, rounds);
                auto actual = expected;

                internal::ChaChaXor(expected_state, expected, rounds,
                                    simd::Type::kNone);
                internal::ChaChaXor(actual_state, actual, rounds,
                                    implementation);

                EXPECT_EQ(actual, expected)
                    << "rounds=" << rounds << " size=" << size;
                EXPECT_EQ(actual_state, expected_state)
                    << "rounds=" << rounds << " size=" << size;
            }
        }
    }
}

class SimdTypeGuard final {
   public:
    SimdTypeGuard() : previous_(simd::kSupported) {}

    ~SimdTypeGuard() { simd::Mock(previous_); }

   private:
    simd::Type previous_;
};

#ifdef YSM_X64
TEST(LegacyChaChaTest, SseMatchesScalar) {
    ExpectMatchesScalar(simd::Type::SSE41);
}

TEST(LegacyChaChaTest, Avx2MatchesScalar) {
    SimdTypeGuard guard;
    InitCpuInfo();
    if (simd::kSupported != simd::Type::AVX2 &&
        simd::kSupported != simd::Type::AVX512) {
        GTEST_SKIP() << "AVX2 is unavailable";
    }
    ExpectMatchesScalar(simd::Type::AVX2);
    ExpectMatchesScalar(simd::Type::AVX512);
}
#elif defined(YSM_ARM64)
TEST(LegacyChaChaTest, NeonMatchesScalar) {
    ExpectMatchesScalar(simd::Type::NEON);
}
#endif

TEST(LegacyChaChaTest, DynamicChunksMatchScalar) {
    SimdTypeGuard guard;
    InitCpuInfo();
    const auto implementation = simd::kSupported;

    BufferFixed<kChaChaPasswordSize> password{};
    for (std::size_t index = 0; index < password.size(); ++index) {
        password[index] = static_cast<Byte>(index * 29U + 7U);
    }
    YsmChaCha scalar(password);
    YsmChaCha accelerated(password);

    for (std::uint32_t chunk = 0; chunk < 4; ++chunk) {
        ASSERT_EQ(accelerated.next_chunk_size(), scalar.next_chunk_size());
        auto expected = MakeBytes(scalar.next_chunk_size(), chunk);
        auto actual = expected;

        simd::Mock(simd::Type::kNone);
        scalar.Decrypt(expected, true);
        simd::Mock(implementation);
        accelerated.Decrypt(actual, true);

        EXPECT_EQ(actual, expected) << "chunk=" << chunk;
    }

    auto expected = MakeBytes(777, 9);
    auto actual = expected;
    simd::Mock(simd::Type::kNone);
    scalar.Decrypt(expected, false);
    simd::Mock(implementation);
    accelerated.Decrypt(actual, false);
    EXPECT_EQ(actual, expected);
}
}  // namespace
}  // namespace ysm::legacy::v3::codec

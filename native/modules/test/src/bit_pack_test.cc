#include <bitpack.h>
#include <gtest/gtest.h>

#include <bit>
#include <cstdint>
#include <limits>

namespace {
enum class Mode : int8_t { kNegative = -2, kPositive = 3 };
enum class State : uint16_t { kIdle = 1, kReady = 9 };

YSM_BIT_PACK(TestHeader, uint32_t, YSM_BIT_FIELD(flags, uint8_t, 3),
             YSM_BIT_FIELD(mode, Mode, 3), YSM_BIT_FIELD(delta, int8_t, 5),
             YSM_BIT_FIELD(size, uint32_t, 21));
YSM_BIT_PACK(PartialByte, uint8_t, YSM_BIT_FIELD(value, uint8_t, 3));
YSM_BIT_PACK(FullWord, uint64_t, YSM_BIT_FIELD(value, uint64_t, 64));
YSM_BIT_PACK(SignedFullWord, uint64_t, YSM_BIT_FIELD(value, int64_t, 64));
YSM_BIT_PACK(EnumHeader, uint8_t, YSM_BIT_FIELD(state, State, 4));
YSM_BIT_PACK(SignedStorageHeader, int32_t, YSM_BIT_FIELD(low, uint16_t, 16),
             YSM_BIT_FIELD(high, uint16_t, 16));
YSM_BIT_PACK(IdentifierCollisionHeader, uint16_t,
             YSM_BIT_FIELD(layout, uint8_t, 4),
             YSM_BIT_FIELD(unsigned_storage_type, uint8_t, 4),
             YSM_BIT_FIELD(packed_value, uint8_t, 8));

TEST(BitPackTest, PacksAndUnpacksInDeclarationOrder) {
    const auto packed = TestHeader::pack(5, Mode::kNegative, -7, 0x1FFFFF);
    ASSERT_TRUE(packed.ok()) << packed.status();
    EXPECT_EQ(*packed, 0xFFFFFE75u);

    const auto [flags, mode, delta, size] = TestHeader::unpack(*packed);
    EXPECT_EQ(flags, 5);
    EXPECT_EQ(mode, Mode::kNegative);
    EXPECT_EQ(delta, -7);
    EXPECT_EQ(size, 0x1FFFFFu);
}

TEST(BitPackTest, RejectsOutOfRangeAndInvalidEnum) {
    EXPECT_FALSE(TestHeader::pack(8, Mode::kPositive, -16, 0x1FFFFF).ok());
    EXPECT_FALSE(TestHeader::pack(7, Mode::kPositive, -17, 0x1FFFFF).ok());
    EXPECT_FALSE(TestHeader::pack(7, Mode::kPositive, -16, 0x200000).ok());
    EXPECT_FALSE(TestHeader::pack(7, static_cast<Mode>(1), -16, 0x1FFFFF).ok());

    const auto boolean = PartialByte::pack(true);
    ASSERT_TRUE(boolean.ok()) << boolean.status();
    EXPECT_EQ(*boolean, 1);
    EXPECT_FALSE(PartialByte::pack(0x103).ok());
}

TEST(BitPackTest, HandlesFullWidthAndUncheckedUnpack) {
    const auto full = FullWord::pack(std::numeric_limits<uint64_t>::max());
    ASSERT_TRUE(full.ok()) << full.status();
    EXPECT_EQ(*full, std::numeric_limits<uint64_t>::max());
    EXPECT_FALSE(FullWord::pack(-1).ok());

    const auto signed_full = SignedFullWord::pack(-1);
    ASSERT_TRUE(signed_full.ok()) << signed_full.status();
    EXPECT_EQ(*signed_full, std::numeric_limits<uint64_t>::max());
    const auto [signed_value] = SignedFullWord::unpack(*signed_full);
    EXPECT_EQ(signed_value, -1);

    const auto [masked] = PartialByte::unpack(0xFF);
    EXPECT_EQ(masked, 7);
}

TEST(BitPackTest, SupportsSignedStorage) {
    const auto packed = SignedStorageHeader::pack(0x5678, 0xF234);
    ASSERT_TRUE(packed.ok()) << packed.status();
    EXPECT_EQ(std::bit_cast<uint32_t>(*packed), 0xF2345678u);

    const auto [low, high] = SignedStorageHeader::unpack(*packed);
    EXPECT_EQ(low, 0x5678);
    EXPECT_EQ(high, 0xF234);
}

TEST(BitPackTest, AvoidsGeneratedIdentifierCollisions) {
    const auto packed = IdentifierCollisionHeader::pack(0xA, 0xB, 0xCD);
    ASSERT_TRUE(packed.ok()) << packed.status();
    EXPECT_EQ(*packed, 0xCDBAu);

    const auto [layout, unsigned_storage_type, packed_value] =
        IdentifierCollisionHeader::unpack(*packed);
    EXPECT_EQ(layout, 0xA);
    EXPECT_EQ(unsigned_storage_type, 0xB);
    EXPECT_EQ(packed_value, 0xCD);
}

TEST(BitPackTest, ValidatesHeterogeneousEnums) {
    EXPECT_TRUE(ysm::EnumValidate(Mode::kPositive, State::kReady).ok());
    EXPECT_FALSE(
        ysm::EnumValidate(Mode::kPositive, static_cast<State>(2)).ok());

    const ysm::bitpack::EnumValue<Mode> mode(Mode::kPositive);
    const ysm::bitpack::EnumValue<State> valid_state(State::kReady);
    const ysm::bitpack::EnumValue<State> invalid_state(static_cast<State>(2));
    EXPECT_TRUE(ysm::bitpack::EnumValidate(mode, valid_state).ok());
    EXPECT_EQ(ysm::bitpack::EnumValidate(mode, invalid_state).code(),
              absl::StatusCode::kInvalidArgument);

    const auto packed = EnumHeader::pack(State::kReady);
    ASSERT_TRUE(packed.ok()) << packed.status();
    const auto [state] = EnumHeader::unpack(*packed);
    EXPECT_TRUE(state.Valid());
    EXPECT_TRUE(state.Status().ok());
    EXPECT_EQ(state, State::kReady);
    EXPECT_EQ(state.Value(), State::kReady);
    EXPECT_EQ(state.ValueOr(State::kIdle), State::kReady);

    const auto [invalid] = EnumHeader::unpack(2);
    EXPECT_FALSE(invalid.Valid());
    EXPECT_EQ(invalid.Status().code(), absl::StatusCode::kInvalidArgument);
    EXPECT_EQ(invalid.ValueOr(State::kIdle), State::kIdle);
    EXPECT_THROW(static_cast<void>(invalid.Value()), std::invalid_argument);
    EXPECT_THROW(static_cast<void>(static_cast<State>(invalid)),
                 std::invalid_argument);
}
}  // namespace

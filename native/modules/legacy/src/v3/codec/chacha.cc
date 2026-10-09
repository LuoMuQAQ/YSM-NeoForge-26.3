// SIMD block layouts are adapted from Crypto++ 8.9 chacha_simd.cpp and
// chacha_avx.cpp, written and placed in the public domain by Jack Lloyd and
// Jeffrey Walton.

#include "chacha.h"

#include "chacha_internal.h"

#include <algorithm>
#include <bit>

#include "modified_city_hash.h"

namespace ysm::legacy::v3::codec {
namespace {
constexpr std::uint64_t kParameterHashSeed = 11973692811708214211ULL;
constexpr std::size_t kBlockSize = 64;
constexpr std::size_t kSseBlockCount = 4;
constexpr std::size_t kAvx2BlockCount = 8;

std::uint32_t LoadLe32(BufferViewR bytes) noexcept {
    return static_cast<std::uint32_t>(bytes[0]) |
           static_cast<std::uint32_t>(bytes[1]) << 8U |
           static_cast<std::uint32_t>(bytes[2]) << 16U |
           static_cast<std::uint32_t>(bytes[3]) << 24U;
}

void StoreLe32(BufferView bytes, std::uint32_t value) noexcept {
    for (std::size_t index = 0; index < sizeof(value); ++index) {
        bytes[index] = static_cast<Byte>(value >> (index * 8U));
    }
}

void QuarterRound(internal::ChaChaState& state, int a, int b, int c,
                  int d) noexcept {
    state[a] += state[b];
    state[d] = std::rotl(state[d] ^ state[a], 16);
    state[c] += state[d];
    state[b] = std::rotl(state[b] ^ state[c], 12);
    state[a] += state[b];
    state[d] = std::rotl(state[d] ^ state[a], 8);
    state[c] += state[d];
    state[b] = std::rotl(state[b] ^ state[c], 7);
}

internal::ChaChaState ChaChaRounds(internal::ChaChaState state,
                                   std::uint32_t rounds) noexcept {
    for (std::uint32_t round = 0; round < rounds; round += 2) {
        QuarterRound(state, 0, 4, 8, 12);
        QuarterRound(state, 1, 5, 9, 13);
        QuarterRound(state, 2, 6, 10, 14);
        QuarterRound(state, 3, 7, 11, 15);
        QuarterRound(state, 0, 5, 10, 15);
        QuarterRound(state, 1, 6, 11, 12);
        QuarterRound(state, 2, 7, 8, 13);
        QuarterRound(state, 3, 4, 9, 14);
    }
    return state;
}

void AdvanceCounter(internal::ChaChaState& state,
                    std::uint64_t count) noexcept {
    auto counter = static_cast<std::uint64_t>(state[12]) |
                   static_cast<std::uint64_t>(state[13]) << 32U;
    counter += count;
    state[12] = static_cast<std::uint32_t>(counter);
    state[13] = static_cast<std::uint32_t>(counter >> 32U);
}

void ChaChaXorScalar(internal::ChaChaState& state, BufferView bytes,
                     std::uint32_t rounds) noexcept {
    for (std::size_t offset = 0; offset < bytes.size(); offset += kBlockSize) {
        auto working = ChaChaRounds(state, rounds);
        for (std::size_t index = 0; index < working.size(); ++index) {
            working[index] += state[index];
        }
        const auto count =
            std::min<std::size_t>(kBlockSize, bytes.size() - offset);
        for (std::size_t index = 0; index < count; ++index) {
            bytes[offset + index] ^=
                static_cast<Byte>(working[index / 4] >> ((index % 4) * 8U));
        }
        AdvanceCounter(state, 1);
    }
}
}  // namespace

void internal::ChaChaXor(ChaChaState& state, BufferView bytes,
                         std::uint32_t rounds,
                         simd::Type implementation) noexcept {
    std::size_t offset = 0;
#ifdef YSM_X64
    if (implementation == simd::Type::AVX2 ||
        implementation == simd::Type::AVX512) {
        while (bytes.size() - offset >= kAvx2BlockCount * kBlockSize) {
            ChaChaXor8Avx2(state, bytes.data() + offset, rounds);
            AdvanceCounter(state, kAvx2BlockCount);
            offset += kAvx2BlockCount * kBlockSize;
        }
    }
    if (implementation != simd::Type::kNone) {
        while (bytes.size() - offset >= kSseBlockCount * kBlockSize) {
            ChaChaXor4Sse(state, bytes.data() + offset, rounds);
            AdvanceCounter(state, kSseBlockCount);
            offset += kSseBlockCount * kBlockSize;
        }
    }
#elif defined(YSM_ARM64)
    if (implementation == simd::Type::NEON) {
        while (bytes.size() - offset >= kSseBlockCount * kBlockSize) {
            ChaChaXor4Neon(state, bytes.data() + offset, rounds);
            AdvanceCounter(state, kSseBlockCount);
            offset += kSseBlockCount * kBlockSize;
        }
    }
#endif
    ChaChaXorScalar(state, bytes.subspan(offset), rounds);
}

class YsmChaCha::Impl {
   public:
    explicit Impl(BufferFixedViewR<kChaChaPasswordSize> password) {
        const auto parameters =
            ModifiedCityHash64WithSeed(password, kParameterHashSeed);
        SetParameters(parameters);
        internal::ChaChaState hchacha{0x61707865U, 0x3320646eU, 0x79622d32U,
                                      0x6b206574U};
        for (std::size_t index = 0; index < 8; ++index) {
            hchacha[index + 4] = LoadLe32(Slice(password, index * 4, 4));
        }
        for (std::size_t index = 0; index < 4; ++index) {
            hchacha[index + 12] = LoadLe32(Slice(password, 32 + index * 4, 4));
        }
        hchacha = ChaChaRounds(hchacha, rounds);

        state = {0x61707865U,
                 0x3320646eU,
                 0x79622d32U,
                 0x6b206574U,
                 hchacha[0],
                 hchacha[1],
                 hchacha[2],
                 hchacha[3],
                 hchacha[12],
                 hchacha[13],
                 hchacha[14],
                 hchacha[15],
                 0,
                 0,
                 LoadLe32(Slice(password, 48, 4)),
                 LoadLe32(Slice(password, 52, 4))};
    }

    [[nodiscard]] std::size_t next_chunk_size() const noexcept {
        return block_count * kBlockSize;
    }

    void Decrypt(BufferView bytes, bool complete_chunk) {
        internal::ChaChaXor(state, bytes, rounds, simd::kSupported);
        if (!complete_chunk) {
            return;
        }
        const auto parameters =
            ModifiedCityHash64WithSeed(bytes, kParameterHashSeed);
        SetParameters(parameters);
        BufferFixed<48> mutable_state{};
        for (std::size_t index = 0; index < 12; ++index) {
            StoreLe32(Slice(mutable_state, index * 4, 4), state[index + 4]);
        }
        for (std::size_t index = 0; index < mutable_state.size(); ++index) {
            // The historical writer mutated this scratch representation rather
            // than rotating the live ChaCha state words.
            mutable_state[index] ^=
                static_cast<Byte>(parameters >> ((index % 8) * 8U));
        }
        for (std::size_t index = 0; index < 12; ++index) {
            state[index + 4] = LoadLe32(Slice(mutable_state, index * 4, 4));
        }
    }

   private:
    void SetParameters(std::uint64_t hash) noexcept {
        block_count = static_cast<std::size_t>(hash % 64U) + 64;
        rounds = static_cast<std::uint32_t>((hash % 3U) + 1U) * 10U;
    }

    internal::ChaChaState state{};
    std::size_t block_count{};
    std::uint32_t rounds{};
};

YsmChaCha::YsmChaCha(BufferFixedViewR<kChaChaPasswordSize> password)
    : YSM_PIMPL_CONSTRUCT(password) {}

YsmChaCha::YsmChaCha(const YsmChaCha&) = default;
YsmChaCha& YsmChaCha::operator=(const YsmChaCha&) = default;
YsmChaCha::YsmChaCha(YsmChaCha&&) noexcept = default;
YsmChaCha& YsmChaCha::operator=(YsmChaCha&&) noexcept = default;
YsmChaCha::~YsmChaCha() = default;

std::size_t YsmChaCha::next_chunk_size() const noexcept {
    return Pimpl().next_chunk_size();
}

void YsmChaCha::Decrypt(BufferView bytes, bool complete_chunk) {
    Pimpl().Decrypt(bytes, complete_chunk);
}

YSM_PIMPL_DEFINITION(YsmChaCha)
}  // namespace ysm::legacy::v3::codec

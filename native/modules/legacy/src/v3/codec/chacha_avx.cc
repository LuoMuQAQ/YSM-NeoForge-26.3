// Adapted from Crypto++ 8.9 chacha_avx.cpp, written and placed in the public
// domain by Jack Lloyd and Jeffrey Walton.

#include "chacha_internal.h"

#ifdef YSM_X64

#include <immintrin.h>

#include <array>

namespace ysm::legacy::v3::codec::internal {
namespace {
struct alignas(32) Rows {
    __m256i row0;
    __m256i row1;
    __m256i row2;
    __m256i row3;
};

template <unsigned int kBits>
YSM_TARGET_AVX2 inline __m256i RotateLeft(__m256i value) noexcept {
    return _mm256_or_si256(_mm256_slli_epi32(value, kBits),
                           _mm256_srli_epi32(value, 32 - kBits));
}

template <>
YSM_TARGET_AVX2 inline __m256i RotateLeft<8>(__m256i value) noexcept {
    const auto mask =
        _mm256_set_epi8(14, 13, 12, 15, 10, 9, 8, 11, 6, 5, 4, 7, 2, 1, 0, 3,
                        14, 13, 12, 15, 10, 9, 8, 11, 6, 5, 4, 7, 2, 1, 0, 3);
    return _mm256_shuffle_epi8(value, mask);
}

template <>
YSM_TARGET_AVX2 inline __m256i RotateLeft<16>(__m256i value) noexcept {
    const auto mask =
        _mm256_set_epi8(13, 12, 15, 14, 9, 8, 11, 10, 5, 4, 7, 6, 1, 0, 3, 2,
                        13, 12, 15, 14, 9, 8, 11, 10, 5, 4, 7, 6, 1, 0, 3, 2);
    return _mm256_shuffle_epi8(value, mask);
}

YSM_TARGET_AVX2 inline void DoubleRound(Rows& rows) noexcept {
    rows.row0 = _mm256_add_epi32(rows.row0, rows.row1);
    rows.row3 = RotateLeft<16>(_mm256_xor_si256(rows.row3, rows.row0));
    rows.row2 = _mm256_add_epi32(rows.row2, rows.row3);
    rows.row1 = RotateLeft<12>(_mm256_xor_si256(rows.row1, rows.row2));
    rows.row0 = _mm256_add_epi32(rows.row0, rows.row1);
    rows.row3 = RotateLeft<8>(_mm256_xor_si256(rows.row3, rows.row0));
    rows.row2 = _mm256_add_epi32(rows.row2, rows.row3);
    rows.row1 = RotateLeft<7>(_mm256_xor_si256(rows.row1, rows.row2));

    rows.row1 = _mm256_shuffle_epi32(rows.row1, _MM_SHUFFLE(0, 3, 2, 1));
    rows.row2 = _mm256_shuffle_epi32(rows.row2, _MM_SHUFFLE(1, 0, 3, 2));
    rows.row3 = _mm256_shuffle_epi32(rows.row3, _MM_SHUFFLE(2, 1, 0, 3));

    rows.row0 = _mm256_add_epi32(rows.row0, rows.row1);
    rows.row3 = RotateLeft<16>(_mm256_xor_si256(rows.row3, rows.row0));
    rows.row2 = _mm256_add_epi32(rows.row2, rows.row3);
    rows.row1 = RotateLeft<12>(_mm256_xor_si256(rows.row1, rows.row2));
    rows.row0 = _mm256_add_epi32(rows.row0, rows.row1);
    rows.row3 = RotateLeft<8>(_mm256_xor_si256(rows.row3, rows.row0));
    rows.row2 = _mm256_add_epi32(rows.row2, rows.row3);
    rows.row1 = RotateLeft<7>(_mm256_xor_si256(rows.row1, rows.row2));

    rows.row1 = _mm256_shuffle_epi32(rows.row1, _MM_SHUFFLE(2, 1, 0, 3));
    rows.row2 = _mm256_shuffle_epi32(rows.row2, _MM_SHUFFLE(1, 0, 3, 2));
    rows.row3 = _mm256_shuffle_epi32(rows.row3, _MM_SHUFFLE(0, 3, 2, 1));
}

YSM_TARGET_AVX2 inline __m256i AddBlockCounters(__m256i state3,
                                                std::uint64_t first,
                                                std::uint64_t second) noexcept {
    return _mm256_add_epi64(
        state3, _mm256_set_epi64x(0, static_cast<std::int64_t>(second), 0,
                                  static_cast<std::int64_t>(first)));
}
}  // namespace

YSM_TARGET_AVX2 void ChaChaXor8Avx2(const ChaChaState& state, Byte* bytes,
                                    std::uint32_t rounds) noexcept {
    alignas(32) const std::array<__m256i, 4> base{
        _mm256_broadcastsi128_si256(
            _mm_loadu_si128(reinterpret_cast<const __m128i*>(state.data()))),
        _mm256_broadcastsi128_si256(_mm_loadu_si128(
            reinterpret_cast<const __m128i*>(state.data() + 4))),
        _mm256_broadcastsi128_si256(_mm_loadu_si128(
            reinterpret_cast<const __m128i*>(state.data() + 8))),
        _mm256_broadcastsi128_si256(_mm_loadu_si128(
            reinterpret_cast<const __m128i*>(state.data() + 12)))};
    alignas(32) std::array<Rows, 4> pairs{};
    for (std::size_t pair = 0; pair < pairs.size(); ++pair) {
        const auto first = pair * 2;
        pairs[pair] = {base[0], base[1], base[2],
                       AddBlockCounters(base[3], first, first + 1)};
    }

    for (std::uint32_t round = 0; round < rounds; round += 2) {
        for (auto& pair : pairs) {
            DoubleRound(pair);
        }
    }

    for (std::size_t pair = 0; pair < pairs.size(); ++pair) {
        const auto first = pair * 2;
        alignas(32) const std::array<__m256i, 4> initial{
            base[0], base[1], base[2],
            AddBlockCounters(base[3], first, first + 1)};
        alignas(32) const std::array<__m256i, 4> result{
            _mm256_add_epi32(pairs[pair].row0, initial[0]),
            _mm256_add_epi32(pairs[pair].row1, initial[1]),
            _mm256_add_epi32(pairs[pair].row2, initial[2]),
            _mm256_add_epi32(pairs[pair].row3, initial[3])};
        for (std::size_t row = 0; row < result.size(); ++row) {
            auto* first_output = bytes + first * 64 + row * 16;
            auto* second_output = first_output + 64;
            const auto first_result = _mm256_castsi256_si128(result[row]);
            const auto second_result = _mm256_extracti128_si256(result[row], 1);
            _mm_storeu_si128(
                reinterpret_cast<__m128i*>(first_output),
                _mm_xor_si128(_mm_loadu_si128(reinterpret_cast<const __m128i*>(
                                  first_output)),
                              first_result));
            _mm_storeu_si128(
                reinterpret_cast<__m128i*>(second_output),
                _mm_xor_si128(_mm_loadu_si128(reinterpret_cast<const __m128i*>(
                                  second_output)),
                              second_result));
        }
    }
}
}  // namespace ysm::legacy::v3::codec::internal

#endif

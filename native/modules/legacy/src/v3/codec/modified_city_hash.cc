// Copyright (c) 2011 Google, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
//
// This is the 64-bit portion of the historical YSM CityHash fork. Its constants
// and uint128 field order are format semantics and intentionally differ from
// upstream CityHash 1.1.1.

#include "modified_city_hash.h"

#include <algorithm>
#include <array>
#include <bit>
#include <cstring>
#include <utility>

#include <buffer_managed.h>

namespace ysm::legacy::v3::codec {
namespace {
constexpr std::uint64_t k0 = 0xE4986A230E5AAA17ULL;
constexpr std::uint64_t k1 = 0x91AF10802CAB25A5ULL;
constexpr std::uint64_t k2 = 0xAF29CE778879D9C7ULL;
constexpr std::uint64_t kMul = 16001129912037256081ULL;

std::uint64_t Fetch64(const std::uint8_t* value) noexcept {
    std::uint64_t result{};
    std::memcpy(&result, value, sizeof(result));
    if constexpr (std::endian::native == std::endian::big) {
        result = std::byteswap(result);
    }
    return result;
}

std::uint32_t Fetch32(const std::uint8_t* value) noexcept {
    std::uint32_t result{};
    std::memcpy(&result, value, sizeof(result));
    if constexpr (std::endian::native == std::endian::big) {
        result = std::byteswap(result);
    }
    return result;
}

std::uint64_t Rotate(std::uint64_t value, int shift) noexcept {
    return shift == 0 ? value : (value >> shift) | (value << (64 - shift));
}

std::uint64_t ShiftMix(std::uint64_t value) noexcept {
    return value ^ (value >> 47U);
}

std::uint64_t HashLen16(std::uint64_t high, std::uint64_t low) noexcept {
    auto a = (low ^ high) * kMul;
    a ^= a >> 47U;
    auto b = (high ^ a) * kMul;
    b ^= b >> 47U;
    b *= kMul;
    return b;
}

std::uint64_t HashLen16(std::uint64_t u, std::uint64_t v,
                        std::uint64_t mul) noexcept {
    auto a = (u ^ v) * mul;
    a ^= a >> 47U;
    auto b = (v ^ a) * mul;
    b ^= b >> 47U;
    b *= mul;
    return b;
}

std::uint64_t HashLen0To16(const std::uint8_t* data,
                           std::size_t length) noexcept {
    if (length >= 8) {
        const auto mul = k2 + length * 2;
        const auto a = Fetch64(data) + k2;
        const auto b = Fetch64(data + length - 8);
        const auto c = Rotate(b, 37) * mul + a;
        const auto d = (Rotate(a, 25) + b) * mul;
        return HashLen16(c, d, mul);
    }
    if (length >= 4) {
        const auto mul = k2 + length * 2;
        const auto a = static_cast<std::uint64_t>(Fetch32(data));
        return HashLen16(length + (a << 3U), Fetch32(data + length - 4), mul);
    }
    if (length != 0) {
        const auto a = data[0];
        const auto b = data[length >> 1U];
        const auto c = data[length - 1];
        const auto y = static_cast<std::uint32_t>(a) +
                       (static_cast<std::uint32_t>(b) << 8U);
        const auto z = static_cast<std::uint32_t>(length) +
                       (static_cast<std::uint32_t>(c) << 2U);
        return ShiftMix(y * k2 ^ z * k0) * k2;
    }
    return k2;
}

std::uint64_t HashLen17To32(const std::uint8_t* data,
                            std::size_t length) noexcept {
    const auto mul = k2 + length * 2;
    const auto a = Fetch64(data) * k1;
    const auto b = Fetch64(data + 8);
    const auto c = Fetch64(data + length - 8) * mul;
    const auto d = Fetch64(data + length - 16) * k2;
    return HashLen16(Rotate(a + b, 43) + Rotate(c, 30) + d,
                     a + Rotate(b + k2, 18) + c, mul);
}

std::pair<std::uint64_t, std::uint64_t> WeakHashLen32WithSeeds(
    std::uint64_t w, std::uint64_t x, std::uint64_t y, std::uint64_t z,
    std::uint64_t a, std::uint64_t b) noexcept {
    a += w;
    b = Rotate(b + a + z, 21);
    const auto c = a;
    a += x + y;
    b += Rotate(a, 44);
    return {a + z, b + c};
}

std::pair<std::uint64_t, std::uint64_t> WeakHashLen32WithSeeds(
    const std::uint8_t* data, std::uint64_t a, std::uint64_t b) noexcept {
    return WeakHashLen32WithSeeds(Fetch64(data), Fetch64(data + 8),
                                  Fetch64(data + 16), Fetch64(data + 24), a, b);
}

std::uint64_t HashLen33To64(const std::uint8_t* data,
                            std::size_t length) noexcept {
    const auto mul = k2 + length * 2;
    auto a = Fetch64(data) * k2;
    auto b = Fetch64(data + 8);
    const auto c = Fetch64(data + length - 24);
    const auto d = Fetch64(data + length - 32);
    const auto e = Fetch64(data + 16) * k2;
    const auto f = Fetch64(data + 24) * 9;
    const auto g = Fetch64(data + length - 8);
    const auto h = Fetch64(data + length - 16) * mul;
    const auto u = Rotate(a + g, 43) + (Rotate(b, 30) + c) * 9;
    const auto v = ((a + g) ^ d) + f + 1;
    const auto w = std::byteswap((u + v) * mul) + h;
    const auto x = Rotate(e + f, 42) + c;
    const auto y = (std::byteswap((v + w) * mul) + g) * mul;
    const auto z = e + f + c;
    a = std::byteswap((x + z) * mul + y) + b;
    b = ShiftMix((z + a) * mul + d + h) * mul;
    return b + x;
}

struct LongHashState {
    std::uint64_t x;
    std::uint64_t y;
    std::uint64_t z;
    std::pair<std::uint64_t, std::uint64_t> v;
    std::pair<std::uint64_t, std::uint64_t> w;
};

LongHashState BeginLongHash(std::uint64_t length,
                            const std::uint8_t* tail) noexcept {
    auto x = Fetch64(tail + 24);
    auto y = Fetch64(tail + 48) + Fetch64(tail + 8);
    auto z = HashLen16(Fetch64(tail + 16) + length, Fetch64(tail + 40));
    auto v = WeakHashLen32WithSeeds(tail, length, z);
    auto w = WeakHashLen32WithSeeds(tail + 32, y + k1, x);
    return {x, y, z, v, w};
}

void HashLongBlock(LongHashState& state, const std::uint8_t* data,
                   bool first) noexcept {
    if (first) {
        state.x = state.x * k1 + Fetch64(data);
    }
    state.x =
        Rotate(state.x + state.y + state.v.first + Fetch64(data + 8), 37) * k1;
    state.y = Rotate(state.y + state.v.second + Fetch64(data + 48), 42) * k1;
    state.x ^= state.w.second;
    state.y += state.v.first + Fetch64(data + 40);
    state.z = Rotate(state.z + state.w.first, 33) * k1;
    state.v = WeakHashLen32WithSeeds(data, state.v.second * k1,
                                     state.x + state.w.first);
    state.w = WeakHashLen32WithSeeds(data + 32, state.z + state.w.second,
                                     state.y + Fetch64(data + 16));
    std::swap(state.z, state.x);
}

std::uint64_t FinishLongHash(const LongHashState& state) noexcept {
    return HashLen16(HashLen16(state.v.first, state.w.first) +
                         ShiftMix(state.y) * k1 + state.z,
                     HashLen16(state.v.second, state.w.second) + state.x);
}
}  // namespace

std::uint64_t ModifiedCityHash64(BufferViewR bytes) noexcept {
    const auto length = bytes.size();
    if (length <= 16) {
        return HashLen0To16(bytes.data(), length);
    }
    if (length <= 32) {
        return HashLen17To32(bytes.data(), length);
    }
    if (length <= 64) {
        return HashLen33To64(bytes.data(), length);
    }

    auto state = BeginLongHash(length, bytes.data() + length - 64);
    const auto loop_length = (length - 1) & ~std::size_t{63};
    for (std::size_t offset = 0; offset < loop_length; offset += 64) {
        HashLongBlock(state, bytes.data() + offset, offset == 0);
    }
    return FinishLongHash(state);
}

std::uint64_t ModifiedCityHash64WithSeed(BufferViewR bytes,
                                         std::uint64_t seed) noexcept {
    return HashLen16(ModifiedCityHash64(bytes) - k2, seed);
}
}  // namespace ysm::legacy::v3::codec

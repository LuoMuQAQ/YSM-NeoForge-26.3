#pragma once

#include <array>
#include <cstdint>

#include <buffer.h>
#include <cpu.h>

namespace ysm::legacy::v3::codec::internal {
using ChaChaState = std::array<std::uint32_t, 16>;

void ChaChaXor(ChaChaState& state, BufferView bytes, std::uint32_t rounds,
               simd::Type implementation) noexcept;

#ifdef YSM_X64
void ChaChaXor4Sse(const ChaChaState& state, Byte* bytes,
                   std::uint32_t rounds) noexcept;
void ChaChaXor8Avx2(const ChaChaState& state, Byte* bytes,
                    std::uint32_t rounds) noexcept;
#elif defined(YSM_ARM64)
void ChaChaXor4Neon(const ChaChaState& state, Byte* bytes,
                    std::uint32_t rounds) noexcept;
#endif
}  // namespace ysm::legacy::v3::codec::internal

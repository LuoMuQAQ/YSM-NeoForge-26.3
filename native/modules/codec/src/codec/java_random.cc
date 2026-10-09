#include "codec/java_random.h"

namespace ysm::codec {
namespace {
constexpr uint64_t kMultiplier = 0x5DEECE66DL;
constexpr uint64_t kAddend = 0xBL;
constexpr uint64_t kMask = (1LL << 48) - 1;

uint64_t InitialScramble(uint64_t seed) noexcept {
    return (seed ^ kMultiplier) & kMask;
}
}  // namespace

uint32_t JavaRandom::Next(uint32_t bits) noexcept {
    seed_ = (seed_ * kMultiplier + kAddend) & kMask;
    return static_cast<uint32_t>(seed_ >> (48 - bits));
}

JavaRandom::JavaRandom(uint64_t seed) noexcept : seed_(InitialScramble(seed)) {}

void JavaRandom::NextBytes(BufferView bytes) noexcept {
    for (size_t i = 0, len = bytes.size(); i < len;)
        for (uint32_t rnd = NextInt(), n = ((len - i) > 4 ? 4 : (len - i));
             n-- > 0; rnd >>= 8)
            bytes[i++] = static_cast<Byte>(rnd);
}

uint32_t JavaRandom::NextInt() noexcept {
    return Next(32);
}
}  // namespace ysm::codec

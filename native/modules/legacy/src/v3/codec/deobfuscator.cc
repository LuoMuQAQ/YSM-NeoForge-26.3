#include "deobfuscator.h"

#include <cstdint>

#include "modified_city_hash.h"

namespace ysm::legacy::v3::codec {
namespace {
constexpr std::uint64_t kXorSeed = 14994677486147417473ULL;
}  // namespace

Deobfuscator::Deobfuscator(BufferFixedViewR<56> password)
    : generator_(ModifiedCityHash64WithSeed(password, kXorSeed)) {}
}  // namespace ysm::legacy::v3::codec

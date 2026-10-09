#include "codec/blake3.h"

#include <blake3.h>

namespace ysm::codec {
class [[maybe_unused]] Blake3Hasher::Impl {
   public:
    blake3_hasher hasher;
};

Blake3Hasher::Blake3Hasher() {
    blake3_hasher_init(&Pimpl().hasher);
}

Blake3Hasher::~Blake3Hasher() = default;

void Blake3Hasher::Update(BufferViewR input) noexcept {
    blake3_hasher_update(&Pimpl().hasher, input.data(), input.size());
}

void Blake3Hasher::Finalize(BufferFixedView<kBlake3HashSize> output) noexcept {
    blake3_hasher_finalize(&Pimpl().hasher, output.data(), output.size());
}

void Blake3Hasher::Reset() noexcept {
    blake3_hasher_reset(&Pimpl().hasher);
    ;
}

YSM_PIMPL_DEFINITION(Blake3Hasher);

}  // namespace ysm::codec
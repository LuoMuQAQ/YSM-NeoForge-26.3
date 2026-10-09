#pragma once

#include "buffer.h"
#include "non_copyable.h"
#include "pimpl.h"

namespace ysm::codec {
constexpr size_t kBlake3HashSize = 32;

class Blake3Hasher : NonCopyable {
    YSM_PIMPL_DECLARE(Impl, 2048);

   public:
    Blake3Hasher();
    ~Blake3Hasher();
    void Update(BufferViewR input) noexcept;
    void Finalize(BufferFixedView<kBlake3HashSize> output) noexcept;
    void Reset() noexcept;
};

inline void Blake3Hash(BufferViewR input,
                       BufferFixedView<kBlake3HashSize> output) {
    Blake3Hasher hasher;
    hasher.Update(input);
    hasher.Finalize(output);
}
}  // namespace ysm::codec
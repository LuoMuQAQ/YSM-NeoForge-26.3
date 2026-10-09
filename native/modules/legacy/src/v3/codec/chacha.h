#pragma once

#include <cstddef>

#include <buffer.h>
#include <pimpl.h>

namespace ysm::legacy::v3::codec {
inline constexpr std::size_t kChaChaPasswordSize = 56;

class YsmChaCha final {
   public:
    explicit YsmChaCha(BufferFixedViewR<kChaChaPasswordSize> password);
    YsmChaCha(const YsmChaCha&);
    YsmChaCha& operator=(const YsmChaCha&);
    YsmChaCha(YsmChaCha&&) noexcept;
    YsmChaCha& operator=(YsmChaCha&&) noexcept;
    ~YsmChaCha();

    [[nodiscard]] std::size_t next_chunk_size() const noexcept;
    void Decrypt(BufferView bytes, bool complete_chunk);

   private:
    YSM_PIMPL_DECLARE(Impl, 96)
};
}  // namespace ysm::legacy::v3::codec

#pragma once

#include <7zTypes.h>
#include <span>

#include "buffer.h"
#include "err.h"
#include "non_copyable.h"

namespace ysm::codec::sevenzip {
class MemoryStream : public NonCopyable {
    BufferViewR data_;
    size_t buffer_position_ = 0;

    struct {
        MemoryStream* ctx;
        ILookInStream vt;
    } vtable_holder_;

   public:
    explicit MemoryStream(BufferViewR data) noexcept;

    absl::Status Ok() const noexcept { return OkStatus(); }

    [[nodiscard]] ILookInStreamPtr vtable() const noexcept;

   private:
    SRes Look(const void** buf, size_t* size) const noexcept;
    SRes Skip(size_t offset) noexcept;
    SRes Read(void* buf, size_t* size) noexcept;
    SRes Seek(Int64* pos, ESzSeek origin) noexcept;
};
}  // namespace ysm::codec::sevenzip

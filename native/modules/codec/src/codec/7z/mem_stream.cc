#include "mem_stream.h"

#include <cstring>

namespace ysm::codec::sevenzip {
SRes MemoryStream::Look(const void** buf, size_t* size) const noexcept {
    if (buffer_position_ == data_.size() && *size != 0) [[unlikely]] {
        *size = 0;
        return SZ_ERROR_INPUT_EOF;
    }
    if (auto remain = data_.size() - buffer_position_; remain < *size) {
        *size = remain;
    }
    *buf = data_.data() + buffer_position_;
    return SZ_OK;
}

SRes MemoryStream::Skip(size_t offset) noexcept {
    if (buffer_position_ + offset > data_.size()) [[unlikely]] {
        return SZ_ERROR_MEM;
    }
    buffer_position_ += offset;
    return SZ_OK;
}

SRes MemoryStream::Read(void* buf, size_t* size) noexcept {
    if (buffer_position_ == data_.size() && *size != 0) [[unlikely]] {
        *size = 0;
        return SZ_ERROR_INPUT_EOF;
    }
    if (auto remain = data_.size() - buffer_position_; remain < *size) {
        *size = remain;
    }
    std::memcpy(buf, data_.data() + buffer_position_, *size);
    buffer_position_ += *size;
    return SZ_OK;
}

SRes MemoryStream::Seek(Int64* pos, ESzSeek origin) noexcept {
    auto old_position = buffer_position_;
    switch (origin) {
        case SZ_SEEK_SET:
            buffer_position_ = *pos;
            break;
        case SZ_SEEK_CUR:
            buffer_position_ += *pos;
            break;
        case SZ_SEEK_END:
            buffer_position_ = data_.size() + *pos;
            break;
        default:
            return SZ_ERROR_PARAM;
    }
    if (buffer_position_ < 0 || buffer_position_ > data_.size()) [[unlikely]] {
        buffer_position_ = old_position;
        return SZ_ERROR_FAIL;
    }
    *pos = static_cast<Int64>(buffer_position_);
    return SZ_OK;
}

MemoryStream::MemoryStream(BufferViewR data) noexcept
    : data_(data),
      vtable_holder_(
          this,
          {
              [](ILookInStreamPtr p, const void** buf, size_t* size) {
                  return Z7_CONTAINER_FROM_VTBL(p, decltype(vtable_holder_), vt)
                      ->ctx->Look(buf, size);
              },
              [](ILookInStreamPtr p, size_t offset) {
                  return Z7_CONTAINER_FROM_VTBL(p, decltype(vtable_holder_), vt)
                      ->ctx->Skip(offset);
              },
              [](ILookInStreamPtr p, void* buf, size_t* size) {
                  return Z7_CONTAINER_FROM_VTBL(p, decltype(vtable_holder_), vt)
                      ->ctx->Read(buf, size);
              },
              [](ILookInStreamPtr p, Int64* pos, ESzSeek origin) {
                  return Z7_CONTAINER_FROM_VTBL(p, decltype(vtable_holder_), vt)
                      ->ctx->Seek(pos, origin);
              },
          }) {}

ILookInStreamPtr MemoryStream::vtable() const noexcept {
    return &vtable_holder_.vt;
}
}  // namespace ysm::codec::sevenzip

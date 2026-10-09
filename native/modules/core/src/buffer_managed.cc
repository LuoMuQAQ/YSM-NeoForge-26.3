#include "buffer_managed.h"

#include <mimalloc-override.h>
#include <new>

#define YSM_BUFFER_ASSERT(expr)     \
    do {                            \
        if (!(expr)) [[unlikely]] { \
            throw std::bad_alloc(); \
        }                           \
    } while (0)

namespace ysm {
BufferManaged::BufferManaged() noexcept : size_(0) {}

BufferManaged::BufferManaged(size_t size) : size_(size) {
    reserve(size);
}

BufferManaged::BufferManaged(BufferViewR data) : size_(0) {
    CopyFrom(data);
}

BufferManaged::BufferManaged(BufferView data, OwnFlag)
    : mem_(data.data()), cap_(data.size()), size_(data.size()) {}

BufferManaged::BufferManaged(BufferManaged&& other) noexcept
    : mem_(other.mem_), cap_(other.cap_), size_(other.size_) {
    other.mem_ = nullptr;
    other.cap_ = 0;
    other.size_ = 0;
}

BufferManaged& BufferManaged::operator=(BufferManaged&& other) noexcept {
    if (this != &other) [[likely]] {
        if (mem_ != nullptr) {
            free(mem_);
        }
        mem_ = other.mem_;
        cap_ = other.cap_;
        size_ = other.size_;

        other.mem_ = nullptr;
        other.cap_ = 0;
        other.size_ = 0;
    }
    return *this;
}

void BufferManaged::clear() noexcept {
    size_ = 0;
}

void BufferManaged::reset() noexcept {
    if (mem_ != nullptr) [[likely]] {
        free(mem_);
        mem_ = nullptr;
        cap_ = 0;
        size_ = 0;
    }
}

BufferManaged::~BufferManaged() noexcept {
    reset();
}

void BufferManaged::CopyFrom(BufferViewR data) {
    if (!data.empty()) {
        resize(data.size());
        std::memcpy(mem_, data.data(), data.size());
    } else {
        clear();
    }
}

void BufferManaged::reserve(size_t size) {
    YSM_BUFFER_ASSERT(size <= kMaxSize);
    if (size <= cap_) {
        return;
    }
    if (cap_ == 0) {
        auto mem = static_cast<Byte*>(malloc(size));
        YSM_BUFFER_ASSERT(mem != nullptr);
        mem_ = mem;
    } else {
        if (auto double_size = cap_ * 2; size < double_size) {
            size = std::min(kMaxSize, double_size);
        }
        auto mem = static_cast<Byte*>(realloc(mem_, size));
        YSM_BUFFER_ASSERT(mem != nullptr);
        mem_ = mem;
    }
    cap_ = size;
}

void BufferManaged::resize(size_t size) {
    reserve(size);
    size_ = size;
}

void BufferManaged::shrink_to_fit() {
    if (cap_ != size_) {
        if (size_ > 0) {
            auto new_mem = static_cast<Byte*>(realloc(mem_, size_));
            YSM_BUFFER_ASSERT(new_mem != nullptr);
            mem_ = new_mem;
            cap_ = size_;
        } else {
            reset();
        }
    }
}

BufferManaged::OwnedMem BufferManaged::release() noexcept {
    if (mem_ == nullptr) [[unlikely]] {
        return {};
    }
    OwnedMem ret(mem_, size_);
    mem_ = nullptr;
    cap_ = 0;
    size_ = 0;
    return ret;
}
}  // namespace ysm
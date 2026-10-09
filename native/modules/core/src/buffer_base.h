#pragma once

#include "buffer.h"

namespace ysm {
template <typename Derived>
class BufferBase {
    constexpr Derived& Self() & noexcept {
        return static_cast<Derived&>(*this);
    }

    constexpr const Derived& Self() const& noexcept {
        return static_cast<const Derived&>(*this);
    }

   public:
    Byte& operator[](size_t index) & { return Self().data()[index]; }

    const Byte& operator[](size_t index) const& { return Self().data()[index]; }

    constexpr bool empty() const noexcept { return Self().size() == 0; }

    constexpr operator BufferView() & noexcept {
        return BufferView{Self().data(), Self().size()};
    }

    constexpr operator BufferViewR() const& noexcept {
        return BufferViewR{Self().data(), Self().size()};
    }

    Byte* begin() & noexcept { return Self().data(); }

    Byte* end() & noexcept { return Self().data() + Self().size(); }

    const Byte* begin() const& noexcept { return Self().data(); }

    const Byte* end() const& noexcept { return Self().data() + Self().size(); }

    Byte& operator[](size_t index) && = delete;
    const Byte& operator[](size_t index) const&& = delete;

    constexpr operator BufferView() && noexcept = delete;
    constexpr operator BufferViewR() const&& noexcept = delete;

    Byte* begin() && noexcept = delete;
    Byte* end() && noexcept = delete;
    const Byte* begin() const&& noexcept = delete;
    const Byte* end() const&& noexcept = delete;
};
}  // namespace ysm

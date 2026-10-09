#pragma once

#include "buffer_base.h"

namespace ysm {

template <typename Scalar>
concept ScalarType =
    std::is_standard_layout_v<Scalar> && std::is_trivially_copyable_v<Scalar>;

template <ScalarType Scalar>
struct BufferScalar final : BufferBase<BufferScalar<Scalar>> {
    constexpr static size_t kScalarSize = sizeof(Scalar);

    Scalar value;

    constexpr explicit BufferScalar(Scalar&& value) : value(std::move(value)) {}

    constexpr Byte* data() & noexcept {
        return reinterpret_cast<Byte*>(&value);
    }

    constexpr const Byte* data() const& noexcept {
        return reinterpret_cast<const Byte*>(&value);
    }

    constexpr size_t size() const noexcept { return kScalarSize; }

    constexpr operator BufferFixedView<kScalarSize>() & noexcept {
        return {data(), kScalarSize};
    }

    constexpr operator BufferFixedViewR<kScalarSize>() const& noexcept {
        return {data(), kScalarSize};
    }

    Byte* data() && = delete;
    const Byte* data() const&& = delete;
    operator BufferFixedView<kScalarSize>() && noexcept = delete;
    operator BufferFixedViewR<kScalarSize>() const&& = delete;
};

template <typename ScalarIn,
          ScalarType Scalar = std::remove_reference_t<ScalarIn>>
constexpr auto ScalarBuf(ScalarIn&& value) {
    if constexpr (std::is_lvalue_reference_v<decltype(value)>) {
        constexpr size_t kScalarSize = sizeof(Scalar);
        if constexpr (std::is_const_v<Scalar>) {
            return BufferFixedViewR<kScalarSize>{
                reinterpret_cast<const Byte*>(&value), kScalarSize};
        } else {
            return BufferFixedView<kScalarSize>{reinterpret_cast<Byte*>(&value),
                                                kScalarSize};
        }
    } else {
        return BufferScalar<Scalar>(std::forward<Scalar>(value));
    }
}

template <ScalarType Scalar>
constexpr Scalar BufScalar(auto&& buf) {
    Scalar value;
    Copy(buf, ScalarBuf(value));
    return value;
}
}  // namespace ysm

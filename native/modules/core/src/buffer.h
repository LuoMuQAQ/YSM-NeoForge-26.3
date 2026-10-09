#pragma once

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <span>
#include <string_view>
#include <type_traits>
#include <utility>

#include "err.h"

namespace ysm {
using Byte = uint8_t;
static_assert(sizeof(Byte) == 1);

using BufferView = std::span<Byte>;
using BufferViewR = std::span<const Byte>;

template <size_t kSize>
using BufferFixed = std::array<Byte, kSize>;
template <size_t kSize>
using BufferFixedView = std::span<Byte, kSize>;
template <size_t kSize>
using BufferFixedViewR = std::span<const Byte, kSize>;

constexpr inline BufferView kEmptyBuffer(static_cast<Byte*>(nullptr), 0);

template <size_t kLiteralValue>
struct SizeLiteral {
    static constexpr auto kValue = kLiteralValue;

    consteval operator size_t() const { return kLiteralValue; }
};

template <char... kChars>
consteval auto operator""_B() {
    constexpr auto kParse = [] {
        size_t v = 0;
        ((v = v * 10 + (kChars - '0')), ...);
        return v;
    };
    return SizeLiteral<kParse()>{};
}

template <char... kChars>
consteval auto operator""_KB() {
    return SizeLiteral<decltype(operator""_B < kChars...>())::kValue
                       * 1024>{};
}

template <char... kChars>
consteval auto operator""_MB() {
    return SizeLiteral<decltype(operator""_B < kChars...>())::kValue
                       * 1024 * 1024>{};
}

template <char... kChars>
consteval auto operator""_GB() {
    return SizeLiteral<decltype(operator""_B < kChars...>())::kValue
                       * 1024 * 1024 * 1024>{};
}

template <size_t kSize>
constexpr auto Slice(auto&& buffer, size_t offset = 0, SizeLiteral<kSize> = {}) {
    if constexpr (std::is_rvalue_reference_v<decltype(buffer)>) if (0) std::move(buffer).begin();
    std::span span = buffer;
    return span.subspan(offset).template subspan<0, kSize>();
}

constexpr auto Slice(auto&& buffer, size_t offset, size_t size = std::dynamic_extent) {
    if constexpr (std::is_rvalue_reference_v<decltype(buffer)>) if (0) std::move(buffer).begin();
    std::span span = buffer;
    return span.subspan(offset, size);
}

constexpr std::string_view BufStr(auto&& buffer) {
    if constexpr (std::is_rvalue_reference_v<decltype(buffer)>) if (0) std::move(buffer).begin();
    return {reinterpret_cast<const char*>(buffer.data()), buffer.size()};
}

template <typename StrType>
    requires std::is_convertible_v<std::remove_cvref_t<StrType>,
                                   std::string_view>
constexpr auto StrBuf(StrType&& str) {
    if constexpr (std::is_same_v<std::remove_cvref_t<StrType>, const char*>) {
        return StrBuf(std::string_view(str));
    } else {
        static_assert(!std::is_same_v<std::string&&, decltype(str)>);
        return std::span{
            reinterpret_cast<std::conditional_t<
                std::is_const_v<std::remove_pointer_t<decltype(str.data())>>,
                const Byte*, Byte*>>(str.data()),
            str.size()};
    }
}

template <size_t kSize>
auto Consume(auto& view, SizeLiteral<kSize> = {}) {
    auto result = view.template first<kSize>();
    view = view.subspan(kSize);
    return result;
}

auto Consume(auto& view, size_t size) {
    auto result = view.first(size);
    view = view.subspan(size);
    return result;
}

bool Cmp(auto&& a, auto&& b) noexcept {
    if (a.size() != b.size())
        return false;
    return std::memcmp(a.data(), b.data(), a.size()) == 0;
}

void Copy(auto&& src, auto&& dst) noexcept {
    std::ranges::copy(src, dst.begin());
}

inline absl::Status DataCorruption() {
    return absl::DataLossError("Data corruption.");
}

#define YSM_ASSERT_BUF_SIZE(buf, least_size) \
    YSM_ASSERT(buf.size() >= least_size, DataCorruption());
}  // namespace ysm

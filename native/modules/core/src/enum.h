#pragma once

#include <format>
#include <magic_enum.hpp>
#include <magic_enum_switch.hpp>
#include <type_traits>
#include <utility>

#include "absl/status/statusor.h"
#include "cpu.h"

namespace ysm {
namespace internal {
template <typename Fn, typename... Selected>
decltype(auto) EnumDispatch(Fn&& fn) {
    return std::forward<Fn>(fn)(Selected{}...);
}

template <typename Fn, typename... Selected>
decltype(auto) EnumDispatch(Fn&& fn, auto value, auto... values) {
    if constexpr (std::is_enum_v<std::remove_cvref_t<decltype(value)>>) {
        return magic_enum::enum_switch(
            [&]([[maybe_unused]] auto selected_value) -> decltype(auto) {
                return EnumDispatch<Fn, Selected..., decltype(selected_value)>(
                    std::forward<Fn>(fn), values...);
            },
            value);
    } else {
        if (value) {
            return EnumDispatch<Fn, Selected...,
                                std::integral_constant<bool, true>>(
                std::forward<Fn>(fn), values...);
        }
        return EnumDispatch<Fn, Selected...,
                            std::integral_constant<bool, false>>(
            std::forward<Fn>(fn), values...);
    }
}

template <typename E>
    requires std::is_scoped_enum_v<E>
absl::Status InvalidEnumValueError(E value) {
    return absl::InvalidArgumentError(
        std::format("Illegal {} value: {}", magic_enum::enum_type_name<E>(),
                    std::to_underlying(value)));
}

}  // namespace internal

template <typename Fn, typename... Enums>
decltype(auto) EnumSwitch(Fn&& fn, Enums... values) {
    static_assert(
        ((std::is_enum_v<Enums> || std::is_same_v<Enums, bool>) && ...));
    return internal::EnumDispatch<Fn>(std::forward<Fn>(fn), values...);
}

template <class E>
absl::StatusOr<E> EnumCast(std::underlying_type_t<E> v) {
    if (auto result = magic_enum::enum_cast<E>(v)) {
        return result.value();
    }
    return absl::InvalidArgumentError(std::format(
        "Illegal {} value: {}", magic_enum::enum_type_name<E>(), v));
}

inline absl::Status EnumValidate() {
    return absl::OkStatus();
}

template <typename E, typename... Enums>
    requires(std::is_scoped_enum_v<E> && (std::is_scoped_enum_v<Enums> && ...))
absl::Status EnumValidate(E value, Enums... values) {
    if (!magic_enum::enum_contains(value)) {
        return internal::InvalidEnumValueError(value);
    }
    if constexpr (sizeof...(Enums) > 0) {
        return EnumValidate(values...);
    }
    return absl::OkStatus();
}

}  // namespace ysm

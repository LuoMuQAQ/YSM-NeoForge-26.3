#pragma once

#include <absl/status/statusor.h>
#include <type_traits>
#include <utility>
#include <string_view>

#include "inline.h"
#include "macro.h"

namespace ysm {
using absl::OkStatus;
using namespace std::string_view_literals;

namespace internal {
template <typename In>
auto&& GetStatus(In&& status) noexcept {
    if constexpr (std::is_same_v<absl::Status, std::remove_cvref_t<decltype(status)>>) {
        return std::forward<In>(status);
    } else {
        return std::forward<In>(status).status();
    }
}

template <typename T>
struct IsReferenceWrapper : std::false_type {};

template <typename T>
struct IsReferenceWrapper<std::reference_wrapper<T>> : std::true_type {};

template <typename T>
inline constexpr bool IsReferenceWrapperV =
        IsReferenceWrapper<std::remove_cvref_t<T>>::value;

template <typename T>
constexpr decltype(auto) UnwrapReference(T&& value) noexcept {
    if constexpr (IsReferenceWrapperV<T>) {
        return value.get();
    } else {
        return std::forward<T>(value);
    }
}
}  // namespace internal

#define YSM_RETURN_IF_ERROR_IMPL(status_name, ...)                 \
    do {                                                           \
        if (auto&& status_name = (__VA_ARGS__); !status_name.ok()) \
            [[unlikely]] {                                         \
            return ::ysm::internal::GetStatus(status_name);        \
        }                                                          \
    } while (0)

#define YSM_RETURN_IF_ERROR(...) \
    YSM_RETURN_IF_ERROR_IMPL(    \
        YSM_MACROS_CONCAT_NAME(_status_or_value, __COUNTER__), __VA_ARGS__)

#define YSM_ASSIGN_OR_RETURN_IMPL(status_name, lhs, ...) \
    do {                                                 \
        auto&& status_name = (__VA_ARGS__);              \
        if (!status_name.ok()) [[unlikely]] {            \
            return status_name.status();                 \
        }                                                \
        lhs = ::ysm::internal::UnwrapReference(std::move(status_name).value());            \
    } while (0)

#define YSM_ASSIGN_OR_RETURN(lhs, ...)                              \
    YSM_ASSIGN_OR_RETURN_IMPL(                                      \
        YSM_MACROS_CONCAT_NAME(_status_or_value, __COUNTER__), lhs, \
        __VA_ARGS__)

#define YSM_DECLARE_OR_RETURN_IMPL(status_name, var_name, ...) \
    auto&& status_name = (__VA_ARGS__);                        \
    if (!status_name.ok()) [[unlikely]] {                      \
        return status_name.status();                           \
    }                                                          \
    auto&& var_name = ::ysm::internal::UnwrapReference(status_name.value())

#define YSM_DECLARE_OR_RETURN(var_name, ...)                             \
    YSM_DECLARE_OR_RETURN_IMPL(                                          \
        YSM_MACROS_CONCAT_NAME(_status_or_value, __COUNTER__), var_name, \
        __VA_ARGS__)

#define YSM_ASSERT(expr, ...)     \
    do {                          \
        if (expr) {               \
        } else [[unlikely]] {     \
            return (__VA_ARGS__); \
        }                         \
    } while (0)

#define YSM_RETURN_IF_NULL_ONE(VAR)                                                      \
    if ((VAR) == nullptr) [[unlikely]] {                                                 \
        return absl::InvalidArgumentError(YSM_SV_LITERAL("Argument " #VAR " is null"));  \
    }                                                                                    \

#define YSM_RETURN_IF_NULL(...)                              \
    do {                                                     \
        YSM_PP_FOR_EACH(YSM_RETURN_IF_NULL_ONE, __VA_ARGS__) \
    } while (false)
}  // namespace ysm
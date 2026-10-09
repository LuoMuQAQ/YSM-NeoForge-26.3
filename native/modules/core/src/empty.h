#pragma once

#include <type_traits>

namespace ysm {
struct Empty {};

#if YSM_WINDOWS
#define YSM_NUA [[msvc::no_unique_address]]
#else
#define YSM_NUA [[no_unique_address]]
#endif

template <bool kCondition, typename T>
using EmptyOr = std::conditional_t<kCondition, T, Empty>;

#define YSM_EMPTY_OR(condition, ...) YSM_NUA ::ysm::EmptyOr<condition, __VA_ARGS__>
}  // namespace ysm
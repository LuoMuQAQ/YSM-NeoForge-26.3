#pragma once

#if defined(_MSC_VER)
#define YSM_ASSUME(condition) __assume(condition)
#elif defined(__GNUC__)
#if defined(__clang__)
#define YSM_ASSUME(condition) __builtin_assume(condition)
#else
#define YSM_ASSUME(condition)           \
    do {                                \
        if (!(condition)) {             \
            __builtin_unreachable();    \
        }                               \
    } while (false)
#endif
#else
#define YSM_ASSUME(condition) static_cast<void>(condition)
#endif

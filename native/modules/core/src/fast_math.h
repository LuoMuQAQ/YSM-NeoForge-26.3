#pragma once

#if defined(_MSC_VER)
#define YSM_FAST_MATH_BEGIN          \
    __pragma(float_control(push))     \
    __pragma(float_control(precise, off))
#define YSM_FAST_MATH_END __pragma(float_control(pop))
#elif defined(__clang__)
#define YSM_FAST_MATH_BEGIN              \
    _Pragma("float_control(push)")        \
    _Pragma("float_control(precise, off)")
#define YSM_FAST_MATH_END _Pragma("float_control(pop)")
#else
#define YSM_FAST_MATH_BEGIN
#define YSM_FAST_MATH_END
#endif

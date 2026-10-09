#pragma once

#ifdef YSM_DEBUG
#define YSM_INLINE inline
#else
#if defined(_MSC_VER)
#define YSM_INLINE __forceinline
#elif defined(__GNUC__)
#define YSM_INLINE inline __attribute__((always_inline))
#else
#define YSM_INLINE inline
#endif
#endif

#if defined(_MSC_VER)
#define YSM_NOINLINE __declspec(noinline)
#elif defined(__GNUC__)
#define YSM_NOINLINE __attribute__((noinline))
#else
#define YSM_NOINLINE
#endif

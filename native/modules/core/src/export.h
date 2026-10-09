#pragma once

#if YSM_WINDOWS
#define YSM_EXPORT __declspec(dllexport)
#else
#define YSM_EXPORT __attribute__((visibility("default")))
#endif

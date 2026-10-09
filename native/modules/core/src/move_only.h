#pragma once

#define YSM_MOVE_ONLY(type)                \
    type(const type&) = delete;            \
    type& operator=(const type&) = delete; \
    type(type&&) = default;                \
    type& operator=(type&&) = default;

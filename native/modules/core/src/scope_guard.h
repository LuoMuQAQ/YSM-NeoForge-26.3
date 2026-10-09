#pragma once

#include <utility>

#include "non_copyable.h"

namespace ysm {
template <typename F>
class ScopeGuard : NonCopyable {
   public:
    explicit ScopeGuard(F&& fn) : fn_(std::forward<F>(fn)) {}

    ~ScopeGuard() {
        if (active_) {
            fn_();
        }
    }

    void Release() noexcept { active_ = false; }

   private:
    F fn_;
    bool active_ = true;
};

template <typename F>
ScopeGuard(F) -> ScopeGuard<F>;
}  // namespace ysm
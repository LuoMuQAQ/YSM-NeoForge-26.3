#pragma once

#include <array>
#include <cstddef>
#include <memory>
#include <new>
#include <utility>

namespace ysm::internal {
template <typename ImplType, std::size_t kExpectedSize, std::size_t kAlign>
class PimplStorage final {
#ifdef YSM_DEBUG
    static constexpr size_t kSize = kExpectedSize * 15 / 10;
#else
    static constexpr size_t kSize = kExpectedSize;
#endif
public:
    template <class... Args>
    explicit PimplStorage(Args&&... args) {
        static_assert(kSize >= sizeof(ImplType));
        static_assert(kAlign >= alignof(ImplType));
        static_assert(kAlign % alignof(ImplType) == 0);
        std::construct_at(reinterpret_cast<ImplType*>(storage_.data()),
                          std::forward<Args>(args)...);
    }

    PimplStorage(const PimplStorage& other) : PimplStorage(other.get()) {}

    PimplStorage& operator=(const PimplStorage& other) {
        if (this != &other) [[likely]] {
            get() = other.get();
        }
        return *this;
    }

    PimplStorage(PimplStorage&& other) noexcept
        : PimplStorage(std::move(other.get())) {}

    PimplStorage& operator=(PimplStorage&& other) noexcept {
        if (this != &other) [[likely]] {
            get() = std::move(other.get());
        }
        return *this;
    }

    ~PimplStorage() noexcept {
        std::destroy_at(&get());
    }

    ImplType& get() noexcept {
        return *std::launder(reinterpret_cast<ImplType*>(storage_.data()));
    }

    const ImplType& get() const noexcept {
        return *std::launder(reinterpret_cast<const ImplType*>(storage_.data()));
    }

private:
    alignas(kAlign) std::array<std::byte, kSize> storage_;
};

#define YSM_PIMPL_DECLARE(impl_type, impl_size) \
    YSM_PIMPL_DECLARE_ALIGN(impl_type, impl_size, sizeof(std::max_align_t))

#define YSM_PIMPL_DECLARE_ALIGN(impl_type, impl_size, impl_align)   \
    class impl_type;                                                \
    using PimplType = impl_type;                                    \
    ::ysm::internal::PimplStorage<PimplType, impl_size, impl_align> \
        pimpl_storage_;                                             \
    PimplType& Pimpl() noexcept;                                    \
    const PimplType& Pimpl() const noexcept;

#define YSM_PIMPL_DEFINITION(type)                        \
    type::PimplType& type::Pimpl() noexcept {             \
        return pimpl_storage_.get();                      \
    }                                                     \
    const type::PimplType& type::Pimpl() const noexcept { \
        return pimpl_storage_.get();                      \
    }

#define YSM_PIMPL_CONSTRUCT(...) pimpl_storage_(__VA_ARGS__)
}  // namespace ysm::internal
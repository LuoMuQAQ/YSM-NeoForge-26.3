#pragma once

#include <cstddef>
#include <limits>
#include <new>
#include <type_traits>

namespace ysm {
namespace internal {
[[nodiscard]] void* SystemAllocate(std::size_t size, std::size_t alignment);
void SystemDeallocate(void* ptr, std::size_t alignment) noexcept;
}  // namespace allocator_internal

// 用于避开 mimalloc 下 tls 生命周期相关疑难杂症
template <typename T>
class SystemAllocator {
   public:
    using value_type = T;
    using is_always_equal = std::true_type;
    using propagate_on_container_move_assignment = std::true_type;

    template <typename U>
    struct rebind {
        using other = SystemAllocator<U>;
    };

    constexpr SystemAllocator() noexcept = default;

    template <typename U>
    constexpr SystemAllocator(const SystemAllocator<U>&) noexcept {}

    [[nodiscard]] T* allocate(std::size_t count) {
        if (count > max_size()) [[unlikely]] {
            throw std::bad_array_new_length{};
        }
        return static_cast<T*>(internal::SystemAllocate(
            count * sizeof(T), alignof(T)));
    }

    void deallocate(T* ptr, std::size_t) noexcept {
        internal::SystemDeallocate(ptr, alignof(T));
    }

    [[nodiscard]] static constexpr std::size_t max_size() noexcept {
        return (std::numeric_limits<std::size_t>::max)() / sizeof(T);
    }

private:

};

template <typename T, typename U>
constexpr bool operator==(const SystemAllocator<T>&,
                          const SystemAllocator<U>&) noexcept {
    return true;
}
}  // namespace ysm

#include "system_allocator.h"

#include <cstdint>
#include <cstdlib>
#include <limits>

namespace ysm::internal {
void* SystemAllocate(std::size_t size, std::size_t alignment) {
    size = size == 0 ? 1 : size;
    if (alignment <= alignof(std::max_align_t)) {
        if (auto* ptr = malloc(size); ptr != nullptr) [[likely]] {
            return ptr;
        }
        throw std::bad_alloc{};
    }

    constexpr auto kHeaderSize = sizeof(void*);
    const auto overhead = alignment - 1 + kHeaderSize;
    if (size > (std::numeric_limits<std::size_t>::max)() - overhead)
        [[unlikely]] {
        throw std::bad_array_new_length{};
    }

    auto* allocation = malloc(size + overhead);
    if (allocation == nullptr) [[unlikely]] {
        throw std::bad_alloc{};
    }

    const auto begin = reinterpret_cast<std::uintptr_t>(allocation) + kHeaderSize;
    const auto aligned = (begin + alignment - 1) & ~(alignment - 1);
    auto* ptr = reinterpret_cast<void*>(aligned);
    reinterpret_cast<void**>(ptr)[-1] = allocation;
    return ptr;
}

void SystemDeallocate(void* ptr, std::size_t alignment) noexcept {
    if (ptr == nullptr) {
        return;
    }
    if (alignment > alignof(std::max_align_t)) {
        ptr = reinterpret_cast<void**>(ptr)[-1];
    }
    free(ptr);
}
}  // namespace ysm::allocator_internal

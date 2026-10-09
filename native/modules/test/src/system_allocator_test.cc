#include <cstdint>
#include <string>
#include <vector>

#include <gtest/gtest.h>

#include <system_allocator.h>

namespace ysm {
namespace {
TEST(SystemAllocatorTest, SupportsStandardContainers) {
    std::vector<int, SystemAllocator<int>> values;
    values.assign({1, 2, 3, 4});

    std::basic_string<char, std::char_traits<char>, SystemAllocator<char>> text;
    text.assign("system allocator");

    EXPECT_EQ(values, (std::vector<int, SystemAllocator<int>>{1, 2, 3, 4}));
    EXPECT_EQ(text, "system allocator");
}

struct alignas(64) OverAlignedValue {
    std::byte data[64];
};

TEST(SystemAllocatorTest, SupportsOverAlignedTypes) {
    std::vector<OverAlignedValue, SystemAllocator<OverAlignedValue>> values(4);

    EXPECT_EQ(reinterpret_cast<std::uintptr_t>(values.data()) %
                  alignof(OverAlignedValue),
              0u);
}

TEST(SystemAllocatorTest, RejectsSizeOverflow) {
    SystemAllocator<std::uint64_t> allocator;

    EXPECT_THROW(static_cast<void>(
                     allocator.allocate(allocator.max_size() + 1)),
                 std::bad_array_new_length);
}
}  // namespace
}  // namespace ysm

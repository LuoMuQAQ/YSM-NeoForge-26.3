#include <gtest/gtest.h>

#include <array>
#include <string>

#include <codec/zstd.h>

namespace ysm::codec {
namespace {
BufferManaged Compress(BufferViewR input) {
    auto bound = ZstdGetCompressMaxSize(input.size()).value();
    BufferManaged compressed(bound);
    auto size = ZstdCompress(input, compressed, 16).value();
    compressed.resize(size);
    return compressed;
}

TEST(ZstdTest, FixedOutputAcceptsEmptyFrame) {
    BufferManaged input;
    auto compressed = Compress(input);
    BufferManaged output;
    BufferView output_view = output;

    auto result = ZstdDecompress(compressed, output_view);

    ASSERT_TRUE(result.ok()) << result.status();
    EXPECT_EQ(result.value(), 0);
}

TEST(ZstdTest, FixedOutputReportsActualDecodedSize) {
    const std::array input{Byte{1}, Byte{2}, Byte{3}, Byte{4}};
    auto compressed = Compress(input);
    BufferManaged output(input.size() + 3);
    BufferView output_view = output;

    auto result = ZstdDecompress(compressed, output_view);

    ASSERT_TRUE(result.ok()) << result.status();
    EXPECT_EQ(result.value(), input.size());
    EXPECT_TRUE(Cmp(Slice(output, 0, input.size()), input));
}

TEST(ZstdTest, FixedOutputRejectsCorruptFrame) {
    const std::array input{Byte{1}, Byte{2}, Byte{3}, Byte{4}};
    auto compressed = Compress(input);
    compressed.data()[0] ^= Byte{0x7f};
    BufferManaged output(input.size());
    BufferView output_view = output;

    auto result = ZstdDecompress(compressed, output_view);

    ASSERT_FALSE(result.ok());
    EXPECT_EQ(result.status().code(), absl::StatusCode::kDataLoss);
    EXPECT_NE(result.status().message().find("zstd frame"), std::string::npos);
}
}  // namespace
}  // namespace ysm::codec

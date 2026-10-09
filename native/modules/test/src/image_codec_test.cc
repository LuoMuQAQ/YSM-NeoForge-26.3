#include <gtest/gtest.h>

#include <array>

#include <codec/image.h>

namespace ysm::codec {
namespace {
TEST(ImageCodecTest, LosslessOutputRoundTripsThroughSharedDecoder) {
    std::array<Byte, 4 * 4 * 4> pixels{};
    for (std::size_t index = 0; index < pixels.size(); ++index) {
        pixels[index] = static_cast<Byte>(index * 37U);
    }

    BufferManaged encoded_bytes(64_KB);
    const auto encoded = ImageEncodeLossless(pixels, 4, 4, encoded_bytes);
    ASSERT_TRUE(encoded.ok()) << encoded.status();
#ifdef YSM_ANDROID
    EXPECT_EQ(encoded->info.format, ImageFormat::kZtx);
#else
    EXPECT_EQ(encoded->info.format, ImageFormat::kWebp);
#endif

    const auto bytes = Slice(encoded_bytes, 0, encoded->size);
    const auto probed = ImageProbe(bytes);
    ASSERT_TRUE(probed.ok()) << probed.status();
    EXPECT_EQ(probed->format, encoded->info.format);
    EXPECT_EQ(probed->width, encoded->info.width);
    EXPECT_EQ(probed->height, encoded->info.height);

    BufferManaged decoded(pixels.size());
    const auto status = ImageDecode(bytes, *probed, decoded);
    ASSERT_TRUE(status.ok()) << status;
    EXPECT_TRUE(Cmp(decoded, pixels));
}
}  // namespace
}  // namespace ysm::codec

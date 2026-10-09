#include <array>
#include <cstdint>
#include <span>
#include <utility>
#include <vector>

#include <gtest/gtest.h>

#include <codec/opus.h>

namespace ysm::codec {
namespace {
std::uint32_t OggCrc(std::span<const Byte> bytes) {
    std::uint32_t crc = 0;
    for (const auto value : bytes) {
        crc ^= static_cast<std::uint32_t>(value) << 24U;
        for (int bit = 0; bit < 8; ++bit) {
            crc = (crc << 1U) ^
                  ((crc & 0x80000000U) != 0 ? 0x04C11DB7U : 0U);
        }
    }
    return crc;
}

void AppendLe(std::vector<Byte>& output, std::uint64_t value, int size) {
    for (int index = 0; index < size; ++index) {
        output.push_back(static_cast<Byte>(value >> (index * 8)));
    }
}

void AppendPage(std::vector<Byte>& output, std::span<const Byte> packet,
                Byte flags, std::uint64_t granule, std::uint32_t sequence) {
    constexpr std::array<Byte, 4> kOgg{'O', 'g', 'g', 'S'};
    constexpr std::uint32_t kSerial = 7;
    const auto page_offset = output.size();
    output.insert(output.end(), kOgg.begin(), kOgg.end());
    output.push_back(0);
    output.push_back(flags);
    AppendLe(output, granule, 8);
    AppendLe(output, kSerial, 4);
    AppendLe(output, sequence, 4);
    AppendLe(output, 0, 4);
    output.push_back(1);
    output.push_back(static_cast<Byte>(packet.size()));
    output.insert(output.end(), packet.begin(), packet.end());

    const auto crc = OggCrc(std::span(output).subspan(page_offset));
    for (int index = 0; index < 4; ++index) {
        output[page_offset + 22 + index] =
            static_cast<Byte>(crc >> (index * 8));
    }
}

std::pair<std::vector<Byte>, std::size_t> MinimalOpus() {
    constexpr std::array<Byte, 19> kHead{
        'O', 'p', 'u', 's', 'H', 'e', 'a', 'd', 1, 1,
        0,   0,   0x80, 0xbb, 0,   0,   0,   0,   0};
    constexpr std::array<Byte, 16> kTags{
        'O', 'p', 'u', 's', 'T', 'a', 'g', 's',
        0,   0,   0,   0,   0,   0,   0,   0};
    constexpr std::array<Byte, 3> kAudio{0xf8, 0xff, 0xfe};

    std::vector<Byte> encoded;
    AppendPage(encoded, kHead, 0x02, 0, 0);
    AppendPage(encoded, kTags, 0, 0, 1);
    const auto audio_page_offset = encoded.size();
    AppendPage(encoded, kAudio, 0x04, 960, 2);
    return {std::move(encoded), audio_page_offset};
}

TEST(OpusAudioStreamTest, DecodeCanPropagateAllocationFailure) {
    EXPECT_FALSE(noexcept(std::declval<OpusAudioStream&>().Decode(
        std::declval<BufferView>())));
}

TEST(OpusAudioStreamTest, RequestsMoreInputWhenPageHeaderIsSplitAcrossChunks) {
    auto [encoded, audio_page_offset] = MinimalOpus();
    constexpr std::size_t kHeaderFragmentSize = 19;
    const auto split = audio_page_offset + kHeaderFragmentSize;

    OpusAudioStream decoder(960);
    decoder.Consume(BufferViewR(encoded).first(split));
    std::array<Byte, 960 * sizeof(std::int16_t)> pcm{};
    EXPECT_EQ(decoder.Decode(pcm), OpusAudioStream::kNeedInput);

    decoder.Consume(BufferViewR(encoded).subspan(split));
    decoder.EndInput();
    EXPECT_EQ(decoder.Decode(pcm), pcm.size());
    EXPECT_EQ(decoder.Decode(pcm), 0);
}
}  // namespace
}  // namespace ysm::codec

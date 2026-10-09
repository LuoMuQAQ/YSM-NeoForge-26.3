#pragma once

#include <algorithm>
#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <random>

#include <buffer.h>
#include <inline.h>

namespace ysm::legacy::v3::codec {
class Deobfuscator final {
   public:
    explicit Deobfuscator(BufferFixedViewR<56> password);
    Deobfuscator(const Deobfuscator&) = default;
    Deobfuscator& operator=(const Deobfuscator&) = default;
    Deobfuscator(Deobfuscator&&) noexcept = default;
    Deobfuscator& operator=(Deobfuscator&&) noexcept = default;
    ~Deobfuscator() = default;

    // The RNG loop must stay visible to callers: ThinLTO does not reliably
    // import it through an opaque PImpl boundary.
    YSM_INLINE BufferView Apply(BufferView bytes) {
        std::size_t offset = 0;
        while (offset < bytes.size() && available_ != 0) {
            bytes[offset++] ^= static_cast<Byte>(random_);
            random_ >>= 8U;
            --available_;
        }

        const auto bulk_end = offset + (bytes.size() - offset) /
                                           sizeof(std::uint64_t) *
                                           sizeof(std::uint64_t);
        while (offset != bulk_end) {
            XorLe64(bytes.data() + offset, generator_());
            offset += sizeof(std::uint64_t);
        }

        while (offset < bytes.size()) {
            if (available_ == 0) {
                random_ = generator_();
                available_ = 8;
            }
            bytes[offset++] ^= static_cast<Byte>(random_);
            random_ >>= 8U;
            --available_;
        }

        offset = 0;
        if (salt_header_size_ != salt_header_.size()) {
            const auto count =
                std::min(salt_header_.size() - salt_header_size_, bytes.size());
            Copy(Slice(bytes, 0, count),
                 Slice(salt_header_, salt_header_size_, count));
            salt_header_size_ += count;
            offset += count;
            if (salt_header_size_ == salt_header_.size()) {
                salt_remaining_ =
                    (static_cast<std::uint16_t>(salt_header_[0]) |
                     static_cast<std::uint16_t>(salt_header_[1]) << 8U) &
                    0x03ffU;
            }
        }
        if (salt_header_size_ == salt_header_.size() && salt_remaining_ != 0) {
            const auto skipped =
                std::min(salt_remaining_, bytes.size() - offset);
            salt_remaining_ -= skipped;
            offset += skipped;
        }
        return bytes.subspan(offset);
    }

   private:
    static YSM_INLINE void XorLe64(Byte* bytes, std::uint64_t mask) noexcept {
        if constexpr (std::endian::native == std::endian::little) {
            std::uint64_t value;
            std::memcpy(&value, bytes, sizeof(value));
            value ^= mask;
            std::memcpy(bytes, &value, sizeof(value));
        } else {
            for (std::size_t index = 0; index < sizeof(mask); ++index) {
                bytes[index] ^= static_cast<Byte>(mask >> (index * 8U));
            }
        }
    }

    std::mt19937_64 generator_;
    std::uint64_t random_{};
    std::uint8_t available_{};
    BufferFixed<2> salt_header_{};
    std::size_t salt_header_size_{};
    std::size_t salt_remaining_{};
};
}  // namespace ysm::legacy::v3::codec

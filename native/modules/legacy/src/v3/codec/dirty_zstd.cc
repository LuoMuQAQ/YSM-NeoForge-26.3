#include "dirty_zstd.h"

namespace ysm::legacy::v3::codec {
absl::StatusOr<NormalizedZstdBlockHeader> NormalizeDirtyZstdBlockHeader(
    BufferFixedViewR<3> bytes) noexcept {
    constexpr std::uint32_t kHeaderSalt = 0xD4E9;
    const auto dirty = static_cast<std::uint32_t>(bytes[0]) |
                       static_cast<std::uint32_t>(bytes[1]) << 8U |
                       static_cast<std::uint32_t>(bytes[2]) << 16U;
    const auto last = (dirty & 0x80U) != 0;
    const auto dirty_type = (dirty >> 5U) & 3U;
    if (dirty_type == 2) {
        return absl::DataLossError("Legacy zstd block type is reserved");
    }
    const auto block_size =
        ((dirty & 0x1FU) << 16U) | ((dirty >> 8U) ^ kHeaderSalt);
    const auto standard_type = dirty_type == 0 ? 2U : dirty_type == 1 ? 1U : 0U;
    const auto standard = static_cast<std::uint32_t>(last) |
                          (standard_type << 1U) | (block_size << 3U);
    return NormalizedZstdBlockHeader{
        {static_cast<Byte>(standard), static_cast<Byte>(standard >> 8U),
         static_cast<Byte>(standard >> 16U)},
        dirty_type == 1 ? 1U : block_size,
        last};
}
}  // namespace ysm::legacy::v3::codec

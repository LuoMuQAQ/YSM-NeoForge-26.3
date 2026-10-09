#pragma once

#include <absl/status/status.h>

#include <cstdint>

#include <buffer.h>
#include <pimpl.h>

namespace ysm::legacy::v3::codec {
inline constexpr std::uint64_t kLegacySourceByteLimit = 69'206'016;
inline constexpr std::uint64_t kLegacySummaryByteLimit = 16'777'216;

class ContainerReader final {
   public:
    explicit ContainerReader(BufferViewR source);
    ContainerReader(const ContainerReader&) = delete;
    ContainerReader& operator=(const ContainerReader&) = delete;
    ContainerReader(ContainerReader&&) = delete;
    ContainerReader& operator=(ContainerReader&&) = delete;
    ~ContainerReader();

    absl::Status Initialize();
    [[nodiscard]] std::uint64_t source_size() const noexcept;
    [[nodiscard]] bool ReadExact(BufferView destination);
    [[nodiscard]] bool Finish();
    [[nodiscard]] const absl::Status& status() const noexcept;
    [[nodiscard]] std::uint64_t offset() const noexcept;

   private:
    YSM_PIMPL_DECLARE(Impl, 6144)
};
}  // namespace ysm::legacy::v3::codec

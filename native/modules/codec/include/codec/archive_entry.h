#pragma once

#include "c_string_view.h"

namespace ysm::codec {
class ArchiveEntry {
    std::string full_name_;
    std::pair<uint16_t, uint16_t> file_name_range_;
    size_t size_;

   protected:
    ArchiveEntry(std::string&& full_name, size_t size)
        : full_name_(full_name.starts_with('/') ? full_name.substr(1)
                                                : std::move(full_name)),
          file_name_range_(GetFileNameRange()),
          size_(size) {}

   public:
    ArchiveEntry(const ArchiveEntry& other) = delete;
    ArchiveEntry& operator=(const ArchiveEntry&) = delete;

    ArchiveEntry(ArchiveEntry&& other) noexcept = default;
    ArchiveEntry& operator=(ArchiveEntry&&) noexcept = default;

    [[nodiscard]] CStringView FullName() const noexcept { return full_name_; }

    [[nodiscard]] CStringView FileName() const noexcept {
        return {full_name_.data() + file_name_range_.first,
                file_name_range_.second};
    }

    [[nodiscard]] size_t Size() const noexcept { return size_; }

   private:
    std::pair<uint16_t, uint16_t> GetFileNameRange() const {
        auto slash_pos = full_name_.find_last_of('/');
        if (slash_pos == std::string::npos) {
            return {0, full_name_.size()};
        }
        if (slash_pos == full_name_.size() - 1) {
            return {0, 0};
        }

        auto offset = slash_pos + 1;
        return {offset, full_name_.size() - offset};
    }
};
}  // namespace ysm::codec

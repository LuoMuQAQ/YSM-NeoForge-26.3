#pragma once

#include <absl/functional/function_ref.h>
#include <optional>

#include "archive_entry.h"
#include "buffer.h"
#include "err.h"
#include "fs.h"

namespace ysm::codec {
class Zip {
    void* zip_handle_;
    void* stream_handle_;
    bool stream_open_ = false;
    bool zip_open_ = false;
    std::optional<std::string> file_;

   public:
    class Entry : public ArchiveEntry {
        uint64_t crc32_;
        int64_t offset_;

        friend class Zip;

        Entry(std::string&& full_name, size_t size, uint64_t crc32,
              int64_t offset)
            : ArchiveEntry(std::move(full_name), size),
              crc32_(crc32),
              offset_(offset) {}
    };

    using EntryType = Entry;

    explicit Zip(BufferViewR zip_buffer);

    explicit Zip(const fs::path& path);

    Zip(const Zip& other);

    Zip& operator=(const Zip&) = delete;
    Zip(Zip&&) = delete;
    Zip& operator=(Zip&&) = delete;

    absl::Status Ok() const;

    ~Zip();

    absl::Status Visit(absl::FunctionRef<void(Entry&&)> visitor) const;

    absl::Status Extract(const Entry& entry, BufferManaged& buffer) const;

   private:
    void Open();
};
}  // namespace ysm::codec

#pragma once

#include <absl/functional/function_ref.h>
#include <span>

#include <codec/archive_entry.h>

#include "buffer_managed.h"
#include "err.h"
#include "fs.h"
#include "non_copyable.h"

namespace ysm::legacy::bundle {
class LegacyBundle : NonCopyable {
    BufferManaged cache_;
    mutable BufferManaged tmp_buf_;
    BufferViewR buf_;
    bool ver2_;

   public:
    class Entry : public codec::ArchiveEntry {
        BufferViewR buf_;

        friend class LegacyBundle;

        Entry(std::string full_name, size_t size, BufferViewR buf)
            : codec::ArchiveEntry(std::move(full_name), size), buf_(buf) {}
    };

    using EntryType = Entry;

    explicit LegacyBundle(const fs::path& path);
    explicit LegacyBundle(BufferViewR data);
    absl::Status Ok() const;

    absl::Status Visit(absl::FunctionRef<void(Entry&&)> visitor) const;
    absl::Status Extract(const Entry& entry, BufferManaged& buffer) const;
};
}  // namespace ysm::legacy::bundle

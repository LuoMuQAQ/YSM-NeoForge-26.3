#include "codec/zip.h"

#include <mz.h>
#include <mz_strm_mem.h>
#include <mz_strm_os.h>
#include <mz_zip.h>

#include "string_cvt.h"
#include "err.h"
#include "log.h"

namespace ysm::codec {
Zip::Zip(BufferViewR zip_buffer)
    : zip_handle_(mz_zip_create()), stream_handle_(mz_stream_mem_create()) {
    mz_stream_mem_set_buffer(stream_handle_,
                             const_cast<Byte*>(zip_buffer.data()),
                             static_cast<int32_t>(zip_buffer.size()));
    Open();
}

Zip::Zip(const fs::path& path)
    : zip_handle_(mz_zip_create()),
      stream_handle_(mz_stream_os_create()),
      file_(PathToU8(path)) {
    Open();
}

Zip::Zip(const Zip& other)
    : zip_handle_(mz_zip_create()),
      stream_handle_(other.file_ ? mz_stream_os_create()
                                 : mz_stream_mem_create()),
      file_(other.file_) {
    if (!file_) {
        const void* buf = nullptr;
        int32_t len = 0;
        if (mz_stream_mem_get_buffer(other.stream_handle_, &buf) != MZ_OK)
            [[unlikely]]
            return;
        mz_stream_mem_get_buffer_length(other.stream_handle_, &len);
        mz_stream_mem_set_buffer(stream_handle_, const_cast<void*>(buf), len);
        Open();
    }
}

void Zip::Open() {
    if (file_) {
        if (mz_stream_os_open(stream_handle_, file_->c_str(),
                              MZ_OPEN_MODE_READ) != MZ_OK) [[unlikely]]
            return;
    } else {
        if (mz_stream_mem_open(stream_handle_, nullptr, MZ_OPEN_MODE_READ) !=
            MZ_OK) [[unlikely]]
            return;
    }
    stream_open_ = true;
    if (mz_zip_open(zip_handle_, stream_handle_, MZ_OPEN_MODE_READ) != MZ_OK)
        [[unlikely]]
        return;
    zip_open_ = true;
}

Zip::~Zip() {
    if (zip_handle_) [[likely]] {
        if (zip_open_) [[likely]] {
            mz_zip_close(zip_handle_);
            zip_open_ = false;
        }
        mz_zip_delete(&zip_handle_);
        zip_handle_ = nullptr;
    }
    if (stream_handle_) [[likely]] {
        if (stream_open_) [[likely]] {
            if (!file_) {
                mz_stream_mem_close(stream_handle_);
            } else {
                mz_stream_os_close(stream_handle_);
            }
            stream_open_ = false;
        }
        if (!file_) {
            mz_stream_mem_delete(&stream_handle_);
        } else {
            mz_stream_os_delete(&stream_handle_);
        }
        stream_handle_ = nullptr;
    }
}

absl::Status Zip::Visit(absl::FunctionRef<void(Entry&&)> visitor) const {
    YSM_ASSERT(stream_open_ && zip_open_, Ok());
    auto result = mz_zip_goto_first_entry(zip_handle_);
    while (result == MZ_OK) {
        auto offset = mz_zip_get_entry(zip_handle_);
        mz_zip_file* entry_info;
        YSM_ASSERT(mz_zip_entry_get_info(zip_handle_, &entry_info) == MZ_OK,
                   absl::InternalError("Error obtaining zip entry info"sv));
        if (mz_zip_entry_is_dir(zip_handle_) != MZ_OK &&
            mz_zip_entry_is_symlink(zip_handle_) != MZ_OK) [[likely]] {
            visitor(Entry(entry_info->filename, entry_info->uncompressed_size,
                          entry_info->crc, offset));
        }
        result = mz_zip_goto_next_entry(zip_handle_);
    }
    return OkStatus();
}

absl::Status Zip::Extract(const Entry& entry, BufferManaged& buffer) const {
    YSM_ASSERT(stream_open_ && zip_open_, Ok());
    YSM_ASSERT(mz_zip_goto_entry(zip_handle_, entry.offset_) == MZ_OK,
               absl::NotFoundError("Entry not found"sv));
    YSM_ASSERT(mz_zip_entry_read_open(zip_handle_, false, nullptr) == MZ_OK,
               absl::InternalError("Error opening entry"sv));
    buffer.resize(entry.Size());
    auto size = mz_zip_entry_read(zip_handle_, buffer.data(),
                                  static_cast<int32_t>(buffer.size()));
    mz_zip_entry_read_close(zip_handle_, nullptr, nullptr, nullptr);
    YSM_ASSERT(size == entry.Size(), absl::DataLossError("Invalid data"sv));
    return OkStatus();
}

absl::Status Zip::Ok() const {
    YSM_ASSERT(stream_open_,
               absl::FailedPreconditionError("Zip stream not open."sv));
    YSM_ASSERT(zip_open_, absl::FailedPreconditionError("Zip not open."sv));
    return OkStatus();
}
}  // namespace ysm::codec

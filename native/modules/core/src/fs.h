#pragma once

#include <filesystem>

#include "buffer_managed.h"
#include "err.h"

namespace ysm {
namespace fs = std::filesystem;

absl::Status FileRead(const fs::path& path, BufferManaged& dst);

absl::Status FileWrite(const fs::path& path, BufferViewR file);

absl::StatusOr<size_t> FileSize(const fs::path& path);
}  // namespace ysm
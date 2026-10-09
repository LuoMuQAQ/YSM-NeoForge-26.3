#include "fs.h"

#include "string_cvt.h"

#include <algorithm>
#include <fstream>
#include <limits>
#include <string>
#include <string_view>
#include <system_error>
#include <utility>

namespace ysm {
namespace {
std::string PathMessage(const fs::path& path) {
    return "'" + PathToU8(path) + "'";
}

std::string ErrorMessage(std::string_view action, const fs::path& path,
                         std::string_view detail) {
    std::string result;
    result.reserve(action.size() + path.native().size() + detail.size() + 8);
    result.append(action);
    result.append(" ");
    result.append(PathMessage(path));
    if (!detail.empty()) {
        result.append(": ");
        result.append(detail);
    }
    return result;
}

absl::Status FileSystemError(std::string_view action, const fs::path& path,
                             const std::error_code& ec) {
    const auto message = ErrorMessage(action, path, ec.message());
    if (ec == std::errc::no_such_file_or_directory) {
        return absl::NotFoundError(message);
    }
    if (ec == std::errc::permission_denied) {
        return absl::PermissionDeniedError(message);
    }
    return absl::UnavailableError(message);
}
}  // namespace

absl::Status FileRead(const fs::path& path, BufferManaged& dst) {
    dst.resize(0);

    std::error_code ec;
    const auto status = fs::status(path, ec);
    if (ec) {
        return FileSystemError("Failed to stat", path, ec);
    }
    if (!fs::is_regular_file(status)) {
        return absl::InvalidArgumentError(ErrorMessage("Not a regular file", path, ""));
    }

    const auto size = fs::file_size(path, ec);
    if (ec) {
        return FileSystemError("Failed to get file size for", path, ec);
    }
    if (size > BufferManaged::kMaxSize) {
        return absl::ResourceExhaustedError(ErrorMessage("File too large", path, ""));
    }

    std::ifstream stream(path, std::ios::in | std::ios::binary);
    if (!stream.is_open()) {
        return absl::UnavailableError(ErrorMessage("Failed to open", path, ""));
    }

    dst.resize(size);
    auto* out = reinterpret_cast<char*>(dst.data());
    auto remaining = dst.size();
    while (remaining > 0) {
        const auto chunk = static_cast<std::streamsize>(std::min<size_t>(
            remaining,
            static_cast<size_t>(std::numeric_limits<std::streamsize>::max())));
        stream.read(out, chunk);
        const auto read = stream.gcount();
        if (read <= 0 || read != chunk) {
            return absl::DataLossError(ErrorMessage("Failed to read complete file", path, ""));
        }
        out += read;
        remaining -= static_cast<size_t>(read);
    }

    return OkStatus();
}

absl::Status FileWrite(const fs::path& path, BufferViewR file) {
    std::ofstream stream(path,
                         std::ios::out | std::ios::binary | std::ios::trunc);
    if (!stream.is_open()) {
        return absl::UnavailableError(ErrorMessage("Failed to open", path, ""));
    }

    auto* in = reinterpret_cast<const char*>(file.data());
    auto remaining = file.size();
    while (remaining > 0) {
        const auto chunk = static_cast<std::streamsize>(std::min<size_t>(
            remaining,
            static_cast<size_t>(std::numeric_limits<std::streamsize>::max())));
        stream.write(in, chunk);
        if (!stream) {
            return absl::DataLossError(ErrorMessage("Failed to write complete file", path, ""));
        }
        in += chunk;
        remaining -= static_cast<size_t>(chunk);
    }

    stream.close();
    if (!stream) {
        return absl::DataLossError(ErrorMessage("Failed to close", path, ""));
    }

    return OkStatus();
}

absl::StatusOr<size_t> FileSize(const fs::path& path) {
    std::error_code ec;
    const auto status = fs::status(path, ec);
    if (ec) {
        return FileSystemError("Failed to stat", path, ec);
    }
    if (!fs::is_regular_file(status)) {
        return absl::InvalidArgumentError(ErrorMessage("Not a regular file", path, ""));
    }

    const auto size = fs::file_size(path, ec);
    if (ec) {
        return FileSystemError("Failed to get file size for", path, ec);
    }
    if (size > std::numeric_limits<size_t>::max()) {
        return absl::ResourceExhaustedError(ErrorMessage("File too large", path, ""));
    }

    return static_cast<size_t>(size);
}
}  // namespace ysm

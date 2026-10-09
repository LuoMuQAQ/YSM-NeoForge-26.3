#pragma once

#include <absl/status/statusor.h>

#include <cstdint>

#include <legacy/v3.h>

#include <v3/container/model.h>

namespace ysm::legacy::v3::conversion {
absl::StatusOr<ImportResult> Convert(container::LegacyModel model,
                                     std::uint64_t source_size);
}  // namespace ysm::legacy::v3::conversion

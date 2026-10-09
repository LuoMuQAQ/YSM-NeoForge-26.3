#pragma once

#include <absl/status/status.h>

#include "model.h"

namespace ysm::legacy::v3::container {
[[nodiscard]] absl::Status PrepareImage(LegacyImage& image, ImageRole role);

[[nodiscard]] absl::Status ValidatePreparedImage(const LegacyImage& image,
                                                 ImageRole role);
}  // namespace ysm::legacy::v3::container

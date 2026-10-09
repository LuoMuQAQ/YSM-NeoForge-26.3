#pragma once

#include <absl/status/statusor.h>

#include <buffer.h>

#include "model.h"

namespace ysm::legacy::v3::codec {
class ContainerReader;
}  // namespace ysm::legacy::v3::codec

namespace ysm::legacy::v3::container {
using Reader = codec::ContainerReader;

absl::StatusOr<LegacyModel> Decode(Reader& reader);
absl::StatusOr<LegacyModel> Decode(BufferViewR bytes);
}  // namespace ysm::legacy::v3::container

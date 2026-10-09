#pragma once

#include <absl/status/statusor.h>

#include <memory>

#include <legacy/v3.h>

namespace ysm::legacy::java {
using v3::ImageMetadata;
using v3::ImportResult;
using v3::Payload;
using v3::PayloadEncoding;
using v3::PayloadKind;

class ResultOwner final {
   public:
    static absl::StatusOr<std::unique_ptr<ResultOwner>> Create(
        ImportResult result);

    ResultOwner(const ResultOwner&) = delete;
    ResultOwner& operator=(const ResultOwner&) = delete;
    ResultOwner(ResultOwner&&) = delete;
    ResultOwner& operator=(ResultOwner&&) = delete;
    ~ResultOwner() = default;

    [[nodiscard]] BufferViewR descriptor() const noexcept {
        return descriptor_;
    }

    [[nodiscard]] const std::vector<Payload>& payloads() const noexcept {
        return result_.payloads;
    }

    [[nodiscard]] const void* direct_address(
        const Payload& payload) const noexcept {
        return payload.bytes.empty() ? &empty_sentinel_ : payload.bytes.data();
    }

   private:
    ResultOwner(ImportResult result, BufferManaged descriptor) noexcept
        : result_(std::move(result)), descriptor_(std::move(descriptor)) {}

    ImportResult result_;
    BufferManaged descriptor_;
    Byte empty_sentinel_{};
};

absl::StatusOr<BufferManaged> EncodeDescriptor(const ImportResult& result);
}  // namespace ysm::legacy::java

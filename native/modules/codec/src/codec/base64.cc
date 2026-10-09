#include "codec/base64.h"

#include <cryptopp/base64.h>

namespace ysm::codec {
absl::StatusOr<size_t> Base64Encode(BufferViewR input, BufferView output) {
    YSM_ASSERT(!input.empty(), 0);
    YSM_ASSERT(output.size() >= Base64EncodeOutputMaxSize(input.size()),
               absl::InvalidArgumentError("Output is not large enough"sv));
    try {
        auto sink = new CryptoPP::ArraySink(output.data(), output.size());
        CryptoPP::ArraySource source(input.data(), input.size(), true,
                                     new CryptoPP::Base64Encoder(sink));
        return sink->TotalPutLength();
    } catch (const CryptoPP::Exception& e) {
        return absl::InternalError(e.what());
    }
}

absl::StatusOr<size_t> Base64Decode(BufferViewR input, BufferView output) {
    YSM_ASSERT(!input.empty(), 0);
    YSM_ASSERT(output.size() >= Base64DecodeOutputMaxSize(input.size()),
               absl::InvalidArgumentError("Output is not large enough"sv));
    try {
        auto sink = new CryptoPP::ArraySink(output.data(), output.size());
        CryptoPP::ArraySource source(input.data(), input.size(), true,
                                     new CryptoPP::Base64Decoder(sink));
        return sink->TotalPutLength();
    } catch (const CryptoPP::Exception& e) {
        return absl::InternalError(e.what());
    }
}
}  // namespace ysm::codec
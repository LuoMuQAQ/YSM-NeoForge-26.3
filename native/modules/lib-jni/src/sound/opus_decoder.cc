#include <cstdint>
#include <memory>

#include <codec/opus.h>
#include <err.h>
#include <java/buffer.h>
#include <java/entry.h>

namespace ysm::lib::sound {
namespace {
codec::OpusAudioStream& Cast(jlong ptr) {
    return *reinterpret_cast<codec::OpusAudioStream*>(
        static_cast<std::intptr_t>(ptr));
}
}  // namespace

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/sound/OpusDecoder;nCreate(J)J",
    (expected_frames)) {
    YSM_ASSERT(expected_frames >= 0,
               absl::InvalidArgumentError("Negative Opus frame count"sv));
    auto decoder = std::make_unique<codec::OpusAudioStream>(
        static_cast<uint64_t>(expected_frames));
    return reinterpret_cast<jlong>(decoder.release());
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/sound/OpusDecoder;nFeed(JLjava/nio/ByteBuffer;)Z",
    (ptr, input)) {
    YSM_ASSERT(ptr != 0 && input != nullptr,
               absl::InvalidArgumentError("Null Opus decoder input"sv));
    YSM_DECLARE_OR_RETURN(bytes, java::GetDirectBuffer(env, input));
    Cast(ptr).Consume(bytes);
    return OkStatus();
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/sound/OpusDecoder;nEndInput(J)V",
    (ptr)) {
    YSM_ASSERT(ptr != 0,
               absl::InvalidArgumentError("Null Opus decoder"sv));
    Cast(ptr).EndInput();
    return OkStatus();
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/sound/OpusDecoder;nDecode(JLjava/nio/ByteBuffer;)I",
    (ptr, output), codec::OpusAudioStream::kError) {
    YSM_ASSERT(ptr != 0 && output != nullptr,
               absl::InvalidArgumentError("Null Opus decoder output"sv));
    YSM_DECLARE_OR_RETURN(bytes, java::GetDirectBuffer(env, output));
    return Cast(ptr).Decode(bytes);
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/sound/OpusDecoder;nDestroy(J)V",
    (ptr)) {
    YSM_ASSERT(ptr != 0,
               absl::InvalidArgumentError("Null Opus decoder"sv));
    delete &Cast(ptr);
    return OkStatus();
}
}  // namespace ysm::lib::sound

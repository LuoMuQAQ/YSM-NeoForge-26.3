#pragma once

#include "buffer.h"
#include "non_copyable.h"
#include "pimpl.h"

namespace ysm::codec {
class OpusAudioStream : public NonCopyable {
    YSM_PIMPL_DECLARE(Impl, 512)
   public:
    static constexpr int32_t kError = -1;
    static constexpr int32_t kNeedInput = -2;

    explicit OpusAudioStream(uint64_t expected_frames);
    ~OpusAudioStream();

    void Consume(BufferViewR data_buffer);

    void EndInput() noexcept;

    int32_t Decode(BufferView dst_buffer);
};
}  // namespace ysm::codec

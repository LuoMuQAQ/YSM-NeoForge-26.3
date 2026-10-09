# Opus 流解码

[返回基建总览](../infrastructure_cn.md)

主要头文件：`<codec/opus.h>`。

`codec::OpusAudioStream` 增量解码单一 Ogg/Opus logical stream，输出 48 kHz mono signed 16-bit PCM；双声道输入下混为 mono。`MiniOgg` 是内部 packet parser，不作为普通音频入口。

## 输入与结束协议

构造参数 `expected_frames` 是经过媒体时间轴验证、扣除 pre-skip 和尾部裁剪后的精确输出 frame 数，按 48 kHz 计数；它参与最终完整性检查和尾部裁剪，不能用估算时长或容量上限代替。

- `Consume(bytes)` 复制输入，返回后调用方可释放或复用输入；按需 feed，避免累计未消费数据。
- 输入源确认结束后调用 `EndInput()`，随后继续 `Decode` 排空输出。输入暂时不足时不要提前宣告结束。
- `EndInput()` 或解码失败后不能再 `Consume`，否则抛 `std::logic_error`。失败对象不复用；新流创建新实例。
- 构造、feed 和 decode 都可能因分配失败抛异常；它们不是 `noexcept`。在可靠边界收敛异常，不将这些调用放入要求无分配、无异常的音频内层。

## 输出契约

`Decode(dst)` 要求至少 2 bytes 的可写空间；奇数容量末尾的 1 byte 不使用。不足一个 sample 会使 decoder 进入失败状态。

| 返回值 | 调用方处理 |
| --- | --- |
| `> 0` | 仅消费该长度的 PCM 前缀，然后继续 decode；长度单位为 byte，总是 2 的倍数 |
| `kNeedInput` | 等待或 feed 下一片；若源已结束，调用 `EndInput()` 后继续 decode |
| `0` | 已通知结束、排空输出且通过完整性检查，正常 EOF |
| `kError` | 内容或输出参数错误；丢弃本次输出并结束流 |

`EndInput()` 本身不校验完整性。此前成功产出的 PCM 也不证明最终成功；仍需读取到 `0`，截断或 frame 数不匹配会返回错误。

## 最小消费循环

下面用有限的输入分片演示完整协议。`consume_pcm` 必须在返回前消费或复制 borrowed PCM；生产流仅在源确认 EOF 时执行同样的 `EndInput` 分支。

```cpp
#include <absl/functional/function_ref.h>
#include <codec/opus.h>
#include <err.h>
#include <span>

namespace ysm {
absl::Status DecodeOpus(
    std::span<const BufferViewR> chunks, uint64_t expected_frames,
    absl::FunctionRef<absl::Status(BufferViewR)> consume_pcm) {
    codec::OpusAudioStream decoder(expected_frames);
    BufferFixed<4096> pcm;
    size_t next_chunk = 0;
    bool input_ended = false;

    for (;;) {
        const auto result = decoder.Decode(pcm);
        if (result > 0) {
            YSM_RETURN_IF_ERROR(consume_pcm(
                Slice(pcm, 0, static_cast<size_t>(result))));
        } else if (result == codec::OpusAudioStream::kNeedInput) {
            if (next_chunk < chunks.size()) {
                decoder.Consume(chunks[next_chunk++]);
            } else if (!input_ended) {
                decoder.EndInput();
                input_ended = true;
            } else {
                return absl::InternalError("Decoder requested input after EOF");
            }
        } else if (result == 0) {
            return OkStatus();
        } else {
            return absl::DataLossError("Invalid or incomplete Opus stream");
        }
    }
}
}  // namespace ysm
```

<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 格式、Schema 与编解码已知问题

本页记录当前实现与[格式一致性要求](../../standards/conformance.md)之间的重要缺口，不修改标准本身。

## 一致性与转换

- Asset Container 与 Model Schema（当前均为 `0.1.0-unstable`）均缺跨实现 portable binary golden；当前 Java generic container 与 model profile 都使用该精确版本，同一实现自产自读不能证明标准版本互操作。
- Canonicalizer profile 已覆盖冻结 capture、无符号 UTF-8 排序和 little-endian records，但仍缺跨 source adapter 的公开 golden。
- Verification payload 的 hash/signLength 结构和精确 preamble 已接线；opaque `sign` 不提供作者、授权或抗篡改保证。固定字符串 ASCII、图像 payload 长度及 metadata 一致性仍有严格校验偏差。
- raw 几何转换会误写 visible bounds offset，可能使转换后的模型范围信息错误。
- Model Schema 允许 unspecified 且无 thumbnail；Local Catalog 另按实际来源拒绝缺有效内嵌 preview 的 direct 成品，内部 raw/builtin/legacy 转换结果保留 schema 合法缺图。该来源矩阵已有 Java 局部测试，尚缺完整产品入口与跨平台媒体组合验收。

## Proto presence

- 全部 source Proto 已完成正向证据审计；当前 Model Schema import closure、统一网络协议和 baked manifest 均由对应快照或 fixture 覆盖。证据来自 legacy V3 格式规范、legacy native serializer implementation、source adapter 的明确省略分支和协议 absence 契约；这些证据只能推出“可缺省”，不能从 C++ 普通成员或嵌套聚合初始化推出 non-null。当前 strict profile 仍要求其余 ordinary singular 在 `build()` / `parseFrom(...)` 时存在，并禁止 partial value 进入业务路径。
- `Info.metadata` 与 `Author.avatar` 继续以显式 `optional` 表达历史合法缺省；`Info.properties` 与 `Info.settings` 是 current schema 的 ordinary required message，raw 与 legacy producer 都必须物化它们，immutable builder/parse 在 publication 前拒绝缺失。Preview 与 icon source 独立以显式 `optional` 表达命名图像 absence。
- 历史 `Animation.start_delay`、`loop_delay` 与 `override_previous_animation` 是显式 nullable，当前 raw parser 也能读取，但业务层没有 consumer；当前 Model Schema 不承载它们，转换器按裁决静默丢弃且不告警。这不改变当前可观察语义；未来若支持，必须以新 schema version 和新字段显式引入，不能重新解释既有 current container。
- 其他显式 absence 已由现有表示或验证规则承载：`BoneKeyFrame.post` 用 empty repeated 表示等于 `pre`，PBR extension、blend transition linear value、export/preview/GUI resource 与 PlayerState DELTA 使用已标记的 `optional`，replacement asset absence 由 map/repeated membership 表达；缺失顶层 player 则由当前 M2 的 player-target 不变量拒绝。这里不据此反推未标字段为 non-null。
- Model Schema import closure 当前有 8 个 repeated numeric/bool primitive，均位于 geometry Proto 且显式声明 `[packed = true]`；当前没有 repeated enum，repeated string/message 与 map 不属于该门禁。Snapshot checker 已把该显式标记作为 source/snapshot 一致性之外的独立门禁。

## Codec、导入与平台

- `ZTX` 是未冻结的私有图像格式；PNG、JPEG、WebP、AVIF 与各运行平台的 encode / decode 组合尚未系统验证。
- legacy v1/v2 已接到 archive→raw→current 路径，raw/current 音频写出与重开有独立 fixture 自动化；真实语料中的 12 个文件已完成格式分类，但缺可信历史声音包装的 Java 端到端验证。v3 加密容器已接通[同步流式单向导入](../../architecture/asset-pipeline/conversion-and-export.md#历史输入的单向投影)，native 真实语料覆盖 185 个容器、inner version 1/4/9/15 和 778 个 sound field；Java current staging/reopen 与音频 projector 自动化已通过，仍缺可信历史 v3 包装到 Java、远端、实际游戏和跨平台产品入口的端到端验收。
- MSVC 19.51 单独处理 GNU-style `asm volatile` 的 Release build 存在工具链阻塞；固定官方源码已通过 clang-cl、MSVC CRT/STL 和 Windows SDK 构建 Windows x64 Native，并完成真实 JNI 初始化及模型物化。该工具链结果不证明其他平台或纯 MSVC 构建已通过。
- Codec 与 archive 验证尚未覆盖全部产品平台；存在 native 构建目标或库文件不等于产品入口已经接通或验收。

Model Schema 的音频 profile 已启用：普通 raw producer 与 legacy projection 都能写出 `Common.sounds`/`stream-N`，Java staging/reopen 会复核 descriptor、logical bytes 与媒体解释，客户端按需取得并播放。当前自动化使用独立 Ogg Vorbis/Opus fixture 覆盖 raw/current、legacy projector、JNI 解码和 runtime 局部边界，但真实 legacy v1/v2/v3 包装、remote session、Minecraft/OpenAL 设备与跨平台组合仍缺端到端验收；完整边界见[当前支持状态](../support-and-verification.md)。

<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 网络与同步已知问题

本页只记录当前实现与[当前网络协议](../../standards/protocol-v1/README.md)的落地缺口。正常职责见[网络架构](../../architecture/network/README.md)。

- 当前协议是 `0.3.0-unstable`，只支持同步构建且没有旧 wire reader。真实 Forge 双端的同构建 full/delta/resource/player-state、旧版本 peer 拒绝、secondary-login 单 session 及多人/重连/退出世界组合仍缺机器可判定验证。
- Exact-connection model session、四 collection typed publication、metadata/model/presentation 三类 request、七类 typed fragment、本地 assembly/terminal owner、逐项授权、server-private forced selection 和唯一 global dispatch 已通过自动化门禁；descriptorless preview 还覆盖动态 final range、overlap/gap、16 MiB 单图、128 MiB 实收聚合、内嵌/cache/`UNAVAILABLE` 与 icon/cover descriptor 隔离。现有 unit/contract 与 thin Forge host 证据不是新 preview 的真实 LAN frame 或完整产品 oracle。
- 协议有意不维护 catalog revision、缺号检测或 resync；exact connection 会丢弃重连前的迟到工作，但同一 active connection 内普通消息的残余跨消息乱序仍可能保留较旧 collection view，需要实机确认。
- Local、YSM channel 缺席、版本不匹配、intrinsic-default-only fail-closed，以及迟到 typed fragment/player-state/entity/notice、preview replacement 与页面关闭的真实 Forge 排队组合仍需实机覆盖。
- 独立 Backend 未实现，不属于当前验证范围。
- 用户反馈完全关闭游戏后模型选择回到默认。目录未就绪时的 resolve 曾把缺件选择直接改写为默认；现在运行兜底保留保存意图，初次握手与目录发布后经当前 session 校验恢复，显式选择可替换它，见[存档选择与运行兜底](../../architecture/network/player-state.md#存档选择与运行兜底)。当前存档中已经被旧构建覆盖的选择无法由该机制推断恢复；重新选择后保存、退出世界、完整重启及多人权限组合仍待实机确认。

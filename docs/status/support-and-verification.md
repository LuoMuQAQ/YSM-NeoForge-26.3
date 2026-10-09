<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 当前支持状态

“已接线”只表示主要代码链路存在，不代表稳定、完整或通过实机、跨平台及跨实现验证。格式与协议要求以[一致性标准](../standards/conformance.md)为准。

当前 Windows x86_64 / Minecraft 26.3 / NeoForge 26.3.0.58-beta 的公开测试包收到维护者“测试基本正常”的总体反馈。该反馈支持当前基本使用情况，不覆盖全部模型、GUI 缩放、声音设备、光影、可选模组、旧世界保存重进或 LAN 首次资源传输；下列逐项验收缺口继续保留。

| 能力 | 当前边界 |
|---|---|
| Minecraft 26.3 / NeoForge | Java 25 的全部生产源码和分发 `shadowJar` 已构建通过，包含默认模型契约、builtin index 与非默认内置模型完整物化检查。正式 payload/attachments、GUI/SDL3/HUD、实体/预览/截图及第一人称提交已接线；普通副手、真实目标 Sophisticated 背包和 Iris PBR 上传/采用边界已适配。所有已登记必需 Mixin 通过目标字节码静态检查，实际应用、视觉、声音设备、可选联动组合与 LAN 尚未验收。宿主退出清理、离线世界副本工具和旧 NeoForge 玩家及投射物/载具数据桥已接线；模型选择、授权、收藏、Molang 以及实际世界升级保存尚待实机确认。鞘翅已接入宿主 WINGS 装备资源层及 HUD/GUI avatar 状态，尺寸与背部锚点已修复；全部模型与姿态组合仍待逐项确认。旧 ForgeCaps 桥、特殊键覆盖、第一人称背景网格和部分女仆功能仍有缺口。可选联动的编译依赖不随 JAR 分发；未有目标 provider 证据的反射分支只能局部降级，不能称为恢复支持。 |
| Asset Container / Model Schema `0.1.0-unstable` | 标准已定义 canonical `ModelId`、扩展 verification payload、preamble 与完整模型 profile；Java 主路径已接线核心结构、direct/zstd、BLAKE3、冻结 capture 和严格 preamble，当前 generic container 与 model profile 均精确写出/接受 `0.1.0-unstable`，尚未完成该版本跨实现 conformance，见[格式问题](known-issues/format-and-schema.md) |
| ED25519 verification | 不支持 |
| Protocol `0.3.0-unstable` | NeoForge payload 适配沿用原目标主线的 bounded frame、exact-connection session closure、四 collection typed full/delta、三类 model-distribution request、七类 typed fragment、server-private forced selection、全局 dispatch 及既有玩家/entity/control path 已接线。Player-state report/update 与收藏快照已退出 sequence/revision，报告机会采用 best-effort，真实 FULL baseline 留在 exact model session；资源侧由获取前已存在的 typed owner 将完整 packet 集合提交给唯一 dispatch，bytes/chunk/file 共用有界 range 生命周期。Schema/registry、typed producer/consumer、业务 bounds/assembly、replacement/late outcome、per-child admission/cancel、三种 source retry/close、dispatch closure 与完整 unsigned transfer ID range 已有自动化覆盖；真实 Forge 的 no-channel/buffer handoff、同 UUID replacement、逐观察者异常、LAN/集成 server frame、真正 version mismatch、长期资源回收及 secondary-login 单 session 仍缺机器可判定 observation，见[网络问题](known-issues/network.md) |
| Java 模型管理 | 进程 Catalog 的 startup default、首次消费登记/scan/prune、逐项验证与 owner-tick 增量发布、direct 精确读取实例、remote/converted 非破坏性坏读、exact session 查询释放、独立 runtime、完整 target lease 与 30/60 unused LRU 已接线；GUI/local/每个 remote entity 的 0.3/0.7 秒连续需求也已接入。自动化与 thin Forge host 覆盖主要局部分支；真实大目录、多进程 prune、广泛 reload、长期资源回收、成本对照和完整 LAN 交错仍需独立验收，见[模型管理问题](known-issues/model-management.md) |
| Preview 与 export | Direct 成品内嵌 preview 准入、内部转换缺图例外、`ContainerId` 独立图片 cache、特化 presentation、真实 target/bake→256×256 私有 target 绘制和异步 readback→worker encode，以及 `/ysm export` 的直接输入与 `.mxc` 完整容器重封装已接线。读回完成前不 reset GUI entity、不销毁 target、不释放父 lease；迟到完成不复活已结束请求。Client tick 的单一总 admission 覆盖 probe、Ready/Flight 或 detached load、host pixels、encode/persist/export 和终态；专服已有图片 export 由 server tick 接纳完整 worker，cold miss 明确失败。Owner-pause、共享容量、terminal release、host/encode 失败关闭、导出 route/后缀和当前容器再发现已有自动化覆盖；真实画面、游戏内命令、完整失败矩阵、长期图片空间与冷暖成本仍未完成最终验收。 |
| Java 动画运行时 | animation、coded / Bedrock / hybrid controller、玩家状态、骨骼输出与模型声音触发主链已接线，模型 execution-bearing 字段携带 Molang source 字符串，由 `com.elfmcys.ysm.molang` 的 lexer/parser/`ExpressionEvaluator` 与 `client.animation.molang` 的 binding 在本地逐次解析和求值；Roaming 由 `LocalRoamingStruct` / `RemoteRoamingStruct` 记录并消费 full 与 delta，`AnimationProcessor.putRemoteStruct` 把它们交给 entity 的 processor。Java `test` task 最近执行 575 项，0 failure、0 error、9 skipped，覆盖 animation/controller Proto、模型导入、Roaming 和模型音频局部边界。表达式解释器仍没有专门单元测试，也没有 Minecraft/Forge world 级、多 pass、`ysm.sync`、config-action、声音 once-only 或性能测量；不能声明运行时验收通过，见[动画问题](known-issues/animation.md) |
| Native CPU renderer | bake / extract / render、SIMD 与多线程顶点生成已接线；固定官方 Native 源码的 Windows x64 Release 已用 clang-cl/MSVC CRT/SDK 构建并通过真实 JNI 初始化与内置模型物化；MSVC 单独处理 GNU-style `asm volatile` 的失败是另一工具链边界。目标 JAR 沿用同一配对 DLL，本次 Java 适配未修改 native ABI；跨平台、自动化基线和视觉验收仍未收敛，见[格式与构建问题](known-issues/format-and-schema.md#codec导入与平台)和[渲染问题](known-issues/rendering.md) |
| 图像 codec | Native 解码覆盖 PNG、JPEG、WebP、AVIF、ZTX，编码仅覆盖 WebP、AVIF、ZTX；平台组合未完整验证，JPEG XL 不支持 |
| 当前产品运行入口 | Java 入口覆盖 Windows x64（最低 Windows 10）、GNU/Linux x64 和专用环境下的 Android arm64；x64 要求 SSE4.1，x86-64-v1 与 Windows 7 尚不支持；macOS 与其他架构尚不可用，native 构建目标不等于产品支持；目标基线与适配边界见[平台适配现状与原则](../product-decisions/requirements/req-platform-availability.md) |
| legacy v1/v2 | Archive 到统一 raw parser、当前音频写出与重开已有自动化；真实语料中 12 个文件已按 header 精确识别为 raw，但仍缺带可信历史 provenance 的 Java 端到端声音包装验证 |
| **模型音频** | **Raw directory/archive、current export/reopen、legacy projection、严格 Ogg Vorbis/Opus 解释、真实 JNI Opus 解码、按需本地/remote chunk 取得、64 MiB encoded/PCM 统一保留以及 Minecraft `AudioStream` 播放主链已接线。冻结 fixture 覆盖阈值、精确 frame、冷/热一致、独立播放、两周期 loop、取消与 channel handoff 局部边界；真实 legacy v1/v2/v3 包装、remote session、Minecraft/OpenAL 设备、stream pool N+1、heap retaining path 与 GC/Cleaner 最终回收尚未验收，不能据自动化声明端到端支持已通过** |
| v3 加密模型 | 同步、流式的单向导入已接线；native 真实语料覆盖 185 个加密容器及 inner version 1/4/9/15，全部完成解密、反混淆、解压、反序列化和 current projection，并覆盖 778 个 sound field（777 个保留、1 个未知格式按裁决省略）。Java current staging/reopen 与音频 projector 自动化已通过，但仍缺把可信历史 v3 包装接到 Java、远端和实际游戏播放的端到端、跨平台验收，见[格式问题](known-issues/format-and-schema.md) |
| 独立 Backend / 通用外部模型源 / GPU renderer | [Backend](../future/independent-backend.md)、[外部模型源](../future/external-model-sources.md)与 [GPU renderer](../future/gpu-compute-renderer.md)均未实现 |

26.3 模型播放的 Vorbis decoder 使用宿主 `JOrbisAudioStream`，保持严格 channel/rate/frame 检查与 stereo→mono 规则；没有对帧数差做截断。用户花火样本已通过目标 decoder 的完整离线资源检查，白露空骨骼名仍被 native bake 拒绝。离线声音解码与设备播放是不同证据。下文所引原目标主线 `test` 结果未在本次 Java 25 移植中重跑。

总体使用风险见[项目概览](../README.md)。

第一人称单侧提取曾在进入世界渲染时触发 locator 类型不匹配；模型装配器现按几何用途显式选择 locator，消费端保留类型门禁。普通持物的额外空手姿态补交已移除，Z 轮盘文字改用不透明 ARGB 并在背景之后提取；用户反馈基本正常，完整重进/地图/惯用手仍未逐项验收，见[动画问题](known-issues/animation.md)。左上角预览的配置 yaw 偏移已恢复，新的倾角和设置页面交互待用户复核，见[渲染问题](known-issues/rendering.md)。此前模型页/加载正面反馈保留其原范围。

内置魔法酒狐的空手伸出范围差异已定位到移植新增的宿主肩部对齐。当前恢复官方源码的第一人称屏幕坐标偏移，单侧提取、类型门禁与宿主物品路径保留；用户确认当前酒狐显示正常，其他模型、惯用手及地图组合仍待验收，见[动画问题](known-issues/animation.md)。分类文件夹卡片文字遗漏的 ARGB alpha 及封面与标题分区已修正，普通模型卡片缺字的覆盖范围和修复画面仍需实机核查，见[渲染问题](known-issues/rendering.md)。

一次目标客户端启动在 `ArrowEntityAccessor` 的 Mixin prepare 阶段失败：访问器接口中的普通 `default` 方法导致它被分类成普通接口 Mixin，不能应用到 `Arrow` 类。该 helper 已移至调用方，保留 custom-effects 查询语义；字段/方法静态匹配未覆盖这类分类错误。后续静态核对须包含接口 subtype 与目标类型。后续实际运行已越过该 prepare 阶段并进入主菜单与世界；全部 Mixin application 与视觉行为仍需按场景验收。

后续运行已越过上述 Mixin prepare，并完成 native 与 default CPU 内容加载，但停在 sided setup。源码与宿主加载器表明 required client bootstrap 原来从 `modloading-sync-worker` 等待 client-queue GPU publication，而客户端仍等待该加载阶段完成。初始化已移到设置客户端线程后、第一次 tick 前的 `ClientStartedEvent`，由 render owner 排空 required publication；完整 Ready 后才发布 service。报告不含线程转储，不能据此排除其他停滞原因；后续日志确认 required service 已 Ready，用户已进入主菜单与世界。矩阵字节序及重复背景提取修复后，用户确认模型正常加载、Alt+Y 正常打开；卡片名称和第一人称双臂/姿态仍有反馈，后续源码修复尚待用户复核，见[渲染问题](known-issues/rendering.md)与[动画问题](known-issues/animation.md)。

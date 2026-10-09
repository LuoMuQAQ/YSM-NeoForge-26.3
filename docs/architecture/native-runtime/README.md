# Native 运行边界

> **适用问题**：native 加载、JNI 绑定、计算能力分工及跨语言失败边界；**不包含**：模型业务权威、公开格式定义、GPU draw 和平台支持矩阵。

Native 是由 Java 调用的能力层。`com.elfmcys.ysm.natives` 提供 Java 包装；`ysm::lib` 只保留面向这些包装的 JNI glue，`ysm::java` 提供签名、buffer、引用和 handle 机制。具体能力已按职责分开：通用 runtime 基础不承载 codec 或 renderer，`ysm::codec` 负责 hash、压缩、archive、图像和音频，`ysm::gfx::bake` / `ysm::gfx::renderer` 负责烘焙与 CPU 顶点输出，生成的 Proto 类型形成独立 schema seam，`ysm::legacy` 负责历史容器解码与单向投影。Legacy 结果返回后仍进入 Java 当前容器管线。

## Native 内部分层

| 边界 | 当前职责 | 依赖方向 |
|---|---|---|
| 通用 runtime 基础 | buffer、分配、CPU capability、日志、状态与通用数据结构 | 位于底层，不依赖 codec、renderer、JNI 或 legacy 业务 |
| 生成 Proto 类型 | Native 所需的当前 schema 值类型与序列化接口 | 独立于运行算法；由渲染、legacy 和最终 glue 按需消费 |
| `ysm::codec` | hash、压缩、archive、图像、音频及相关二进制算法 | 依赖通用 runtime，不依赖 JNI glue |
| `ysm::gfx::bake` / `ysm::gfx::renderer` | baked 数据、数学与 CPU render | 依赖通用 runtime；只在需要 schema 输入的实现边缘消费生成 Proto 类型 |
| `ysm::java` | JNI descriptor、注册、引用、buffer 与 typed handle 基础设施 | 依赖通用 runtime，不实现领域算法 |
| `ysm::legacy` | legacy bundle/v3 解码、历史 codec 与 current schema 投影 | 复用通用 codec，并在投影边缘消费生成 Proto 类型 |
| `ysm::lib` 与 legacy Java bridge | 把上述能力适配成唯一 Java 可加载的共享库，并拥有 JNI 返回值/结果 owner 的边界 | 作为最外层聚合；算法不得反向依赖该 glue |

这个拆分约束的是依赖方向，不增加第二套模型 authority。Java 仍决定来源、identity、预算、存储、发布和生命周期；Native 子系统之间也不能借共享库聚合关系交换这些业务状态。

## 加载与一次性绑定

`NativeLibUtil.load()` 选择并准备当前环境的库。`loadLibraryOnLifetimeThread()` 在专用 daemon holder thread 调用 `System.load()` 和 `NativeRuntime.initialize()`，将初始化结果交回启动方；成功后该线程保持存活，维持分配器所需的加载线程生命周期。它不承担模型工作队列或游戏侧退出协调。

```mermaid
flowchart LR
    JAVA["NativeLibUtil"] --> HOLDER["Native holder thread"]
    HOLDER --> LOAD["System.load / JNI_OnLoad"]
    LOAD --> BIND["BindEntry"]
    BIND --> CPU["CPU capability initialization"]
    CPU --> CONFIG["NativeRuntime.initialize"]
    CONFIG --> AVAILABLE["Java availability gate"]
```

`YSM_JNI_ENTRY` 把 Java descriptor、参数与返回形状关联到静态注册记录。`BindEntry()` 先检查登记非空、按 class/method/signature 排序并拒绝重复，再解析目标类并分组 `RegisterNatives`。Registry 只允许从 collecting 进入一次 binding，最终为 bound 或 failed；绑定失败使 `JNI_OnLoad` 返回错误，不建立动态修复或重试注册表。

加载窗口与 client/server 服务创建的关系见[运行模型](../runtime-model.md)。平台选择、CPU 最低要求与发布范围以[平台决策](../../product-decisions/requirements/req-platform-availability.md)和[当前状态](../../status/support-and-verification.md)为准；CMake 能构建的目标不能直接当作产品可用入口。

## 能力分工

| Java 入口 / native 领域 | Native 交付 | 调用者仍负责 |
|---|---|---|
| `Blake3`、`Zstd` / `ysm::codec` | 摘要、压缩或按期望大小/hash 验证的解压结果 | 选择 identity 输入、descriptor、业务预算与存储提交 |
| `Image`、`ImageEncoder` / `ysm::codec` | 图像 probe、像素解码和编码结果 | 用途与质量策略、buffer 所有权、Minecraft texture 创建和上传 |
| `OpusDecoder`、`SupportedAudioProbe` / `ysm::codec` | Ogg Opus 结构/时间轴解释、有界 feed/end-input/read 与 mono PCM | 精确内容取得、Vorbis host adapter、每播放 owner、PCM 保留与 Minecraft channel 交接 |
| `NativeArchive` / archive adapter | 包内目录、文件枚举和借用文件 bytes | 来源选择、路径语义、capture 和模型解析 |
| `NativeBakedModel`、`NativeModelState`、`NativeRenderer` / bake 与 renderer | 烘焙结果、帧状态和 CPU 顶点 | 动画求值、`RenderType`、`VertexConsumer` 提交和 GPU 生命周期 |
| `NativeLegacyImporter` / `ysm::legacy` | 有界 direct-buffer 输入、同步流式解密/解压与有类型的 `ImportResult` | 文件读取与 buffer 生命周期、结果协议校验、staging、重开验证和当前 catalog 接入 |

图像、音频和 archive 消费边界见[资产管线](../asset-pipeline/README.md)，渲染算法见[渲染架构](../rendering/README.md)。音频的精确 source、保留和播放 owner 见[模型管理](../model-management/ownership-and-lifecycle.md#模型音频生命周期)。这些能力共享 JNI 机制，不共享模型 current-content map 或 session authority。

## 失败如何返回

通用 JNI entry wrapper 在边界内处理 `absl::Status` / `StatusOr` 与 C++ 异常，失败时返回对应入口的 false、null 或配置的 fallback。Java 包装必须解释该返回值，不能将默认值当作有效模型或有效输出。具体失败分类并不自动由 C++ status 无损传播；需要诊断和结构化结果的历史导入有自己的 response 协议。

JNI 返回成功也不自动提交外部状态。例如 `NativeRenderer.render()` 只有在 native 成功后才调用 fallback writer 或推进 direct vertex region；模型 Ready 与纹理 publication 则由 Java resource owner 决定。异常、预算和内容失效对系统的影响最终回到[领域失败处理](../model-management/failure-and-recovery.md)。

Buffer 与 handle 的具体约束见[JNI 与内存](jni-and-memory.md)。

## Android 启动器接入

Android 的适配前提由[启动器产品边界](../../product-decisions/decisions/platform-baselines.md#bcandroid-launcher-runtime-prerequisite)定义。`MOD_ANDROID_RUNTIME` 的目录与 native library namespace 接入说明可查 [YSM FAQ 的启动器开发者说明](https://ysm.cfpa.team/en/wiki/faq/)；该外部说明用于接入导航，不扩大本主线的平台支持范围。

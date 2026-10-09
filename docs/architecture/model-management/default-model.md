<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# Intrinsic default

Intrinsic default 按[分类降级与启动基线](../../product-decisions/decisions/model-fallback.md#dddefault-model-is-reliability-baseline)承担启动硬依赖。构建期 contract 固定它的 `ModelId` 和默认动画集合；运行时从 builtin raw source 生成容器并再次校验身份。

Client 在 private service candidate 内完成 intrinsic default 的 parse、bake、default render target、animation 与 residency 验证，随后才发布唯一可达的 model service。任一步失败都关闭尚未发布且 ownership 固定的 candidate resources，并传播原始 cause；系统不暴露 partial service、readiness enum、startup failure cache 或 timeout recovery。Optional builtin work 只在 service publication 之后沿普通 catalog owner 启动，不能回滚 required publication。

当前构造由 builtin raw source 直接生成内存中的容器 bytes，再通过 `ManagedContainer.openResidentDefault` 完成 descriptor、lazy chunks 与驻留表示。Client 在 private candidate 中完成 default render 初始化并持有 required lease；dedicated server 只保留本侧只读内容。路径不创建临时容器、delegate handoff 或清理目录，缓存命中也不能跳过每次启动物化。设计理由见[默认内容不建立磁盘缓存](design-rationale.md#默认内容不建立磁盘缓存)。

Default 的独立生命周期与启动失败边界见[默认基线契约](../../product-decisions/decisions/model-fallback.md#bcdefault-startup-is-required)；会话失败降级见[session-failure](../../product-decisions/decisions/session-failure.md)。Selection 由 ID 17 `PlayerStateUpdate.model` / `ModelSelectionState` 表达，独立于 model-session collection publication。

`builtin_default` 在 wire 上不携带模型 hash；client 将其应用到 player 时确定性映射到本进程常驻 default 的 `ModelId`，并作为 primary binding 使用。只有其他权威目标不可用，或尚无可应用目标时，default target 才带 fallback 标记；wire authority 与 client-local applied identity 因此是单向投影，不构成第二套选择 authority。

## 默认资产构建设计

26.3 客户端在 `ClientStartedEvent` 中建立 private service candidate；该事件在 `Minecraft.run` 设置客户端线程后、第一次 tick 前于 client/render owner 触发。`FMLClientSetupEvent.enqueueWork` 只完成联动和 locator 准备：其同步队列在 `modloading-sync-worker` 上执行，而客户端仍阻塞于 `ClientModLoader.finish()`，不能在那里等待排入客户端队列的默认 GPU 纹理 publication。`ClientModelService.start` 对冷启动要求 client/render owner，完成 required future 时由 `managedBlock` 排空同一队列，再一次性发布 service；不增加 partial service、timeout recovery 或首次运行期延迟补齐。

默认来源、两侧物化职责和驻留范围由[model-fallback](../../product-decisions/decisions/model-fallback.md)定义；无磁盘中间表示的设计与工程理由统一见[默认内容不建立磁盘缓存](design-rationale.md#默认内容不建立磁盘缓存)。

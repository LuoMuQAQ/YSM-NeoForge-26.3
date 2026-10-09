<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 渲染已知问题

本页只记录当前实现可证实的渲染缺口。目标与正常语义见[渲染架构](../../architecture/rendering/README.md)。

## 视觉与接入

- 客户端已实际进入主菜单与世界。JNI 矩阵写入使用本机字节序，模型页和贴图页由宿主包装入口统一处理背景；用户已确认模型正常加载、Alt+Y 可以正常打开。这不覆盖全部第三人称、预览与贴图页面。卡片底部名称仍有不可见反馈，悬停提示未核查；模型卡片名称已使用不透明 ARGB，分类文件夹卡片遗漏的透明 RGB 已修正，并隔离封面与标题绘制。该遗漏能解释分类卡片缺字，普通模型卡片及修复后的页面仍需实机区分与确认。
- 实体替换、通用层和模型/贴图/额外玩家预览已经在 submit 时生成顶点快照，并由 `SubmitNodeCollector` 稍后回放。源码编译通过不等于视觉、物品附着或 Iris/PBR 已验收。第一人称手臂已迁到宿主提交入口，只有有效顶点入 collector 才取消原有手臂。
- 用户确认持物与 Z 轮盘修复后基本正常，仍反馈左上角玩家预览正对、缺少原有的微小偏转。预览已恢复只在矩阵中使用配置 yaw 偏移，玩家真实身体/头部朝向不再被覆盖；默认偏移为 5°，修复后的方向和设置页面拖动仍待实机复核。基本正常的反馈不覆盖全部模型、地图、声音或画面组合。
- 半透明 `RenderType` 使用带 `BlendFunction.TRANSLUCENT` 的实体管线，因此 `hasBlending()` 为真，自定义几何进入宿主半透明阶段；工厂不调用 `sortOnUpload()`。Java 为 native 转换反向深度约定，单模型远近排序仍由 native 完成，见[顶点输出](../../architecture/rendering/vertex-output.md)。跨实体、跨模型和跨 draw 的次序仍由上层决定。Iris shadow 不执行透明排序。实际混合结果尚未在游戏里核对。
- PBR 效果依赖 Iris 版本、shader pack 和 companion texture 接入，不能仅凭 baked tangent 存在保证一致。
- 加入 Iris 1.11.7 后，用户反馈部分原在52实例正常的模型显示 `Model texture publication failed`。该 provider 的新 holder 取得会排队到下一帧，原接入立即核对默认 holder 而拒绝带 PBR 组件的模型；已在发布窗口消费 Iris 加载队列后重新核对实际 holder，并为发布失败补充 cause 日志。修复后的模型、光影开关和资源重载仍待实机确认，不将等待状态误判为内容损坏。
- 右手持物和头部物品走宿主物品提交。用户反馈装备鞘翅时物品栏预览为紫黑缺图、HUD 不显示；旧默认纹理路径在宿主资源中已不存在，HUD 也未传入 avatar 状态。鞘翅现接到宿主 WINGS 装备层，HUD/玩家模型与贴图预览补齐独立提取的 AvatarRenderState，肩部鹦鹉共享该状态，具体机制见[逐帧状态与调度](../../architecture/rendering/frame-execution.md)。普通、附魔、披风纹理、蹲伏/飞行、资源重载和多视角显示仍待用户实机确认。普通副手使用左手 locator 与物品上下文提交；TaCZ/Superb Warfare 的专用副手没有适用 locator、查询失败或未提交时回退普通物品。Sophisticated 背包使用真实渲染状态与提交 API，Iris PBR 要核对上传后实际组件身份。这些接线已编译，左右惯用手、特殊武器、背包位置和 PBR 外观尚未实机验收。
- 女仆小银狼的主手物品在第三人称和左上角预览缺失、第一人称右手正常有用户反馈。只读几何检查确认模型有左右手定位点而没有背包定位点；组大小查询曾误读相邻组，右手因而检查了空的背包组。查询已按与提取及遍历一致的 `sequence - 1` 修正，仍尊重动画对定位点的隐藏及零缩放。修复后的主副手画面待实机确认。
- 取消玩家 `Pre` 后，原版模型、层和名字牌不会绘制；名字牌由替换渲染器按提取状态另行提交。火焰和阴影仍按提取状态由宿主绘制。小坐骑偏移和计分板名字隐藏没有接上。调试动画与加载状态 HUD 已适配 `GuiLayer` 并注册，女仆调试依赖通过门禁的可选 provider，未取得可验证的目标组合。
- 未经 `ProjectionMatrixBuffer` 上传的外部投影 slice 没有 CPU 矩阵关联，YSM 在这类 pass 中跳过模型提交。缓存复用与恢复已按绑定 slice 查询，未知投影不会污染后续已知 pass。完整视觉、发光队伍色、预览视口和读回失败处理仍待实机检查。

## 正确性与失败处理

用户后续截图表明鞘翅贴图已经正常，但尺寸明显偏大。鞘翅层已移除沿用旧层的固定两倍缩放，保留 authored locator scale，按宿主标准几何尺寸提交。各模型的尺寸、背部位置、蹲伏/滑翔与资源重载后的结果仍需实机确认。

- `ModelState::Extract` 会先使旧状态失效；失败后 `GeoModelState` 没有完整失败分支，同一逻辑帧可能不再重试。Native render 失败时不会改用其他输出路径或 `VertexConsumer` fallback，本次模型直接无顶点。
- Serialized baked cache payload 不能独立证明 SIMD capability 匹配；读取虽校验结构、层级、索引和计数，却未重新验证几何浮点值的有限性及语义域。Bake 对极端有限输入派生的 plane / tangent 也缺少完整结果域验证。
- 上层必须提供与 position matrix 匹配的 normal matrix；native 只校验数值有限，不验证二者一致，该组合目前也没有端到端验证。
- `BakeModelOptions.force_translucent` 可能让原本 opaque 的骨骼进入透明分区，却未同步其透明深度准备条件。当前 Java 主加载路径不启用该选项，启用前需补齐最终排序验证。

## 并发与性能

- `ParallelExecutor`、translucent scratch、`VertexConsumer` fallback 与 `NativeRenderer` 的共享 matrix scratch 都不可重入，多个 `renderer::Render` 必须全局串行。`ModelState` 原地复用自身 pose 与索引存储，Java view 只在该状态的有效期内可读，因此同一输出槽的 Extract、Render、换模与释放必须串行。
- 调度按不可拆分 `CubeGroup` 数而非实际 quad、PBR 或剔除成本分配任务，复杂模型可能出现 worker 尾部不均衡。
- 剔除分区按最大可见容量预留，并以零值填充未使用槽位，这是固定 offset 的当前代价。

## 扩展接线

`RegisterRenderStateModifierEvent` 和 `RenderStateModifier.apply()` 当前只有声明与容器代码，生产路径没有发布该注册事件或调用 modifier。`@ParallelInvoke("entity")` 注解不能作为该扩展已接通的证明；已存在的 locator、模型和 layer 事件窗口见[游戏与扩展接入](../../architecture/integration/README.md)。

## 待验证场景

Native renderer 尚未形成可作为支持声明依据的自动化回归与视觉验收闭环。

在声明支持前，Minecraft 运行验证至少应覆盖：`level` entity 同帧多 pass、`inventory` / `paperDoll` context 的 mutable 输出、本地第一人称 `irisShadow`、模型热切换、`VertexConsumer` fallback、透明与 PBR、非均匀缩放及各 locator layer。还应验证 locator mapping 始终读取对应 native `ModelState` 的当前 pose，且旧 Java view 不跨越 Extract 或 close 使用。Packed normal 的编解码约定见[顶点输出](../../architecture/rendering/vertex-output.md)，约定匹配不能替代 fallback 与 direct 的完整视觉等价验收。视觉验收应比较 Vanilla、Iris 与 Blockbench 基准，并区分几何语义偏差和 shader / 光照环境差异。

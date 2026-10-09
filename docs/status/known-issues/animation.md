<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 动画已知问题

本页只记录当前实现可证实的动画缺口。正常职责和数据流见[动画架构](../../architecture/animation/README.md)。

一次第一人称单侧提取在进入世界渲染时因 `locator type mismatch` 崩溃：装配器曾把 arm geometry 绑定为身体 locator。装配已按用途显式传递类型，覆盖默认驻留、新 bake 与 cache 重开；第一人称消费前也检查类型，异常时记录诊断并保留宿主原绘制。修复后已有用户第一人称世界截图，持物分支额外空手姿态与轮盘缺字也已修复，用户反馈基本正常；完整重进/地图/惯用手组合未得到逐项确认。

内置魔法酒狐空手时露出的手臂范围差异来自移植时新增的宿主肩部对齐。两版该资产几何、动画和描述内容一致，宿主空手外层位移/旋转和基础手部 FOV 也一致；现已恢复官方源码的屏幕坐标偏移，并保留单侧提取和类型门禁，用户确认当前酒狐显示正常。该反馈不覆盖全部模型、左右惯用手与地图组合。

## 求值与语义

- `AnimationEvent.isMoving()` 已按绝对步态幅度超过阈值判断，修复静止/小幅步态被标为移动、正常正向步态反而被标为静止的错误。当前默认主动作另用位置差值选择移动状态，该修复不等于重写全部移动查询，相关外部 predicate 仍需实机确认。
- 头部 yaw / pitch 由当前输入计算并应用到 head locator，主帧推进与其他 pass 的恢复路径均存在；此前“coded head-bone 被禁用”的记录已不符合实现。第一人称手臂通过独立 owner 和手臂 controller factory 推进，未共享身体 controller 进度；具有定制双视角同步需求的模型仍需核对。
- 骨骼基准 snapshot 已分别读取 `cubes_hidden` 和 `children_hidden`，修复自身与后代可见性互相污染；带独立隐藏通道的模型、locator 与动画混合仍待实机确认。
- Controller 的旋转混合仍保留不完整的历史行为；当前 Schema 的 `State` 已没有 `blend_via_shortest_path` 字段（field 7 空置），reader 一律按 `false` 处理，因此该行为无法由模型表达。
- 骨骼绝对轴心由 `ysm.bone_pivot_abs` 提供；引用旧 `ysm.bone_absolute_pivot` 名称的模型不会解析到该函数，依赖它的行为缺口仍存在。
- Sound keyframe 与 controller-state sound effect 都能进入同一 `SoundInstanceManager`：无冒号名称解析当前 render target 的模型声音，有冒号名称继续播放 Minecraft `SoundEvent`。模型声音的内容与播放主链已有局部自动化覆盖，但真实 Forge world 的 once-only 触发、音量、OpenAL adoption、设备容量和停止时序仍未验收；`ysm.play_sound` 使用相同路由。

## 26.3 查询输入

这些查询已经改到 26.3 宿主 API。数值来源和旧版不完全相同，编译结果不能代替实机数值验收。

- 箭的效果查询通过纯 `@Invoker` 接口读取 `PotionContents.customEffects()`，继续排除基础药水效果。访问器中的普通 `default` helper 会触发 Mixin interface/class 类型不匹配；该 helper 已移到调用方，修复后的启动应用仍需实机确认。

- `swing_time` 现在是挥手过程中的整数 tick，未挥手时为 0。tick 通过 `LivingEntity.SwingState` 的客户端 mixin accessor 读取。挥手结束后宿主会清空当前手，查询记住上一只手，缺省主手。`attack_time` 仍取 `getSwingAnimation` 的 0–1。
- Coded 挥手重播按宿主新挥手描述的身份消费，已修复错过第 0 tick 后同名单次攻击无法再次启动的问题，机制见[Controller 与播放](../../architecture/animation/controllers-and-playback.md#播放状态)。星见雅持剑在第三人称和 HUD 的连续攻击、快速连击及其他模型仍待用户实机复测；模型自定义 controller 和第一人称独立运行时不在本次修复范围内。
- `step_height_addition` 改为当前属性值减去基础值，属性缺失时为 0。旧值是额外高度且默认 0；现在的差值包含基础值变化，数值尺度不同。
- `moon_phase` 使用主世界时钟 `(getOverworldClockTime() / 24000) % 8`，顺序仍是满月 0 到盈凸月 7。它不读取带位置的 `EnvironmentAttributes.MOON_PHASE`，月相被维度固定时不会单独反映。`time_of_day` 和 `time_stamp` 使用同一时钟。
- `modified_distance_moved` 已恢复为实体实际碰撞裁剪后的水平移动距离累计值，比例为 0.6；披风使用该累计值的前后 tick 插值。累计路径与宿主步态的平滑、限速、baby scale 和骑乘/死亡清零相互独立，瞬移和重复渲染不增加它。宿主客户端的远端移动模拟路径与旧版不同，远端实体的实际数值仍需实机验收。
- `equipped_enchantment_level` 按参数下标读取附魔 id。旧循环始终读取第 1 个参数。
- 猪仍走 `ride_pig`。其后的可骑乘分支是 `AbstractHorse` 或 `Strider`，不再调用已删除的 `Saddleable`，也不检查是否已装鞍。
- `is_in_water_rain` 改为 `isInWaterOrRain()`。气泡柱是否仍计入没有单独核对。
- 第一人称空主手及地图的原版手臂窗口经 `RenderArmEvent` 替换。普通持物额外空手姿态与宿主物品分离的问题已通过移除补交入口修复，持物继续走宿主原有提交；空副手也不额外绘制。按请求侧提取及肩部对齐见[逐帧状态与调度](../../architecture/rendering/frame-execution.md)。只有有效顶点实际入 collector 才取消原有手臂，空手外观、物品使用、左右惯用手和地图仍需用户实机验收。轮盘动作名、快捷键、路径及配置图标的基础文字色已改为不透明 ARGB，文字在扇区之后提取；配置变化和动作触发仍需用户检查。背景标志会在不透明方块之后清掉，但背景网格本身没有提交。第三人称普通副手已补交，特殊副手无可用 locator 或失败时回退普通物品。Sophisticated 自定义背包 locator 保持旧绘制显式无额外胸甲偏移的语义；位置和组合仍需实机核对。

## Context、线程与生命周期

- 异步求值没有不可变输入快照，会直接读取 live `Entity`、`level`、`Minecraft` 输入和可选模组 API；同次求值可能混入不同时间点数据，也可能违反外部 API 的线程限制。
- Forge world 中 controller、instruction、defer、config 与多 `RenderContext` 的实际 once-only 行为尚未端到端验证。`AnimatableEntity.executeMolangExp` 只把任务排入 `AnimationProcessor` 的待执行队列，队列的排空时机与同一次逻辑推进内的执行次数没有实机证据，也没有对应自动化覆盖。
- Canonical pose 可以跨兼容 pass 复用，复用时仍刷新 `RenderContext`，但 `partialTick` 和 `animationData` 沿用首次提取结果。多 pass 的输入差异是否都被 immutable context 判断排除，尚缺实机证据。
- 同一 render target 内热替换会保留动画状态并依赖烘焙兼容；兼容性门禁和第一人称独立运行时尚缺完整闭环。
- `AnimationProcessor` 原地修改共享 snapshot 与 attribute，没有事务 staging、异常回滚或模型 revision 二次校验，失败可能留下部分更新。

## Molang 与同步

- 轮盘 radio 曾把模型 read 的数值直接当作 labels 下标。女仆小银狼的“吐舌头”写入6，但显示在下标1，因此被错误勾选为下标6的“笑”。现从原动作的无条件字面量赋值推导显示映射，初次打开和刷新使用同一映射；动态/不可推导的动作仅保留本页成功点击，不猜测重开后的选择。原动作和模型文件未改，两个表情组、滚动后点击与重开页面仍待用户实机复测。

- 模型内 Molang 已回到本地解释器直接执行 source：加载与绑定会解析 execution-bearing 字段，求值在渲染路径上逐次进行。求值路径没有专门的单元测试，热路径的解析/求值成本、allocation、表达式复杂度上限与 tick/render 影响都尚未测量，不能声明主循环成本可接受；expression runtime 在 Minecraft/Forge world 层也没有端到端验证。
- Roaming 的 full 与 delta 已由玩家状态报告路径携带并在消费端按协议验证，但网络 inactive storage 仍按模型派生的 32-bit 短键分组，短键碰撞和协议无变量删除语义仍未解决。
- `ysm.sync` 只携带有限 F32 参数并把参数个数限制为 16，没有目标 identity、独立频率限制，按设计也没有 sequence、ACK、重放或持久化。实体缺失、generation/model 不一致或会话变化时事件会丢失，真实双端 relay/disconnect 路径尚未验证。

## 联动与验证

- 目标联动源码已能编译。有真实目标依赖的 Curios、PAL、Better Combat、Iris 和 Sophisticated 等使用对应 API；未取得目标 provider 的旧分支通过 `OptionalApi` 访问真实成员并局部降级，不能把桥接代码视为这些联动已恢复支持。既有接线未完成统一输入快照、外部线程要求与组合实机审计。Better Combat 攻击使用宿主 SwingState 的重启入口以保留每次攻击重置挥手的行为。
- 女仆渲染接管必须经过宿主提交边界门禁。旧 provider 被拒绝接管；独立 transient owner、模型/贴图页面及主动画接线已编译，provider 实际行为未验收。`getGeoModel` 与 roaming 同步仍保留原有 TODO。
- Java/corpus 测试已覆盖 animation/controller 的 Proto 映射与字段顺序、`Program` envelope 的 oneof tag 与 source 往返、ModelData 的双 Proto codec 互操作、内置目录索引、模型导入与 raw parse、Roaming 变量存储，以及模型声音 retention/playback/handoff 的局部边界；`test` task 最近执行 575 项，0 failure、0 error、9 skipped。它仍缺少 coded / Bedrock / hybrid controller 在真实 entity 上运行、controller config、`ysm.sync`、多人远端 `Entity`、模型热切换、同帧多 `RenderContext`、声音 once-only/音量/host adoption、第一人称共享和长期 reference convergence 的 Minecraft 端到端测试。表达式解释器的解析与求值本身没有直接测试。
- `BoneAttribute` 与 `BakedModel` preorder、可见性、locator 的跨语言契约缺少独立 golden 验证；布局重复定义仍有漂移风险。

模组恢复边界见[模组动画联动](../../future/mod-animation-integration.md)，渲染侧的 extract、locator 与重入缺口见[渲染已知问题](rendering.md)。

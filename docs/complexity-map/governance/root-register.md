# 根依据总表

本表是根键解释的唯一登记位置。表内短键只压缩图表达，产品键引用唯一判断或其具体契约，架构键引用内部取舍；多个历史短键可导航到同一归约后的判断，不能据此重复计算产品决策数量。业务目标再经各 DD 的 Requirement/Motivation 返回[共享约束](../../product-decisions/shared.md#constraints)，不在本图复制完整产品树。

| 键 | 既有决策与短语义 |
|---|---|
| D-RELOAD | [DD.custom-content-hot-load](../../product-decisions/decisions/authoring-input-and-reload.md#ddcustom-content-hot-load)：进程内迭代内容 |
| D-CATALOG | [DD.single-current-catalog](../../product-decisions/decisions/catalog-publication.md#ddsingle-current-catalog)：完整发布唯一当前目录 |
| D-HOLD | [DD.single-current-catalog](../../product-decisions/decisions/catalog-publication.md#ddsingle-current-catalog)：退役不撤销已有使用 |
| D-SOURCE | [DD.user-model-sources-are-readonly](../../product-decisions/decisions/artifact-storage.md#dduser-model-sources-are-readonly)：来源不归缓存维护所有 |
| D-EXACT | [DD.single-current-catalog](../../product-decisions/decisions/catalog-publication.md#ddsingle-current-catalog)：不混表示/半写派生物 |
| D-IDENTITY | [DD.model-and-representation-identities](../../product-decisions/decisions/model-identity.md#ddmodel-and-representation-identities)：来源等价与字节等价分开 |
| D-JOIN | [DD.catalog-before-model-body](../../product-decisions/decisions/model-distribution.md#ddcatalog-before-model-body)：入服/浏览不全量下载 |
| D-CHUNKS | [DD.catalog-before-model-body](../../product-decisions/decisions/model-distribution.md#ddcatalog-before-model-body)：只取目标依赖闭包 |
| D-REUSE | [DD.reuse-content-before-downloading](../../product-decisions/decisions/model-distribution.md#ddreuse-content-before-downloading)：先复用合格内容 |
| D-META | [DD.catalog-before-model-body](../../product-decisions/decisions/model-distribution.md#ddcatalog-before-model-body)：描述与普通内容分阶段使用 |
| D-VERIFY | [DD.catalog-before-model-body](../../product-decisions/decisions/model-distribution.md#ddcatalog-before-model-body)：使用 chunk 时再独立验证 |
| D-FREEZE | [DD.semantic-freeze-at-export](../../product-decisions/decisions/model-compatibility.md#ddsemantic-freeze-at-export)：导出固定当次作品语义 |
| D-MIGRATION | [DD.semantic-freeze-at-export](../../product-decisions/decisions/model-compatibility.md#ddsemantic-freeze-at-export)：单向迁移保留可解释行为 |
| D-SOUND | [DD.model-sounds-are-observable-content](../../product-decisions/decisions/model-sound.md#ddmodel-sounds-are-observable-content)：有效模型声音形成实际播放结果并局部隔离失败 |
| D-IMAGE | [DD.compression-preserves-model-meaning](../../product-decisions/decisions/image-policy.md#ddcompression-preserves-model-meaning)：压缩降低重复分发成本并保真 |
| D-GUI | [DD.visual-model-selection](../../product-decisions/decisions/selection-and-attribution.md#ddvisual-model-selection)：GUI 浏览并发起选择 |
| D-DEFAULT | [DD.default-model-is-reliability-baseline](../../product-decisions/decisions/model-fallback.md#dddefault-model-is-reliability-baseline)：兜底内容提前闭合 |
| D-FALLBACK | [DD.default-model-is-reliability-baseline](../../product-decisions/decisions/model-fallback.md#dddefault-model-is-reliability-baseline)：Humanoid 默认基线 |
| D-HOST-FALLBACK | [DD.default-model-is-reliability-baseline](../../product-decisions/decisions/model-fallback.md#dddefault-model-is-reliability-baseline)：其他目标恢复宿主 |
| D-RECOVER | [DD.degraded-presentation-can-recover](../../product-decisions/decisions/model-fallback.md#dddegraded-presentation-can-recover)：保留请求并在有效条件变化后恢复 |
| D-FAIL | [DD.local-failure-degradation](../../product-decisions/decisions/failure-isolation.md#ddlocal-failure-degradation)：普通错误只局部降级 |
| D-LOOP | [DD.main-loop-has-priority](../../product-decisions/decisions/workload-budget.md#ddmain-loop-has-priority)：模型工作服从主循环，禁止等异步结果 |
| D-SESSION | [DD.session-scopes-remote-state](../../product-decisions/decisions/session-isolation.md#ddsession-scopes-remote-state)：旧连接事实不进入新连接 |
| D-SESSION-FAIL | [DD.local-failure-degradation](../../product-decisions/decisions/failure-isolation.md#ddlocal-failure-degradation)：模型会话失败保留 Minecraft |
| D-WIRE | [DD.protocol-compatibility-has-bounded-cost](../../product-decisions/decisions/session-failure.md#ddprotocol-compatibility-has-bounded-cost)：unstable 不维护多版本读取 |
| D-VALIDATE | [DD.validate-client-before-effect](../../product-decisions/decisions/trust-boundaries.md#ddvalidate-client-before-effect)：客户端输入生效前严格检查 |
| D-CAPABILITY | [DD.trusted-server-with-capability-floor](../../product-decisions/decisions/trust-boundaries.md#ddtrusted-server-with-capability-floor)：远端内容不获得任意宿主能力 |
| D-ACCESS | [DD.selection-permission-is-separate-from-asset-access](../../product-decisions/decisions/model-authorization.md#ddselection-permission-is-separate-from-asset-access)：选择/展示/运行内容各自授权 |
| D-ADMISSION | [DD.request-time-authorization](../../product-decisions/decisions/model-authorization.md#ddrequest-time-authorization)：已接纳传输保留授权结果 |
| D-SERVER | [DD.server-decides-model-operations](../../product-decisions/decisions/model-authorization.md#ddserver-decides-model-operations)：服务端裁决模型操作 |
| D-PLAYER | [DD.game-state-plus-mod-projection](../../product-decisions/decisions/player-state-sync.md#ddgame-state-plus-mod-projection)：复用游戏事实、补齐模组投影 |
| D-TRACKING | [DD.tracking-scopes-state-sync](../../product-decisions/decisions/player-state-sync.md#ddtracking-scopes-state-sync)：跟踪范围内同步 |
| D-SCRIPT | [DD.script-selects-sync-values](../../product-decisions/decisions/script-sync.md#ddscript-selects-sync-values)：脚本显式选择跨端值 |
| D-ENTITY | [DD.animation-state-is-entity-local](../../product-decisions/decisions/animation-isolation.md#ddanimation-state-is-entity-local)：实体不共用可变求值状态 |
| D-EFFECT | [DD.render-observation-does-not-repeat-actions](../../product-decisions/decisions/animation-isolation.md#ddrender-observation-does-not-repeat-actions)：重复观察同一次推进不重复动作 |
| D-SAMPLE | [DD.animation-sampling-is-separate-from-state-updates](../../product-decisions/decisions/animation-sampling.md#ddanimation-sampling-is-separate-from-state-updates)：整体表现与逻辑因果独立于具体采样调度 |
| D-CONTROLLER | [DD.model-controller-overrides-builtins](../../product-decisions/decisions/bone-and-controller.md#ddmodel-controller-overrides-builtins)：作品可覆盖内建 controller |
| D-VANILLA-ACTION | [DD.model-controller-overrides-builtins](../../product-decisions/decisions/bone-and-controller.md#ddmodel-controller-overrides-builtins)：保留基础动作表现 |
| D-AN-FALLBACK | [DD.model-controller-overrides-builtins](../../product-decisions/decisions/bone-and-controller.md#ddmodel-controller-overrides-builtins)：缺动画可借默认动作 |
| D-EXT | [DD.local-failure-degradation](../../product-decisions/decisions/failure-isolation.md#ddlocal-failure-degradation)：可选联动不阻断 startup |
| D-OUTPUT | [DD.authoring-semantics-before-optimization](../../product-decisions/decisions/geometry-regions.md#ddauthoring-semantics-before-optimization)：各输出/CPU 路径语义等价 |
| D-HOST | [DD.local-failure-degradation](../../product-decisions/decisions/failure-isolation.md#ddlocal-failure-degradation)：失败不污染后续世界绘制 |
| D-TRANSPARENCY | [DD.transparency-stops-at-model-boundary](../../product-decisions/decisions/transparency-scope.md#ddtransparency-stops-at-model-boundary)：透明保证有单模型边界 |

以下 A 是当前架构选择，不是新 ADR。存在性可为 Fact，具体历史理由仍按机制页区分。`C-JNI` 是 JVM/native ABI、调用期地址与错误边界的外部约束；它不参与“决策候选”排名。

| 键 | 选择 / 原因与证据 |
|---|---|
| A-ONE-ARTIFACT | [统一运行制品](../../architecture/asset-pipeline/conversion-and-export.md#统一运行制品)：转换后统一当前容器 |
| A-DEFAULT-MEM | [默认资产构建设计](../../architecture/model-management/design-rationale.md#默认内容不建立磁盘缓存)：默认构建不建立无收益磁盘中间表示 |
| A-NATIVE | Java 领域/宿主 owner、native 计算与顶点 bytes，经 JNI 交接；[迁移理由](../../migration-overview.md#迁移方向)减少 native 业务 authority。为何 CPU renderer 必须采用当前策略没有独立性能比较。 |
| A-CAPTURE | Scan 与 compile 分阶段消费可变来源；AP-01 用冻结 VFS 及同一 parser 解决来源漂移与双解析器风险；[capture 理由](../../architecture/model-management/design-rationale.md#capture-复用同一解析路径)。 |
| A-IMAGE | 转换按用途处理图片：raw 与 legacy 都复用合法既有压缩表示，只对 PNG 或 RGBA32 进入重编码；成功结果不以体积比较回退，raw 的可处理 codec I/O 失败保留原图，legacy 的编码/round-trip 失败终止该 source；[转换](../../architecture/asset-pipeline/conversion-and-export.md#图像处理的位置)。 |
| A-PAGE | 页面作为有限异步需求 owner，内部重建保持同一页面意图；[展示架构](../../architecture/client-presentation/README.md)。 |
| A-PREVIEW | 卡片 preview-first；连续 hover 0.3 秒后才探测 cache-only target，展示图独立取得；[展示架构](../../architecture/client-presentation/README.md)。真实 3D 改善选模的理由仍为 Inference。 |
| A-CONTINUOUS-DEMAND | 页面、hover 与实体各自只保存当前意图和连续起点；缺件 presentation/entity 超过 0.7 秒、hover 超过 0.3 秒才提交对应工作；[展示架构](../../architecture/client-presentation/README.md)与[失败恢复](../../architecture/model-management/failure-and-recovery.md)。 |
| A-DIRECT-ADMISSION | Schema 接受合法无图容器，只有 resolver 已知的 direct 成品在目录准入时强制完整内嵌 preview；[转换与导出](../../architecture/asset-pipeline/conversion-and-export.md#preview-取得与显式-export)。 |
| A-PREVIEW-CACHE | 独立 preview 只按 `ContainerId` 弱关联，坏读不删除、验证后原子覆盖，日常取得不回写容器；[存储](../../architecture/model-management/storage-and-cache.md)。 |
| A-NOTIFY | Server 事实先提交，collection full/delta best-effort，无 ACK/history/rollback/resync；[通知理由](../../architecture/network/design-rationale.md#游戏事实与观测机会先于通知结果)。 |
| A-RESOURCE | Ready 强可达、Pending exact interest 分开；Ready 最后 consumer 后进入 30/60 unused LRU，以无同步物理回收上界换取安全共享；[资源理由](../../architecture/model-management/design-rationale.md#分离-ready-可达性与-pending-interest)。 |
| A-AUDIO-RETENTION | 客户端用一个 64 MiB、30 秒 acquire TTL、访问顺序 LRU 账本统一保留 encoded/完整短 PCM，PCM 发布原子替代 encoded cache 引用；[音频存储](../../architecture/model-management/storage-and-cache.md#客户端音频保留)。 |
| A-WORK | Catalog、client/server runtime 各自管理 admission/terminal 与独立容量；有限 worker 完成同一 worker-safe 链、不等网络/host/其他 worker，terminal 只由固定 owner tick 提交。Preview/export 同样以 client/server tick 单写的总 operation admission 覆盖跨 host 阶段；[有限工作理由](../../architecture/model-management/design-rationale.md#有限工作保持-domain-owned)。 |
| A-FRAME-BUDGET | 严格小于 30 KiB 的物理 frame budget，为当前缓冲及未来宿主留余量；[帧预算理由](../../architecture/network/design-rationale.md#30-kib-是跨宿主的物理帧预算)。 |
| A-TYPED | 只共用 mechanical helper，各 typed owner 裁决自身 coverage/terminal；[assembly 理由](../../architecture/network/design-rationale.md#reassembly-必须属于业务-owner)。 |
| A-DISPATCH | 一个 accepted outbound owner，当前 cursor 才构帧；[dispatch 理由](../../architecture/network/design-rationale.md#accepted-resource-只有一个-dispatch-owner)。 |
| A-FORCED | 当前 server-private forced-selection 特权；[Q-09](../java-models-and-network/open-questions.md#q-09)。存在 Fact、业务依据未闭合。 |
| A-REPORT | 握手业务就绪后仍等待自身 authority FULL 才形成报告；该资格不依赖 catalog publication。首 FULL/DELTA/周期 FULL 按当前机会 best-effort 尝试，观测进度与投递结果分离，真实 FULL baseline 留在 exact model session；[玩家状态](../../architecture/network/player-state.md)。 |
| A-AN-01 | Level entity 在 draw 前异步原地求值，本帧汇合消费；[帧执行](../../architecture/rendering/frame-execution.md)。比其他调度更好的理由未证明。 |
| A-AN-02 | 各 RenderContext 共用实体动画历史与输入，按输出槽区分结果；[实体状态](../../architecture/animation/entity-and-frame-state.md)。 |
| A-AN-03 | 按距离/可见性减少实际求值；[帧执行](../../architecture/rendering/frame-execution.md)。阈值收益及逻辑因果保持仍待 O-07 验证；限频本身是允许的架构选择。 |
| A-AN-04 | 生成依赖级 checker 在目标环境判定扩展兼容；[接入](../../architecture/integration/README.md#扩展兼容检查与注册)。范围必要性见 Q-07。 |
| A-SIMD | 单次启动选 SIMD，bake/render 使用同宽度 group；[bake](../../architecture/rendering/bake-and-partition.md)。 |
| A-BAKED-CACHE | 私有可重建 baked 表示按实际输入和 profile 复用；[cache 理由](../../architecture/rendering/design-rationale.md#派生-cache-按精确输入隔离)。 |
| A-FRAME-REUSE | 输出槽原地复用 native state 并返回借用；NR-06 与[帧状态](../../architecture/rendering/frame-execution.md#输入输出与状态)。 |
| A-PLAN-CACHE | 姿态变化但可见工作集不变时复用 schedule；NR-07 源码/测试。缓存选择与正确失效分开。 |
| A-PARALLEL | CubeGroup 为不可拆并行单元，同 draw 汇合、预分配输出；NR-08/09 和[帧执行](../../architecture/rendering/frame-execution.md)。 |
| A-ARCHIVE | Wrapper 缓存返回借用 extraction bytes；NR-11。性能理由 Inference，覆盖条件 Fact。 |
| A-LEGACY-READ | Java 按观察长度完整读入有界 direct buffer，native 从借用视图同步流式解密/解压，来源稳定性由调用方负责；NR-12 和[输入契约](../../architecture/asset-pipeline/conversion-and-export.md#legacy-envelope-的输入契约)。 |
| A-LEGACY-PROJECTION | Native 旧 decoder/projector，Java 当前容器 writer；NR-13 和[单向投影](../../architecture/asset-pipeline/conversion-and-export.md#历史输入的单向投影)。 |
| A-TEXTURE-PUBLISH | Candidate 全部 base/PBR component 经 host 接纳后才发布唯一 Ready，避免发布后再建立 completion/rollback authority；[资源理由](../../architecture/model-management/design-rationale.md#分离-ready-可达性与-pending-interest)。 |
| A-PUBLISH-BUDGET | Ordinary host publication 在 render owner 按已处置 terminal 数分批；[owner 边界](../../architecture/model-management/ownership-and-lifecycle.md)与 MN-19。阈值收益未量化，不视作 host upload 耗时或驻留硬上界。 |
| A-FAILURE-MEMORY | Failed Flight 退出命中，诊断与重试资格由 exact domain 保存；[失败理由](../../architecture/model-management/design-rationale.md#failed-flight-不成为恢复状态)。 |
| A-REMOTE-FILES | Remote bytes 写入固定 root 的文件型 cache；[storage](../../architecture/model-management/storage-and-cache.md)。文件系统间接寻址使 hash 文件名不足以独立约束 physical sink。 |
| A-SHARED-CACHE | Baked 模型与 baked 动画本地派生物使用跨进程 writer；MN-23 的锁和重验是当前源码事实。实际共享收益待 Q-10；converted、remote、preview 与 direct source 不使用同一锁协议。 |
| A-CONVERTED-CONSUMERS | Converted 跨进程共享时以实际 OS 锁登记活跃消费者；首次 scan 只在无其他消费者且取得短期许可时保守 prune 一次；[converted storage](../../architecture/model-management/storage-and-cache.md#converted-storage)。 |
| A-DEFER | Action 可以在动画上下文 reset 点之前保留已经求值的参数，并在该点按登记逆序执行 `defer` handler；[副作用阶段](../../architecture/animation/controllers-and-playback.md#副作用阶段)。这是当前执行语义，不补造最初引入理由。 |

`H-BLEND`、`H-FENCE`、`H-ALLOCATOR`、`H-FORCED` 是明确未闭合的根，分别见 AN-08、AN-13、NR-03、MN-15；不能把 H 当已接受决策。

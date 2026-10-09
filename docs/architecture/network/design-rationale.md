# 网络决策理由

本文记录网络 owner 与复杂度取舍；精确 wire 由[协议](../../standards/protocol-v1/README.md)定义，权威分层见[文档政策](../../governance/documentation-policy.md)。

## Unstable 阶段只维护一条 wire path

短protobuf使用simple frame，大protobuf或attachment使用full frame；一个tag同时选择message ID与frame kind。这样高频短消息不承担full header/压缩分支，大消息仍共享同一物理长度与decode边界。

Unstable 与 stable 的协议兼容承诺及维护成本取舍见 [DD.protocol-compatibility-has-bounded-cost](../../product-decisions/decisions/session-failure.md#ddprotocol-compatibility-has-bounded-cost)。

## 30 KiB 是跨宿主的物理帧预算

Minecraft 单包上限约为 2 MiB，但当前 Netty 发送缓冲区为 32 KiB，未来计划接入的 Bukkit 插件端还可能给出更小的可用上限。因此当前协议把严格小于 30 KiB 的 frame body 作为跨宿主兼容预算，给外层 framing 和不同宿主保留余量；大于预算的业务内容由原 typed owner 分片，而不是把 Forge 当前能接受的最大包当作设计目标。

这个预算只决定单个物理帧必须多小，不预留发送容量，也不承诺通过 admission 后一定送达。Backpressure、队列 hard limit、连接关闭或发送失败仍按各自 owner 的既有 terminal 语义处理。

## Reassembly 必须属于业务 owner

Catalog/grants/pack/default-animation是collection operation；metadata prefix是per-container sequence；model与presentation是byte range；optional presentation还有`UNAVAILABLE`。把这些语义压进通用fragment header会迫使所有业务共享kind field bag、total size、completion与failure scope，并把不合法组合扩大为wire状态。

因此只复用frame transport和owner-private mechanical range helper。每个typed receiver独立拥有natural key、numeric bounds、coverage、terminal和publication。删除generic assembler后，复杂度不会转移到caller，而是回到真正拥有不同语义的三个深Module。

## 只有超帧 descriptor 才支付 parent 成本

一个完整业务动作通常可以用一个request/ID表达。始终拆成per-item transfer会放大ID、cancel与terminal状态；wire batch/parent ID又会建立第二套生命周期。当前策略先编码完整descriptor，只有超过physical frame时才由原action owner顺序启动有限children。

具体 parent/child 所有权见[资产传输](asset-transfer.md#typed-request-与-action-owner)。

## 游戏事实与观测机会先于通知结果

Catalog与grants是server游戏事实。新状态先提交，再best-effort发送full/delta；编码或enqueue失败不回滚authority。否则慢client或closing queue会成为权限事务参与者。

这里允许client暂时保留旧collection view，因为reconnect提供确定收敛。ACK、history、rollback、replay或主动resync会把当前unstable通知路径升级为分布式复制协议，没有对应产品收益。Selection继续使用已有ID 17 player-state路径；把它重新并入collection transaction会复制authority并制造跨消息协调。

Player-state、收藏快照与选模请求同样只消费当前一次表现机会。Reporter 在调用宿主前推进当前观测和初始/周期 FULL 时序，server 在事实成功应用后再尝试每个下行通知；失败不保留 dirty、待发、失败或恢复状态，也不把本地提交解释成 peer 接纳。真实 FULL baseline 仍在 exact model session 内阻止无基线 DELTA，但不借 sequence、revision 或发送 generation 承诺去重、顺序或最终收敛。

## Accepted resource 只有一个 dispatch owner

一次 child enqueue 划定唯一 ownership frontier，使 queue snapshot、memory-pressure sensor 或 session-local worker 无须再承担 capacity/scheduler authority；完整机制见[资产传输](asset-transfer.md#全局-dispatch-owner)。

业务 transfer owner 在获取 source 前建立，完整 packet 集合只从该 frontier 转移一次；拒绝或构造失败仍由业务 owner 释放局部能力，accepted 后只有 dispatch 能关闭 packet/source。按 cursor 有界读取并在 retry 时重建 frame，以额外局部 I/O 换取与文件大小无关的 queue residency 和单一 close owner。

## 资产分发保护复用既有 owner

[Gameplay 消息优先级](../../product-decisions/decisions/workload-budget.md#ddgameplay-traffic-precedes-asset-distribution)通过[分发负载保护](asset-transfer.md#分发负载保护)落到发送侧与需求侧。全局限流约束总量，背压检测响应连接的实时发送压力，per-player 限制约束单玩家负载，客户端防抖减少需求扰动；它们处理不同来源的压力，单靠分发总速率不能说明连接发送队列仍有余量。

发送侧复用既有 dispatch owner 的调度与接纳边界，避免增加第二套容量状态和跨 owner 协调；客户端防抖降低请求频率，但不能承担服务端的容量保护。代价是模型取得可能延后，容量不足时资产请求可能被局部拒绝；具体阈值和等待策略属于可调整的实现取舍。

## Connection 是延后工作的完整边界

跨会话结果隔离见 [DD.session-scopes-remote-state](../../product-decisions/decisions/session-isolation.md#ddsession-scopes-remote-state)。Publication、typed transfer、activation、cache/page commit 和 entity/world projection 共用 exact Forge connection 的 admission 与 retirement，避免在每个 handler 增加 generation/tombstone 状态。退出时的数据保留范围见 [Transport 与 session](transport-and-session.md)。

## 产品约束

展示、选择和请求接纳见[model-authorization](../../product-decisions/decisions/model-authorization.md)；客户端输入与服务端能力底线见[trust-boundaries](../../product-decisions/decisions/trust-boundaries.md)。

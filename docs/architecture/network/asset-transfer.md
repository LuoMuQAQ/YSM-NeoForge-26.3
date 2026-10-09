# 资产传输

Model distribution 有三个独立业务 owner：catalog activation 批量取得 metadata prefixes；一次 model activation 取得 definition 与 required textures；GUI page 聚合 preview、icon 与 pack cover slots。三者共享 physical frame/dispatch 能力，但不共享 generic batch、assembler、member registry 或 parent runtime。

## Typed request 与 action owner

每个业务 owner先形成完整 canonical miss descriptor。全 cache hit不分配 ID；descriptor能编码进一个 physical frame时只建立一个`data_transfer_id`。只有完整 descriptor超帧时，owner才按 canonical order切成有限个完整 child requests；当前实现顺序启动 children，因此任一时刻一个 parent至多有一个 started nonterminal child。

一个 child独立拥有 admission、typed assembly、cancel与唯一 terminal。Parent只保存未启动 descriptor、已分配 child ID/outcome和一个对外 result，不持有 child backing。Early child提交的 immutable exact cache entry不因later child `BUSY`回滚；parent失败或取消时停止未启动 child并取消 started nonterminal child，迟到 outcome只释放原 child状态，不能重开 parent。全部 required child与cache hit完成后才发布一次业务结果。

三个 wire request分别是：

- `MetadataPrefixRequest`：按 canonical `container_id` 列表请求连续 metadata prefixes；
- `ModelChunkRequest`：按 exact model/container identity 与 canonical chunk descriptors 请求 definition/required texture stored bytes；
- `PresentationPageRequest`：按 typed slot 请求 preview、icon 或 pack cover。Preview member 只携带 `slot/model_id/container_id`，不以 chunk name/hash/size/encoding 寻址；icon 与 pack cover 保留完整 descriptor。Optional resource 可返回 `UNAVAILABLE`。

精确字段、numeric limits与fragment completion规则见[当前协议](../../standards/protocol-v1/README.md#typed-model-distribution)。

## Server admission 与 source closure

Server handler 先执行[协议 structural pass](../../standards/protocol-v1/README.md#typed-model-distribution)；malformed request 不推进 connection-local high-water。通过后才推进 high-water 并进入 business pass。

Business pass以当前 `ServerModelSession` 检查catalog/container identity、presentation selector、grants/forced-selection authorization与source descriptor。整份 descriptor全部验证后才创建任何 source plan；protected chunk授权早于source lookup。业务 transfer owner 在开始任何 source acquisition 前建立，使同步 metadata、异步 chunk 与 presentation 都由同一责任承接取消、迟到完成和 terminal。Pack cover只能从owner-fixed catalog root取得：hierarchy先规范化，logical resolve后检查below-root，再将root与file解析为physical path并再次检查below-root和regular-file属性。

Metadata source是有界borrowed prefix；large model、icon 与 pack cover source只形成normalized file path、range与强lease。Queued transmission不保存完整文件或预生成frame list，只在访问当前 cursor 时读取一个有界 frame。Source unavailable按该业务的failure/outcome映射，不会创建未归属capability。

Preview 在 membership、exact model/container 与 presentation 资格验证后，先读取容器中声明的有效 thumbnail；缺失时把 `ContainerId` cache probe 交给独立 server runtime worker，同一 action 的多个 cache probe 顺序执行。Owner tick 只消费完成事实，再构造内嵌图、cache bytes 或 `UNAVAILABLE` transmission；服务端不因 preview 请求 bake 或生成。内嵌、cache、icon 与 cover 的实际发送 bytes 合计不得超过 128 MiB，单张 preview encoded bytes 不超过 16 MiB。

Bytes、verified chunk lease 与 file range 由一个 range packet 的三种 source capability 承接共同的有界读取、fragment 构造与逻辑终结。Bytes 在接管外部可变数组时只做一次隔离副本；chunk 保持实际 lease 到 packet 终结；file 保存规范路径、声明 range 与 backing 强引用，每次 cursor 只打开本次读取句柄。业务 owner 解释 natural key、授权和 typed failure，source 只解释自身范围和释放能力。

## Receiver-local assembly 与 publication

Typed receiver 按[协议 coverage 与终态规则](../../standards/protocol-v1/README.md#typed-model-distribution)组装 child，拒绝 request descriptor 外的 natural key。

Metadata/chunk 由[分层验证](../asset-pipeline/container-and-validation.md)建立内容事实，再交[Storage](../model-management/storage-and-cache.md)提交。Presentation slot 产生完整 validated data 或合法 `UNAVAILABLE`；page owner 等全部 slots terminal 后一次发布 staged futures。Preview receiver 以唯一 `final_fragment` 的结束 offset 动态确定范围，再检查从零开始的完整 coverage、duplicate 一致性、overlap/gap、未知 member、单图 16 MiB 和 action 实收 128 MiB；完整媒体解码还限制宽高各 4096、像素数 16 Mi。它不校验服务端提供的图片 hash或模型绑定，随后只按 `ContainerId` 提交独立 cache。Icon 与 pack cover 仍按 descriptor size/hash/encoding 验证。

Remote metadata/chunk 的 physical path confinement 统一见[Storage](../model-management/storage-and-cache.md)，可用远端能力由[协议](../../standards/protocol-v1/README.md#安全与能力边界)限定。Pack cover bytes 验证 size/hash/format 后交固定 image probe；其 catalog-root 检查由上述 source owner 负责。

## 分发负载保护

产品优先级由 [DD.gameplay-traffic-precedes-asset-distribution](../../product-decisions/decisions/workload-budget.md#ddgameplay-traffic-precedes-asset-distribution) 定义。当前保护机制覆盖四个位置：全局限流控制服务端资产分发总速率；背压检测感知 transport 发送压力；per-player 软限制与硬限制约束单玩家分发负载；客户端防抖减少短时间需求变化带来的请求扰动。

Dispatch 侧的限制、背压和接纳共用下述唯一 owner，客户端防抖留在需求发起侧。机制的职责分工与成本见[网络决策理由](design-rationale.md#资产分发保护复用既有-owner)。

## 全局 dispatch owner

`ResourceDispatchWorker`由server model service唯一持有：

- 一个 child 的全部 transmissions只调用一次`enqueue()`；hard limit在worker内原子接受或拒绝，业务层不读取heap/native/OS memory或queue snapshot；
- reject/exception不转移ownership，caller回滚child registration、关闭still-owned packets/sources并返回一个`BUSY`；success后dispatch独占cursor、retry、current-frame/source close；
- packet callback 在 source acquisition 前已经绑定既有 transfer owner；不存在晚绑定 owner 或可变 callback bridge；
- 完整 packet 集合在单一提交临界区内全有或全无地转移，构造失败时分别关闭未封装 source 与已封装 packet 前缀；
- siblings独立接纳，不存在cross-transfer reservation、all-children admission或rollback；
- 每玩家一个FIFO queue，非空玩家round-robin；soft/hard默认24/48且满足`1 <= soft < hard <= 256`；
- 构造frame前检查transport writability/pending bytes，构造后按真实frame size复检；cursor只在`trySend`成功后推进；
- transient failure在connection仍有效时固定等待100 ms后重建同一cursor frame；cancel、disconnect、production failure与shutdown由同一owner关闭accepted source；
- queue/session monitor内只detach terminal packet与状态，physical close及其business-owner callback在monitor外执行，因此enqueue与completion没有反向lock edge。

Resource interest 的取消权限由[所有权页](../model-management/ownership-and-lifecycle.md)定义。Model worker 在 network input terminal 且 cache 提交后启动，finite-work 约束见[Reload](../model-management/reload-and-publication.md)。页面关闭只取消 exact page action，不关闭 dispatch/cache 已拥有的 backing。

输入校验与信任边界由[产品决策](../../product-decisions/decisions/trust-boundaries.md)定义。

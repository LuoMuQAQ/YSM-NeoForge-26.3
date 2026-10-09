# REQ.control-local-presentation

- Claim: 玩家能够在自己的客户端屏蔽妨碍正常游玩的模型替换，并调节模型音效，保留正常游戏所需的原版视听体验。
- Decision-status: Complete
- Motivation: [SCN.model-content-obstructs-play](#scnmodel-content-obstructs-play)
- Constraints: [CON.visual-only-scope](../shared.md#convisual-only-scope); [CON.normal-use-isolation](../shared.md#connormal-use-isolation)

## SCN.model-content-obstructs-play

- Claim: 自身或其他玩家的模型、第一人称手臂可能遮挡视野，模型声音也可能干扰游戏；内容没有技术错误时，玩家仍需要主动控制这些影响。
- Motivation: [PG.preserve-host-gameplay](../shared.md#pgpreserve-host-gameplay)

## Select

| 任务涉及 | 决策包 |
|---|---|
| 屏蔽自身、他人或指定玩家模型、恢复第一人称手臂、音效音量 | [local-presentation-control](../decisions/local-presentation-control.md) |

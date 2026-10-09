# REQ.authoritative-model-lifecycle

- Claim: 玩家选择、热加载和联网来源变化时，模型使用者必须得到身份明确、内容完整且仍然有效的模型，或进入约定的降级表现。
- Decision-status: Complete
- Motivation: [PG.creator-controlled-content](../shared.md#pgcreator-controlled-content); [PG.model-replacement](../shared.md#pgmodel-replacement)
- Constraints: [CON.default-failure-exception](../shared.md#condefault-failure-exception); [CON.normal-use-isolation](../shared.md#connormal-use-isolation)

## Select

| 任务涉及 | 决策包 |
|---|---|
| 来源与业务身份、同源复用与精确内容 | [model-identity](../decisions/model-identity.md) |
| 目录与内容完整发布、热加载与已有使用 | [catalog-publication](../decisions/catalog-publication.md) |
| 分类降级、默认启动基线与恢复机会 | [model-fallback](../decisions/model-fallback.md) |

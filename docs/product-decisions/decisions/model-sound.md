# model-sound

- Requirement: [REQ.evaluate-entity-animation](../requirements/req-evaluate-entity-animation.md#reqevaluate-entity-animation)
- Select: 动画/脚本声音触发与播放义务
- Needs:
  - 玩家调节或静音模型音效: [BC.model-audio-volume-is-user-controlled](local-presentation-control.md#bcmodel-audio-volume-is-user-controlled)
  - 声音资产取得: [DD.catalog-before-model-body](model-distribution.md#ddcatalog-before-model-body)
  - 播放条件副作用: [DD.render-observation-does-not-repeat-actions](animation-isolation.md#ddrender-observation-does-not-repeat-actions)
  - 历史模型声音: [DD.semantic-freeze-at-export](model-compatibility.md#ddsemantic-freeze-at-export)
- Landing:
  - [standard](../../standards/model-schema/assets-and-validation.md#common-stringssettings-与音频)
  - [status](../../status/known-issues/animation.md#求值与语义)

## DD.model-sounds-are-observable-content

- Claim: 模型声明的声音必须能够按受支持的动画或脚本触发语义在游戏中播放，实际可听结果服从玩家的本地音效控制。
- Rationale: 声音是完整模型定义的一部分，只保存资产而不形成可听结果会丢失作品的一部分表达；播放应与几何、动画一样成为可观察的模型行为。

### BC.model-sound-triggers-play-audio

- Claim: 有效模型声音资源在声明的播放条件满足时按本地音效设置播放；用户主动调低或静音不属于模型声音语义丢失。资源或播放失败仍按普通模型错误隔离，不损害游戏进程和无关内容。

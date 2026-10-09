# Native 能力层复杂度总览

本主题负责 JNI 绑定与内存协议、音频媒体解释/decoder、能力相关 bake/cache、逐帧 state、并行 worker 与顶点输出、archive 读取以及 legacy importer。Java 领域 authority、模型资源 lease/音频保留、页面策略和宿主 consumer adapter 分别归 [MN](../java-models-and-network/README.md)、[AP](../java-assets-and-presentation/README.md) 与 [AN](../java-animation-and-integration/README.md)。

## 阅读路径

| 问题 | 页面 |
|---|---|
| NR 机制的行为、删除失败与证据入口 | [机制](mechanisms.md) |
| Native 计算、边界和平台覆盖到哪里 | [证据、覆盖与根依据](evidence-and-scope.md) |
| JNI、布局、worker、借用和 legacy 的因果关系 | [因果图与高扇出候选](causal-graph.md) |
| Cache、archive、补偿机制可以如何归约 | [归约支点](reduction-pivots.md) |
| Allocator、cache identity 等哪些前提未闭合 | [因果缺口与差异](open-questions.md) |

## 子系统拓扑

| 机制簇 | 主要机制 | 边界 |
|---|---|---|
| JNI、ABI 与音频 | NR-01–03、14–15 | 一次性绑定、范围/owner 交接、音频解释/decoder、失败收口与 allocator 补偿 |
| Bake、cache 与帧 state | NR-04–07 | SIMD 热布局、serialized cache、借用 frame view 与计划失效 |
| 并行输出 | NR-08–10、16 | 固定区间、完成协议、透明重排和容量空洞清理 |
| Archive 与 legacy | NR-11–13、17 | 借用 extraction、输入边界、typed projection 与图片表示归约 |

本主题拥有 NR 内部关系；跨域边由[跨子系统关系](../governance/cross-subsystem-relations.md)唯一维护，加载规则见[关系登记入口](../governance/relationship-register.md)。

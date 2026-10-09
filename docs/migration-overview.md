# 迁移概览

## 背景

旧版 YSM 的架构是在闭源、模型保护和减少攻击面的约束下逐步形成的。为了将模型格式、缓存、同步和加密逻辑隐藏在 native 层，Java 与 C++ 进行了深度绑定，C++ 最终承担了模型解析、缓存、同步、导出、烘焙、解码和渲染等大量职责。

这套系统能够完成从模型读取到多人同步和渲染的完整闭环，但代价也越来越明显：

* Java 与 C++ 之间存在大量业务级 JNI 接口，修改一个功能往往需要同时理解两侧实现。
* 许多模组业务语义被放在 C++ 中，调试、跨版本适配和自动化测试都比较困难。
* 旧架构缺少系统文档，又有较高的 C++ 门槛，实际维护工作长期集中在少数熟悉内部实现的人身上。
* 项目的保密要求进一步限制了代码交流和协作，使新人很难逐步参与。
* 即使对熟悉旧架构的维护者而言，继续扩展这套系统也越来越困难，部分工作长期处于缓慢推进甚至停滞状态。
* 这套盘根交错的设计与架构主要服务于旧版保护体系，项目开源后不再产生相应价值。

旧架构是特定约束下的解决方案。但项目准备开源后，继续沿用它会把已经失去意义的安全成本转化为长期技术债。因此新版工作的重点不是修补旧系统，而是重新划定职责边界，使主要业务可以被普通 Java 模组开发者理解、测试和扩展。

## 迁移方向

| 旧主线                        | 新主线                                                                    |
| -------------------------- | ---------------------------------------------------------------------- |
| C++ 拥有模型业务与同步状态            | Java 拥有格式语义、目录、缓存策略、同步、导出、动画和生命周期                                      |
| 缓存 ID、模型密钥、会话密钥和加密缓存共同定义身份 | `ModelId` 定义模型身份，`ContainerId` 定义 network、storage 与私有 baked cache 的精确 representation |
| JNI 传递业务对象并跨语言驱动生命周期       | Protobuf、明确布局的 buffer 和 typed handle 通过 owner / view 契约交接              |
| 以整包模型缓存为同步单元               | metadata-first，资产按用途请求、验证、缓存和回收                                        |

Native 能力层只保留压缩、hash、图像、archive、bake / extract / render 等可替换能力，不拥有来源、权限、会话或缓存策略。兼容可确定解释的旧模型是项目级目标，但旧格式兼容限制在单向输入边缘，不能把旧加密、密钥或 native 模型管理带回新主线。Raw source 写出 Asset Container 是一次导出物的语义冻结点；迁移保护其已定义的可观察语义，不承诺保留无运行效果的历史表示。完整边界见[模型兼容与容器语义冻结](concepts/model-compatibility.md)。

## 当前进度

已形成的主线：

- Asset Container、Model Schema 与当前 protocol 已建立标准和实现入口；
- raw capture、builtin/custom/auth local catalog、完整候选 reload、converted/remote storage、共享 resource lease 和 Cleaner 所有权已在 Java 接线；
- Forge 游戏服模式已切换到唯一 `EventNetworkChannel` 路径，model session、远端 catalog、逐请求授权、按需资产分发、玩家/entity 状态与控制消息已接线；
- Java 动画到 native bake / extract / render 的 CPU 渲染主链已接线；
- v3 加密容器到当前容器的[单向导入](architecture/asset-pipeline/conversion-and-export.md#历史输入的单向投影)已接线。

仍待完成：

- 模型音频仍需真实 legacy v1/v2/v3 来源、远端 session、Minecraft/OpenAL 设备、stream pool 容量和长期回收验收；
- 将 x64 ISA 基线降至 x86-64-v1 并适配 Windows 7 属于平台迁移范围；
- 第一人称、附着 layer、透明渲染和模组联动等旧能力的完整恢复；
- 格式与协议 golden、真实双端 session/在线 delta/授权矩阵/远端资产、断线竞态、恶意输入、跨平台和 Minecraft 视觉验收；
- 独立 Backend、通用外部模型源和 GPU Compute Renderer 等后续方向。

“已接线”只表示主要逻辑存在，不代表稳定、完整或已经通过实机验证。权威边界见[当前支持状态](status/support-and-verification.md)。

## 已知问题

- [格式、Schema 与编解码](status/known-issues/format-and-schema.md)
- [网络与同步](status/known-issues/network.md)
- [模型管理](status/known-issues/model-management.md)
- [动画](status/known-issues/animation.md)
- [渲染](status/known-issues/rendering.md)

## 注意

模型管理横跨 catalog、缓存、网络、所有权和失败恢复，且已有大量 AI 辅助生成的代码，人工长期记忆全部局部细节的成本过高。

建议由人工负责目标、边界、顶层决策、评审和质量门禁，由 AI 细化设计并落地实现、测试和文档。

但 AI 参与不构成正确性证明，这项建议也不能成为接受低质量代码的理由。

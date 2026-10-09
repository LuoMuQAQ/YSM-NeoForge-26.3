<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 总览

## 文档目录

- [构建指南](build.md)：配置本地 native 库、运行开发客户端和构建最终 JAR。
- [局域网与离线登录](lan.md)：可选联机设置模组、房主设置及当前验证边界。
- [迁移概览](migration-overview.md)：旧版架构困境、迁移方向与当前进度。
- [术语表](glossary.md) / [文档政策](governance/documentation-policy.md)：统一名称与信息取舍规则。
- [产品决策](product-decisions/README.md)：声明范围内产品目标、需求、业务约束、领域决策、理由与稳定行为契约的 Root Authority。
- [复杂度地图](complexity-map/README.md)：当前机制的因果来源、高扇出决策、归约支点与待裁决缺口；不拥有产品或架构决策权威。
- [模型兼容与容器语义冻结](concepts/model-compatibility.md)：旧模型单向迁移目标、raw export 的语义边界与 representation normalization 原则。
- [扩展模组兼容性检测](extension-compatibility.md)：扩展入口注解处理器、生成的启动期 checker、检测范围与 `@YsmEventHandler` 自动注册。
- [独立格式标准](standards/README.md)
- [架构总览](architecture/README.md) / [运行模型](architecture/runtime-model.md)：跨语言职责、问题定位、side、线程与生命周期。
- [当前支持状态](status/support-and-verification.md)
- 未来方向：
  - [独立 Backend](future/independent-backend.md)
  - [外部模型源](future/external-model-sources.md)
  - [模组动画联动](future/mod-animation-integration.md)
  - [GPU Compute Renderer](future/gpu-compute-renderer.md)

<a id="subsystem-overviews"></a>

## 文档检查

修改文档后在工程根运行 `python docs/tools/check_docs.py`。

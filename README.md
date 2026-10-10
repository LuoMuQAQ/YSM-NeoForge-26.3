<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# YSM — Unofficial NeoForge 26.3 Port

基于官方 [Yes Steve Model](https://github.com/YesSteveModel/YesSteveModel) 开源源码的非官方 NeoForge 移植版，由 LuoMuQAQ 维护。项目提供源码与编译好的 JAR，未获原项目作者背书。

## 功能

- 使用自定义模型替换玩家、投射物和载具外观。
- 加载内置模型及获合法授权的第三方 `.ysm` 模型。
- 播放模型动画、动作与声音，提供动作轮盘。
- 提供第一人称手臂、左上角角色预览和模型选择界面。

## 运行环境

**Minecraft 26.3 · NeoForge 26.3.0.58-beta · Java 25 · Windows x86_64**

当前发行版仅提供 Windows x64 原生库，要求使用上述 NeoForge 版本。

## 下载与安装

1. 从 [Unofficial 9 Release](https://github.com/LuoMuQAQ/YSM-NeoForge-26.3/releases/tag/v3.0-unofficial.9-mc26.3-nf58) 下载 `ysm-3.0-unofficial.9+mc26.3-nf58.jar`，放入对应实例的 `mods` 目录。
2. 更新时替换旧 YSM，确保实例中只保留一个 YSM JAR。
3. 将第三方模型放入实例游戏目录的 `ysm/custom/`，按 `Alt+Y` 打开模型选择。
4. 按 `Z` 打开动作轮盘，使用当前模型提供的动作。

需要设置局域网端口或允许离线玩家加入时，参阅 [局域网与离线登录指南](docs/lan.md)。联机设置模组需单独安装。

## 内置模型与许可

发行包包含 24 个原版内置模型，包括魔法酒狐：5 个 CC0、19 个 CC BY-NC-SA 4.0。星屑海螺、纸板、小小酒狐因缺少本分支再分发授权而未包含；第三方模型由用户自行取得。

- 程序代码：除另有声明外，使用 [Apache-2.0](LICENSE)。
- 模型资产：使用各自许可，作者署名见 [BUILTIN_MODELS.md](BUILTIN_MODELS.md)。CC BY-NC-SA 模型须署名、非商业使用，改作按相同许可分享。
- 第三方代码、库与其他许可说明：见 [NOTICE.md](NOTICE.md)。

程序的 Apache-2.0 许可不覆盖模型资产。

## 更新与反馈

当前版本为非官方预发布版。版本更新见 [Releases](https://github.com/LuoMuQAQ/YSM-NeoForge-26.3/releases)，兼容范围与已知问题见 [当前支持状态](docs/status/support-and-verification.md)。

问题请提交到 [本仓库 Issues](https://github.com/LuoMuQAQ/YSM-NeoForge-26.3/issues)，注明游戏、加载器、模组和模型版本，附复现步骤及清理个人信息后的日志。

## 源码与构建

仓库使用 `main` 开发，发行版源码对应各 Release 的 Git 标签。Java 源码位于仓库根目录，Native 源码位于 `native/`。

构建方法见 [构建指南](docs/build.md)，设计文档见 [文档入口](docs/README.md)，源码来源见 [source-provenance.json](release/source-provenance.json)，移植修改见 [MODIFICATIONS.md](MODIFICATIONS.md)。

原项目由 YS Group、TartaricAcid、TomatoPuddin、AryochiL 等贡献者开发。此移植使用生成式 AI 辅助编码与排查，保留原作者署名。

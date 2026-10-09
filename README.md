<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# YSM — Unofficial NeoForge 26.3 Port

Unofficial Windows x86_64 port of [Yes Steve Model](https://github.com/YesSteveModel/YesSteveModel)
for **Minecraft 26.3 / NeoForge 26.3.0.58-beta / Java 25**.
Maintained by LuoMuQAQ. This project is not affiliated with or endorsed by the YSM authors.

这是基于官方开源源码的非官方兼容移植版，支持替换玩家等实体模型，并读取第三方模型。
代码公开，编译好的 JAR 放在 [GitHub Releases](https://github.com/LuoMuQAQ/YSM-NeoForge-26.3/releases)。

## 下载与安装

1. 此分支使用 **Windows x64、Java 25、Minecraft 26.3、NeoForge 26.3.0.58-beta**。
2. 从 [NeoForge 58 Release](https://github.com/LuoMuQAQ/YSM-NeoForge-26.3/releases/tag/v3.0-unofficial.2-mc26.3-nf58) 下载 `ysm-3.0-unofficial.2+mc26.3-nf58.jar`，放入对应实例的 `mods` 目录。旧版 Unofficial 1 Release 针对 52-beta，不能用于 58-beta 实例。
3. 确保实例中仅有一个 YSM JAR；更新时替换旧文件。
4. 进入游戏后按 `Alt+Y` 打开模型选择，按 `Z` 打开动作轮盘。
5. 自行取得合法授权的第三方模型放入实例游戏目录的 `ysm/custom/`。

此版仅提供 Windows x86_64 native DLL。Linux、macOS、Android 没有本项目的发行二进制。
加载器版本精确锁定到上述版本；更新 NeoForge 不代表这个 JAR 自动兼容。

## 内置模型与许可

仅包含 24 个上游原版内置模型：5 个 CC0、19 个 CC BY-NC-SA 4.0，包括魔法酒狐。
星屑海螺、纸板、小小酒狐未获本分支再分发授权，已排除。
没有打包本地测试使用的第三方模型、用户配置、存档或日志。

- 程序代码：除另有声明外，使用 [Apache-2.0](LICENSE)。
- 内置模型：使用各自清单中的许可；NC-SA 模型须署名、非商业使用，改作按相同许可分享。
- 独立格式标准：保留上游 CC0 声明。
- 第三方代码和库：保留各自许可证，见 [NOTICE.md](NOTICE.md)。
- 完整内置模型署名见 [BUILTIN_MODELS.md](BUILTIN_MODELS.md)。

程序许可不会覆盖模型资产。不要把整个 JAR 的全部内容视为 Apache-2.0。

## 当前状态

这是公开测试版。维护者已开始 NeoForge 58 实测，并据反馈修复了 Iris PBR 模型加载与攻击动画重播判定；
本次攻击修复的连续攻击表现和完整组合仍待游戏内复测。
源码编译、资源打包成功不等于所有模型、设备和模组组合都已经验证。

目前已处理实体与第一人称提交、HUD 预览、模型卡片名称、持物、Z 轮盘等迁移问题。
退出清理已接入 NeoForge 宿主生命周期，旧 NeoForge 玩家、投射物与载具记录的离线副本工具和来源校验迁移桥已接线。
Iris 1.11.7 的 PBR holder 延迟加载已适配；默认攻击动画按实际新挥手触发重播，避免错过第 0 tick 后同名攻击只播放一次。
生产构建、24 个内置模型物化、包内容及 22 个 Mixin 的 114 项字节码静态核对通过。
退出清理、攻击动作与声音、LAN、光影组合、旧版世界升级保存和全部可选模组联动仍需完整实机验收。
不要把旧世界直接用于迁移测试；保存副本后再尝试。

问题请提交到 [本仓库 Issues](https://github.com/LuoMuQAQ/YSM-NeoForge-26.3/issues)，
注明游戏、加载器和模型版本，附经过个人信息清理的日志。
本项目的移植问题由本仓库维护者处理。

## 源码与构建

Java 源码位于仓库根目录，匹配的 Native 源码位于 `native/`。
构建依赖的可选模组仅作为编译输入，脚本从作者发布地址获取并核对固定哈希，
不会打入发行 JAR。仓库包含上游 QuickBuffers 构建工具及其许可。

构建流程见 [docs/build.md](docs/build.md)，上游设计文档见 [docs/README.md](docs/README.md)。
来源版本和 Native DLL 哈希见 [release/source-provenance.json](release/source-provenance.json)，
移植修改摘要见 [MODIFICATIONS.md](MODIFICATIONS.md)。

原项目由 YS Group、TartaricAcid、TomatoPuddin、AryochiL 等贡献者开发。
移植使用了生成式 AI 辅助编码和排查，并由维护者提供手动游戏反馈；原作者署名保留。

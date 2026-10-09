<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 局域网与离线登录

适用于 Windows x86_64、Minecraft 26.3、NeoForge 26.3.0.58-beta、Java 25。要同步 YSM 模型，房主和访客应使用同一 YSM 构建；旧 1.21.1 YSM 不能作为当前网络协议的对端。

## 联机设置模组

需要离线玩家加入时，可使用独立的 [LAN World Plug-n-Play 2.1.4（26.3 NeoForge）](https://modrinth.com/mod/mcwifipnp/version/m51OXj2s)，文件名为 `mcwifipnp-2.1.4-26.3-neoforge.jar`。作者为 Satxm、Rikka0w0，使用 Apache-2.0 许可。此文件需从作者页面单独下载，YSM JAR 不内置它。

旧 Lan Server Properties 1.13.2 的 Minecraft 声明范围为 `[1.20.5, 1.22)`，不能直接用于 26.3；其作者已在[项目页面](https://github.com/rikka0w0/LanServerProperties)推荐改用 LAN World Plug-n-Play。

## 房主设置

1. 将对应 JAR 放入实例的 `mods`，完全退出并重新启动游戏。
2. 进入世界，打开“对局域网开放”。
3. 将“正版验证 / Online Mode”设为“禁用”，允许离线登录；需要保留已有正版身份时，可选“禁用 + 修复 UUID”。已有玩家保持名字和 UUID 策略一致，避免同一玩家被识别为不同身份。
4. 设置端口并开放世界。使用内网穿透或其他联机工具时，其本地目标端口应与游戏公布的端口一致；例如都使用 `25565`。
5. 访客使用匹配的 Minecraft、NeoForge 和 YSM 版本，通过房主提供的实际连接地址加入。仅在访客启动器选择离线登录，不会改变房主的验证模式。

这些设置由房主和联机模组管理；安装 YSM 不会自动关闭账号验证或修改网络配置。选项含义及 UUID 规则以[联机模组作者说明](https://github.com/Satxm/mcwifipnp/blob/26.3/README.zh-CN.md)为准。

## 验证边界与排查

联机模组的发布声明覆盖当前 Minecraft 与 NeoForge，文件哈希、JAR 完整性和元数据已核对；这只证明安装输入匹配，尚未完成该组合的离线访客登录、首次模型资源取得、互见和重连验收，见[网络已知问题](status/known-issues/network.md)。

登录阶段超时不足以证明 YSM 协议冲突。排查时提供双方版本、连接方式、失败原文和清理个人信息后的日志，先确认是否完成 Minecraft 登录，再检查 YSM 资源同步。局域网列表中的广播发现与通过地址建立连接是不同步骤，广播失败不能单独解释所有登录失败。

<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 构建指南

当前 Java 工程依赖匹配的 native 库。公开源码包含 Native 实现和构建脚本；Windows Java 构建可以复用 Release 内的匹配 DLL，也可以从 Native 源码自行构建。

## 目标平台与构建状态

当前开发目标为 Minecraft 26.3 / NeoForge 26.3.0.58-beta / Java 25，使用 ModDevGradle 2.0.148 和 Gradle 9.1.0。构建配置使用真实 NeoForge userdev/NeoForm 依赖，旧 Forge 编译与发行依赖不进入此工程。全部生产源码与 `shadowJar` 已构建通过；游戏启动、运行时 Mixin 应用和外观仍需实机验证，不能将构建成功解释为可运行支持。

Native/schema 业务实现沿用官方主线；builtin 构建工具同样运行在 Java 25，日志与 LWJGL 使用目标游戏实际解析版本。Windows 的 protoc 无法可靠处理中文物理项目路径，请将仓库克隆到仅含 ASCII 字符的路径；仅使用驱动器映射不足以避开 Gradle 的路径规范化。

## Java 构建工具

Java toolchain 为 25。使用官方 NeoForge userdev/NeoForm 生成目标源码和依赖；Windows 上的 builtin 工具使用目标游戏解析的 LWJGL 3.4.3、Log4j 2.26.0 与 SLF4J 2.0.17。构建工具 runtime 不打入模组分发包。

Curios、Carry On、First Person、Better Combat、Iris、Shoulder Surfing、Sodium、Real Camera、Sophisticated、Cloth Config、Not Enough Animations、PAL 和 Jade 的真实目标依赖只用于 `compileOnly`，不会打入分发包。QuickBuffers 与扩展 checker 使用的 relocated ASM 随产物打包；宿主已提供的依赖不重复分发。

可选 API 文件清单、作者下载地址和哈希在 `release/compile-dependencies.json`。Windows 上先运行 `scripts/prepare-build.ps1`，脚本把这些 JAR 下载到 Git 忽略的 `libs/compile-only/`，并从固定 Release 提取匹配的 Windows DLL。构建不依赖维护者的本机目录。

```powershell
.\scripts\prepare-build.ps1
.\scripts\build-release.ps1 -JavaHome '<JDK25安装目录>'
```

`-JavaHome` 应指定自己的 JDK 25 路径。构建脚本检查 ASCII 物理项目路径，使用仓库内的构建缓存和临时目录，结束后恢复环境。Java 构建产物位于 `build/libs/`。

若已下载该 Release JAR，准备步骤可以使用 `-ReleaseJar` 指定其路径。若已持有固定哈希的 DLL，可使用 `-NativeDll` 指定其路径。这些输入仍按清单核对哈希。

编译源码使用 `./gradlew compileJava`。本工程只配置构建产物任务，游戏运行由外部目标实例控制。

## 构建 JAR

1. 按照仓库 `native/docs/build_cn.md` 构建所需平台的 native 库。发行 DLL 使用 clang-cl 22.1.5、静态 MSVC runtime、Windows SDK 10.0.26100；完整构建配置见 `release/windows-clang-profile`。在已初始化相应编译器环境的命令行中，先执行 `native/bootstrap.cmd setup`，再按 Native 指南执行 `build native`；基准测试和 Tracy 未启用。自行构建的 DLL 可能因工具链不同而具有不同哈希，使用它时将库手动放到下述目录，并运行准备脚本的 `-SkipNative` 模式，只获取编译 API。
2. 将这些库放入 `src/main/resources/META-INF/native/`。当前运行时使用的资源文件名为：Windows x86_64 的 `ysm.dll`、GNU/Linux x86_64 的 `libysm.so`，以及 Android arm64 的 `libysm-android.so`。Android 构建生成的 `libysm.so` 需要以 `libysm-android.so` 放入该目录。
3. 在 Java 项目根目录运行：

   ```bash
   ./gradlew shadowJar
   ```

   Windows PowerShell 使用：

   ```powershell
   .\gradlew.bat shadowJar
   ```

最终 JAR 位于 `build/libs/`。

公开 Release 当前只包含 Windows x86_64 的 DLL。其他平台源码保留，但没有本分支的发行库或运行验收。Native 和 JAR 分发时应一同保留第三方许可及相关 NOTICE/PATENTS 文件；资源中的许可材料由 JAR 打包保留。

## Mixin 接口边界

对类目标使用的访问器接口仅保留 `@Accessor` / `@Invoker` 成员。普通非 synthetic 方法（包括 `default` 方法）会使 Mixin 将它分类为普通接口 Mixin，并拒绝类目标；业务转换应在调用方完成。宿主字段/方法描述符匹配不能替代此类型检查，编译成功也不代表启动期 Mixin prepare/application 已通过。

# 构建指南

以下是面向贡献者的通用构建流程，主要用于本地开发、测试和日常贡献，可能与维护者所使用的发布环境不完全一致。

本项目的依赖比较杂，且需要适配多种目标平台。维护者会尽力保证这套构建流程可用，但无法确保在每台机器、每种构建环境的组合下都能成功构建。

如果在构建过程中遇到问题、需要协助对齐预期的构建环境，或想排查发布版与本地构建的差异，请在贡献者交流群中寻求帮助。

## 0. 前置要求

- Git >= 2.49（很重要）
- 良好的网络环境（能流畅访问 GitHub)
- 仅 Windows：Visual Studio 2022+ (启用 C++ 负载和 CMake 组件)
- 仅 GNU/Linux：build-essential（支持 C++23）
- 仅 Android：Android NDK（支持 C++23）
- 仅 macOS：XCode（支持 C++23）
- CMake >= 3.29 + Ninja （VS 和 NDK 已集成）
- NASM
- Conan >= 2.28
- 确保 `PATH` 环境变量里没有 Yasm

## 1. 索引依赖项

在项目根目录执行:

Windows 示例:

```bash
.\bootstrap.cmd setup
```

<sub>*如果使用 Visual Studio MSVC 构建，要先为命令行设置 vcvars*</sub>

Linux 示例:

```bash
./bootstrap.sh setup
```

这一步会把 recipe 索引到仓库内的 Conan 缓存 `conan/cache`，通常只有首次构建项目或依赖项发生变化时才需要执行。

## 2. 构建项目

在项目根目录执行:

Windows 示例:

```bash
.\bootstrap.cmd build native
```

Linux 示例:

```bash
./bootstrap.sh build native
```

完成后可使用支持 CMake 的 IDE 打开并索引此项目。

# 官方构建环境

此为官方构建使用的完整工具链和环境，可基于此复现官方构建。

<sub>*有点复杂，常规开发不推荐手动复现。*</sub>

## 工具链

| 组件 | 版本     |
| --- |--------|
| LLVM | 22.1.5 |
| CMake | 4.3.4  |
| Ninja | 1.13.2 |
| NASM | 3.01   |
| Conan | 2.29.1 |

- Windows target 使用 clang-cl 编译器前端；
- 所有 target 均使用 lld 链接器。

## 目标环境

| 目标 | 内核/平台基线                                    | C 标准库 | STL + runtime                             | SDK / sysroot |
| --- |--------------------------------------------| --- |-------------------------------------------| --- |
| Windows x86_64 | Windows 10.0.26100.0 API Surface           | UCRT 10.0.26100.0 和 MSVC CRT 14.51 | MSVC STL v145 / 14.51                     | Windows SDK 10.0.26100.0 和 VC toolset 14.51.36231 |
| Linux x86_64 | Linux 3.13.9 内核头文件                         | glibc 2.19 | libc++ 22.1.5；libc++abi 和 libunwind     | Ubuntu 14.04 sysroot |
| Android arm64 | Android API 28       | Bionic | NDK libc++ 21.0.0；libc++abi 和 libunwind | Android NDK r29（29.0.14206865）sysroot |
| macOS arm64 | Darwin 23.5.0；macOS 14.5 deployment target | macOS SDK 提供的 libSystem | libc++ 22.1.5；libc++abi 和 libunwind     | macOS 14.5 SDK |

- macOS target 链接自构建的 `libc++_static.a`，因此不会使用 macOS SDK 提供的动态 libc++ runtime。
- Linux x86_64 和 macOS arm64 target 使用的 libc++ runtime 均启用了 unstable ABI v2.

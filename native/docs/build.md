# Build Guide

The following is the general build flow for contributors. It is intended for local development, testing, and ordinary contribution work, and may not exactly match the maintainer release environment.

The project has a complex native dependency stack and supports multiple target platform environments. The maintainers try to keep this flow usable, but cannot guarantee it will work smoothly on every machine or every build environment combination.

If you run into trouble, need help matching the expected environment, or need to investigate a release-build difference, ask for help in the contributor chat group.

## 0. Prerequisites

- Git >= 2.49 (important)
- Network access that can reach GitHub reliably
- Windows only: Visual Studio 2022+ (with the C++ workload and CMake components enabled)
- GNU/Linux only: build-essential (with C++23 support)
- Android only: Android NDK (with C++23 support)
- macOS only: Xcode (with C++23 support)
- CMake >= 3.29 + Ninja (Visual Studio and NDK already include them)
- NASM
- Conan >= 2.28
- Make sure Yasm is not in `PATH`.

## 1. Index Dependencies

Run this from the project root:

### Windows Example

```bash
.\bootstrap.cmd setup
```

<sub>*If you are building with Visual Studio's MSVC toolchain, you must first run the vcvars script to set up the command-line environment.*</sub>

### Linux Example

```bash
./bootstrap.sh setup
```

This indexes the dependency recipes into the repository-local Conan cache at `conan/cache`. You usually only need to run it for the first build or after dependency changes.

## 2. Build the Project

Run this from the project root:

### Windows Example

```bash
.\bootstrap.cmd build native
```

### Linux Example

```bash
./bootstrap.sh build native
```

After this step, you can open and index the project with a CMake-capable IDE.

# Official Build Environment

This is the complete toolchain and environment used for official builds and can be used to reproduce them.

<sub>*This setup is somewhat complex and is not recommended for routine development.*</sub>

## Toolchain

| Component | Version |
| --- | --- |
| LLVM | 22.1.5 |
| CMake | 4.3.4 |
| Ninja | 1.13.2 |
| NASM | 3.01 |
| Conan | 2.29.1 |

- Windows targets use the clang-cl compiler frontend.
- All targets use the lld linker.

## Target Environments

| Target | Kernel / platform baseline | C library | STL + runtime                                      | SDK / sysroot                                       |
| --- | --- | --- |----------------------------------------------------|-----------------------------------------------------|
| Windows x86_64 | Windows 10.0.26100.0 API surface | UCRT 10.0.26100.0 and MSVC CRT 14.51 | MSVC STL v145 / 14.51                              | Windows SDK 10.0.26100.0 and VC toolset 14.51.36231 |
| Linux x86_64 | Linux 3.13.9 headers | glibc 2.19 | libc++ 22.1.5; libc++abi and libunwind | Ubuntu 14.04 sysroot                                |
| Android arm64 | Android API 28 | Bionic | NDK libc++ 21.0.0; libc++abi and libunwind      | Android NDK r29 (29.0.14206865) sysroot             |
| macOS arm64 | Darwin 23.5.0; macOS 14.5 deployment target | libSystem from the macOS SDK | libc++ 22.1.5; libc++abi and libunwind | macOS 14.5 SDK                                      |

- The macOS target links a locally built `libc++_static.a`, so it does not use the dynamic libc++ runtime provided by the macOS SDK.
- The libc++ runtimes for the Linux x86_64 and macOS arm64 targets use unstable ABI v2.

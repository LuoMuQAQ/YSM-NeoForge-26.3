<!-- Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026). -->
# 注意

## 内置模型许可

内置模型资产不属于 Apache License 2.0。

`assets/ysm/builtin/**/ysm.json` 中的 `metadata.license` 是每个内置模型资产的权威许可证声明，`metadata.authors` 保留其作者与归属信息。

## 第三方许可证与来源声明

本仓库包含通过源码拷贝、修改或构建期 shading 纳入的第三方作品。第三方作品保留其原始许可证和版权声明，不因位于本仓库内而改为 Apache License 2.0。

### 直接拷贝或修改的 Java 源码

| 仓库内范围 | 上游项目 | 许可证 | 快照说明 | 随包许可证 |
|---|---|---|---|---|
| `com/elfmcys/ysm/lib/concentus` | [Concentus](https://github.com/lostromb/concentus) | BSD-style Opus/Concentus terms | 无法从当前源码证明确切 revision | `licenses/concentus` |
| `com/elfmcys/ysm/lib/gagravarr` | [VorbisJava / Gagravarr](https://github.com/Gagravarr/VorbisJava) | Apache-2.0 | 无法从当前源码证明确切 revision | `licenses/vorbis-java-core` |
| `com/elfmcys/ysm/geckolib3` | [GeckoLib](https://github.com/bernie-g/geckolib) | MIT | 包含本项目修改；无法从当前源码证明确切 revision | `licenses/geckolib` |
| `com/elfmcys/ysm/mclib` | [McLib](https://github.com/mchorse/mclib) | MIT | 无法从当前源码证明确切 revision | `licenses/mclib` |
| `com/elfmcys/ysm/molang` | [Mocha](https://github.com/unnamed/mocha) | MIT | 包含本项目修改；无法从当前源码证明确切 revision | `licenses/molang` |

### 随包依赖

构建会将 QuickBuffers runtime relocate 后打入最终制品，其 Apache-2.0 文本位于 `licenses/quickbuffers`。

`licenses/` 还包含随 Java 制品分发的 native 与其他第三方组件的许可证文本。

Legacy native importer 内嵌了格式兼容所需的修改版 CityHash 64-bit 实现与
ChaCha/HChaCha 参考实现。其来源和许可分别见 `licenses/cityhash` 与
`licenses/legacy-chacha`；这些声明必须与三个平台的主 native 库一同分发。

Native 项目的第三方组件、版本和许可证清单由其自身仓库维护：[THIRD_PARTY_LICENSES.md](https://github.com/YesSteveModel/YesSteveModel-Native/blob/dev/THIRD_PARTY_LICENSES.md)。

本清单是合规辅助材料，不替代各上游许可证正文。


## Unofficial Minecraft 26.3 port

This distribution is maintained by LuoMuQAQ and is not an official YSM release.
Original YSM code and attribution are retained. Port changes are identified in the
modified files and summarized in MODIFICATIONS.md. Upstream source revisions are
recorded in release/source-provenance.json; matching native sources are in native/.

Built-in model authors and licenses are listed in BUILTIN_MODELS.md and retained
in each original ysm.json manifest. This package includes 24 upstream models and
does not include third-party models supplied for local gameplay.

Native legal companions (licenses, NOTICE and PATENTS files) are included under
licenses/native-sources in the JAR. The native JNI header snapshot is distributed
under GPL-2.0-only WITH Classpath-exception-2.0; its full license text is under
licenses/jni-gpl2-classpath and the corresponding headers are in native/third-party/jni/src.
The release includes the notices for STX CStringView and MiniOgg. This software is
based in part on the work of the Independent JPEG Group.

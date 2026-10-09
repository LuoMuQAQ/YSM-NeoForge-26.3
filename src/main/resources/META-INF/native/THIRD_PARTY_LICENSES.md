# Third-Party Licenses

This file inventories the third-party software used by ysm-ng. Standard
license names use SPDX identifiers. `AND` denotes combined terms and `OR`
denotes a choice offered by the upstream project.

> **Important:** This is an informational index. It does not reproduce every
> license, notice, attribution, or patent text and is not, by itself, a
> complete compliance bundle for source or binary distribution. Release
> artifacts must be reviewed separately and accompanied by all texts and
> attributions required by the dependencies they contain.

The dependency inventory is based on [`conanfile.py`](conanfile.py) and the
pinned sources in `third-party/*/conandata.yml`.

## Direct dependencies

| Component | Pinned source | License | Notes |
| --- | --- | --- | --- |
| [Abseil](https://github.com/abseil/abseil-cpp) | 20260107.1 | Apache-2.0 |  |
| [BLAKE3](https://github.com/BLAKE3-team/BLAKE3) | 1.8.5 | CC0-1.0 OR Apache-2.0 OR [Apache-2.0 WITH LLVM-exception](https://github.com/BLAKE3-team/BLAKE3/blob/93a431c78a52d7ccf0f366f106467f5070e6075e/LICENSE_A2LLVM) |  |
| [cglm](https://github.com/recp/cglm) | v0.9.6 | MIT |  |
| [cpu_features](https://github.com/google/cpu_features) | v0.11.0 | Apache-2.0 |  |
| [Crypto++](https://github.com/weidai11/cryptopp) | CRYPTOPP_8_9_0 | BSL-1.0 AND BSD-3-Clause | Includes public-domain portions. The [cryptopp-cmake wrapper at CRYPTOPP_8_9_0](https://github.com/abdes/cryptopp-cmake) is BSD-3-Clause. |
| [FlatBuffers](https://github.com/google/flatbuffers) | v25.12.19-2026-02-06-03fffb2 | Apache-2.0 |  |
| [JNI headers](third-party/jni/src) | local snapshot | [GPL-2.0-only WITH Classpath-exception-2.0](third-party/jni/src/jni.h) | `8.0.0` is a local recipe label; the exact upstream revision is not recorded. The headers contain license notices, but the standalone GPL and Classpath Exception texts are not stored in this repository. |
| [libavif](https://github.com/AOMediaCodec/libavif) | v1.4.1 | BSD-2-Clause |  |
| [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) | 3.1.90 | [IJG](https://github.com/libjpeg-turbo/libjpeg-turbo/blob/e1dbfa7be7b7e54922020051dc77781e92739700/LICENSE.md) AND BSD-3-Clause | See the upstream [IJG notice](https://github.com/libjpeg-turbo/libjpeg-turbo/blob/e1dbfa7be7b7e54922020051dc77781e92739700/README.ijg); its binary attribution requirements apply where relevant. |
| [libspng](https://github.com/randy408/libspng) | v0.7.4 | BSD-2-Clause |  |
| [libwebp](https://github.com/webmproject/libwebp) | v1.6.0 | BSD-3-Clause | See upstream [PATENTS](https://github.com/webmproject/libwebp/blob/4fa21912338357f89e4fd51cf2368325b59e9bd9/PATENTS). |
| [libyuv](https://github.com/lemenkov/libyuv) | 9d98aae | BSD-3-Clause | See upstream [PATENTS](https://github.com/lemenkov/libyuv/blob/9d98aaefe7a5e2710aa6175d44da38892400b381/PATENTS). |
| [LZMA SDK](https://github.com/sisong/lzma) | v25.01 | [LicenseRef-LZMA-SDK-Public-Domain](https://github.com/sisong/lzma/blob/18dab99e5796bd27d1feabcec49708563df7cb98/DOC/lzma-sdk.txt) | Upstream places the LZMA SDK in the public domain; no standard SPDX identifier precisely represents that statement. |
| [magic_enum](https://github.com/Neargye/magic_enum) | v0.9.8 | MIT |  |
| [mimalloc](https://github.com/microsoft/mimalloc) | v3.5.3 | MIT |  |
| [minizip-ng](https://github.com/zlib-ng/minizip-ng) | 4.2.1 | Zlib |  |
| [optional_ref](https://github.com/gharveymn/optional_ref) | 252f1fe | MIT |  |
| [Opus](https://github.com/xiph/opus) | v1.6.1 | BSD-3-Clause |  |
| [Proxy](https://github.com/ngcpp/proxy) | 4.0.2 | MIT |  |
| [pystring](https://github.com/imageworks/pystring) | v1.1.5 | BSD-3-Clause |  |
| [yalantinglibs](https://github.com/alibaba/yalantinglibs) | 0.6.1 | Apache-2.0 | Upstream also provides a [NOTICE](https://github.com/alibaba/yalantinglibs/blob/8c1f2ea927a5a879046067a686599f825a370858/NOTICE). |
| [zlib-ng](https://github.com/zlib-ng/zlib-ng) | 2.3.3 | Zlib |  |
| [Zstandard](https://github.com/facebook/zstd) | v1.5.7 | BSD-3-Clause OR GPL-2.0-only |  |

## Transitive runtime dependencies

| Component | Pinned source | License | Pulled in by |
| --- | --- | --- | --- |
| [dav1d](https://github.com/videolan/dav1d) | 1.5.3 | BSD-2-Clause | libavif; see upstream [PATENTS](https://github.com/videolan/dav1d/blob/b546257f770768b2c88258c533da38b91a06f737/doc/PATENTS). |
| [SVT-AV1](https://gitlab.com/AOMediaCodec/SVT-AV1) | v4.1.0 | BSD-3-Clause-Clear | libavif, except on Android; see upstream [PATENTS](https://gitlab.com/AOMediaCodec/SVT-AV1/-/blob/c04f951541ad600e0d9c10836f2ab7b9bc69816d/PATENTS.md). |

## Test-only dependencies

| Component | Pinned source | License | Relationship |
| --- | --- | --- | --- |
| [Google Benchmark](https://github.com/google/benchmark) | v1.9.5 | Apache-2.0 | Direct test requirement. |
| [GoogleTest](https://github.com/google/googletest) | v1.17.0 | BSD-3-Clause | Direct test requirement. |
| [RE2](https://github.com/google/re2) | 2025-11-05 | BSD-3-Clause | Transitive requirement of GoogleTest. |

## Third-party code stored in this repository

| Component | Local file | Upstream reference | License | Notes |
| --- | --- | --- | --- | --- |
| floodyberry ChaCha/XChaCha reference | [`modules/legacy/src/format/envelope.cc`](modules/legacy/src/format/envelope.cc) | Historical YSM source snapshot; exact upstream commit not recorded | Public Domain OR MIT | The legacy importer contains a portable C++ implementation of the format-critical ChaCha/HChaCha operations and YSM state mutation, rather than the historical runtime-selected assembly library. |
| CityHash 1.1.1 historical YSM fork | [`modules/legacy/src/v3/codec/modified_city_hash.cc`](modules/legacy/src/v3/codec/modified_city_hash.cc) | Historical YSM source snapshot; exact upstream commit not recorded | MIT | Only the 64-bit format-critical implementation is retained. The historical constants and `uint128` field order are intentionally preserved. The complete license text is embedded in the local source. |
| STX `CStringView` | [`modules/core/src/c_string_view.h`](modules/core/src/c_string_view.h) | [STX CStringView](https://github.com/lamarrr/STX) | MIT | The local file identifies STX as its source; the exact imported revision is not recorded. |
| MiniOgg | [`modules/codec/include/codec/mini_ogg.h`](modules/codec/include/codec/mini_ogg.h) | Provenance not recorded | 0BSD | Copyright 2023 John Regan; the complete license text is embedded in the local header. |

## Exclusions

This inventory excludes build tools, compilers, C and C++ runtimes, Iconv,
Windows synchronization libraries, operating-system frameworks, and other
system-provided components. Their terms are determined by the build and target
environments rather than the dependency recipes in this repository.

## Maintenance

Update this file in the same change whenever a dependency, pinned source,
license, notice, patent file, or stored third-party source is added, removed,
or changed. Show recorded tags; use commits only when no tag is recorded. Link
component names to source repositories or local snapshots; do not infer an
upstream revision.

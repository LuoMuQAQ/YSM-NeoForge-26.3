#include "cpu.h"

#include <string_view>

#include "log.h"
#include "macro.h"

namespace ysm {
#ifdef YSM_X64
cpu_features::X86Features kX86Features;
#elif defined(YSM_ARM64)
cpu_features::Aarch64Features kArm64Feature;
#endif

simd::Type simd::kSupported;

void InitCpuInfo() {
#ifdef YSM_X64
    auto info = cpu_features::GetX86Info();
    kX86Features = info.features;
    // 暂不考虑 nova lake
    if (YSM_SV_LITERAL(CPU_FEATURES_VENDOR_GENUINE_INTEL) != info.vendor &&
            kX86Features.avx512f && kX86Features.avx512bw && kX86Features.avx512vl) {
        simd::kSupported = simd::Type::AVX512;
    } else if (kX86Features.avx2 && kX86Features.fma3) {
        simd::kSupported = simd::Type::AVX2;
    } else {
        simd::kSupported = simd::Type::SSE41;
    }
#elif defined(YSM_ARM64)
    kArm64Feature = cpu_features::GetAarch64Info().features;
    simd::kSupported = simd::Type::NEON;
#else
    simd::kSupported = simd::Type::None;
#endif

    YSM_LOG(INFO, "Simd type: {}",
        magic_enum::enum_name(simd::kSupported));
}

void simd::Mock(Type type) {
    kSupported = type;
}
}

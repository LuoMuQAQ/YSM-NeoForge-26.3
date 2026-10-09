#include <codec/blake3.h>
#include <codec/zstd.h>
#include <absl/strings/str_cat.h>
#include <enum.h>
#include <java/buffer.h>
#include <java/entry.h>

namespace ysm::lib::algo {
namespace {
enum class CompressOperation { kCompress = 1, kDecompress = 2 };

using Hash = BufferFixed<ysm::codec::kBlake3HashSize>;

absl::Status ValidateHash(JNIEnv_* env, jbyteArray expected,
                          const Hash& actual) {
    if (expected == nullptr) {
        return OkStatus();
    }

    Hash provided;
    YSM_RETURN_IF_ERROR(java::ReadByteArray<true>(env, expected, provided));
    if (!Cmp(actual, provided)) [[unlikely]] {
        return absl::DataLossError("zstd decoded content hash mismatch"sv);
    }
    return OkStatus();
}

absl::Status Decompress(BufferViewR input, BufferView output,
                        jint expected_size, Hash* computed_hash) {
    YSM_DECLARE_OR_RETURN(written,
                          ysm::codec::ZstdDecompress(input, output));
    if (written != static_cast<size_t>(expected_size)) [[unlikely]] {
        return absl::DataLossError(absl::StrCat(
            "zstd decoded size mismatch: expected ", expected_size,
            ", actual ", written));
    }
    if (computed_hash != nullptr) {
        ysm::codec::Blake3Hash(output, *computed_hash);
    }
    return OkStatus();
}
}

YSM_JNI_ENTRY(
    "Lcom/elfmcys/ysm/natives/Zstd;nZstd(Ljava/lang/Object;J[BIII)Ljava/lang/Object;",
    (input, input_flags, hash, output_param, out_type_value, op_value)) {
    YSM_DECLARE_OR_RETURN(out_buf_type,
                          EnumCast<java::BufferType>(out_type_value));
    YSM_DECLARE_OR_RETURN(op, EnumCast<CompressOperation>(op_value));

    if (op == CompressOperation::kDecompress) {
        Hash computed_hash;

        YSM_DECLARE_OR_RETURN(
            out_buf,
            java::BufferOutput<>::Create(env, output_param, out_buf_type));
        {
            YSM_DECLARE_OR_RETURN(
                in_buf,
                java::BufferInput<true>::Get(env, input, input_flags));
            YSM_RETURN_IF_ERROR(Decompress(
                in_buf, out_buf, output_param,
                hash == nullptr ? nullptr : &computed_hash));
        }
        YSM_RETURN_IF_ERROR(ValidateHash(env, hash, computed_hash));
        return out_buf.release();
    }

    BufferManaged out_buf;
    Hash computed_hash;
    {
        YSM_DECLARE_OR_RETURN(
            in_buf, java::BufferInput<true>::Get(env, input, input_flags));
        YSM_DECLARE_OR_RETURN(
            out_bound, ysm::codec::ZstdGetCompressMaxSize(in_buf.size()));
        out_buf.resize(out_bound);
        YSM_DECLARE_OR_RETURN(
            compressed,
            ysm::codec::ZstdCompress(in_buf, out_buf, output_param));
        out_buf.resize(compressed);

        if (hash != nullptr) {
            ysm::codec::Blake3Hash(in_buf, computed_hash);
        }
    }

    if (hash != nullptr) {
        YSM_RETURN_IF_ERROR(
            java::WriteByteArray<true>(env, hash, computed_hash));
    }
    out_buf.shrink_to_fit();
    return java::TryMoveToOutput(env, std::move(out_buf), out_buf_type);
}
}  // namespace ysm::lib::algo

#include <codec/blake3.h>
#include <enum.h>
#include <java/buffer.h>
#include <java/entry.h>

namespace ysm::lib::algo {
namespace {
enum class HashOperation { kCompare = 1, kCompute = 2 };
}

YSM_JNI_ENTRY(
"Lcom/elfmcys/ysm/natives/Blake3;nBlake3(Ljava/lang/Object;J[BI)I",
    (input, input_flags, out, op_value)) {
    YSM_DECLARE_OR_RETURN(op, EnumCast<HashOperation>(op_value));
    BufferFixed<ysm::codec::kBlake3HashSize> hash;
    {
        YSM_DECLARE_OR_RETURN(
            in_buf, java::BufferInput<true>::Get(env, input, input_flags));
        ysm::codec::Blake3Hash(in_buf, hash);
    }

    if (op == HashOperation::kCompare) {
        BufferFixed<ysm::codec::kBlake3HashSize> provided_hash;
        YSM_RETURN_IF_ERROR(
            java::ReadByteArray<true>(env, out, provided_hash));
        return Cmp(provided_hash, hash);
    }
    YSM_RETURN_IF_ERROR(java::WriteByteArray<true>(env, out, hash));
    return true;
}
}  // namespace ysm::lib::algo

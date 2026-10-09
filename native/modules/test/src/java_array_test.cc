#include <algorithm>
#include <array>
#include <bit>
#include <cstdint>
#include <list>
#include <vector>

#include <gtest/gtest.h>

#include "java/array.h"
#include "java/buffer.h"

namespace ysm::java {
namespace {
struct FakeArrayBase {
    jsize size;
    void* data;
    void (*deleter)(FakeArrayBase*);
};

template <typename T>
struct FakeArray {
    FakeArrayBase base;
    std::vector<T> values;

    explicit FakeArray(jsize size) : values(static_cast<size_t>(size)) {
        base.size = size;
        base.data = values.data();
        base.deleter = [](FakeArrayBase* array) {
            delete reinterpret_cast<FakeArray*>(array);
        };
    }
};

template <typename T, typename Array>
FakeArray<T>& AsFakeArray(Array array) {
    return *reinterpret_cast<FakeArray<T>*>(array);
}

template <typename T, typename Array>
void SetArrayRegion(Array array, jsize start, jsize size, const T* data) {
    auto& output = AsFakeArray<T>(array).values;
    std::copy_n(data, size, output.begin() + start);
}

template <typename T, typename Array>
void GetArrayRegion(Array array, jsize start, jsize size, T* data) {
    const auto& input = AsFakeArray<T>(array).values;
    std::copy_n(input.begin() + start, size, data);
}

class FakeJniEnv {
    JNINativeInterface_ functions_{};
    JNIEnv_ env_{&functions_};
    inline static size_t critical_release_count_ = 0;

    static void JNICALL DeleteLocalRef(JNIEnv_*, jobject object) {
        auto* array = reinterpret_cast<FakeArrayBase*>(object);
        array->deleter(array);
    }

    static jsize JNICALL GetArrayLength(JNIEnv_*, jarray array) {
        return reinterpret_cast<FakeArrayBase*>(array)->size;
    }

    static jshortArray JNICALL NewShortArray(JNIEnv_*, jsize size) {
        return reinterpret_cast<jshortArray>(new FakeArray<jshort>(size));
    }

    static void JNICALL SetShortArrayRegion(JNIEnv_*, jshortArray array,
                                            jsize start, jsize size,
                                            const jshort* data) {
        SetArrayRegion(array, start, size, data);
    }

    static void JNICALL GetByteArrayRegion(JNIEnv_*, jbyteArray array,
                                           jsize start, jsize size, jbyte* data) {
        GetArrayRegion(array, start, size, data);
    }

    static void JNICALL GetShortArrayRegion(JNIEnv_*, jshortArray array,
                                            jsize start, jsize size,
                                            jshort* data) {
        GetArrayRegion(array, start, size, data);
    }

    static void JNICALL SetFloatArrayRegion(JNIEnv_*, jfloatArray array,
                                            jsize start, jsize size,
                                            const jfloat* data) {
        SetArrayRegion(array, start, size, data);
    }

    static void* JNICALL GetPrimitiveArrayCritical(JNIEnv_*, jarray array,
                                                   jboolean* is_copy) {
        *is_copy = JNI_FALSE;
        return reinterpret_cast<FakeArrayBase*>(array)->data;
    }

    static void JNICALL ReleasePrimitiveArrayCritical(JNIEnv_*, jarray, void*,
                                                       jint) {
        ++critical_release_count_;
    }

   public:
    FakeJniEnv() {
        functions_.DeleteLocalRef = DeleteLocalRef;
        functions_.GetArrayLength = GetArrayLength;
        functions_.NewShortArray = NewShortArray;
        functions_.GetByteArrayRegion = GetByteArrayRegion;
        functions_.GetShortArrayRegion = GetShortArrayRegion;
        functions_.SetShortArrayRegion = SetShortArrayRegion;
        functions_.SetFloatArrayRegion = SetFloatArrayRegion;
        functions_.GetPrimitiveArrayCritical = GetPrimitiveArrayCritical;
        functions_.ReleasePrimitiveArrayCritical =
            ReleasePrimitiveArrayCritical;
    }

    JNIEnv_* get() { return &env_; }

    static void ResetCriticalReleaseCount() { critical_release_count_ = 0; }

    static size_t CriticalReleaseCount() { return critical_release_count_; }
};

TEST(JavaArrayTest, WritesSameWidthIntegralContainer) {
    FakeJniEnv env;
    FakeArray<jshort> output(3);
    const std::vector<uint16_t> values{0, 0x7fff, 0xffff};

    ASSERT_TRUE(WriteShortArray(env.get(),
                                reinterpret_cast<jshortArray>(&output), values)
                    .ok());
    EXPECT_EQ(output.values[0], 0);
    EXPECT_EQ(output.values[1], 0x7fff);
    EXPECT_EQ(std::bit_cast<uint16_t>(output.values[2]), 0xffff);
}

TEST(JavaArrayTest, ConvertsValuesInsteadOfCopyingUnrelatedBitPatterns) {
    FakeJniEnv env;
    FakeArray<jfloat> output(2);
    const std::vector<uint32_t> values{1, 2};

    ASSERT_TRUE(WriteFloatArray(env.get(),
                                reinterpret_cast<jfloatArray>(&output), values)
                    .ok());
    EXPECT_FLOAT_EQ(output.values[0], 1.0f);
    EXPECT_FLOAT_EQ(output.values[1], 2.0f);
}

TEST(JavaArrayTest, SupportsSafeTransformsAndEmptyRanges) {
    FakeJniEnv env;
    FakeArray<jshort> output(4);
    const std::array<int, 2> values{3, 4};

    ASSERT_TRUE(WriteShortArray<true>(
                    env.get(), reinterpret_cast<jshortArray>(&output), values,
                    [](int value) { return static_cast<jshort>(value * 2); }, 1)
                    .ok());
    EXPECT_EQ(output.values, (std::vector<jshort>{0, 6, 8, 0}));

    FakeArray<jshort> empty_output(0);
    const std::list<int> empty;
    EXPECT_TRUE(WriteShortArray(env.get(),
                                reinterpret_cast<jshortArray>(&empty_output),
                                empty)
                    .ok());
}

TEST(JavaArrayTest, ReadsArraySlicesIntoExistingContainers) {
    FakeJniEnv env;
    FakeArray<jshort> input(4);
    input.values = {10, 20, 30, 40};
    std::array<uint16_t, 2> direct{};

    ASSERT_TRUE(ReadShortArray(env.get(),
                               reinterpret_cast<jshortArray>(&input), direct, 1)
                    .ok());
    EXPECT_EQ(direct, (std::array<uint16_t, 2>{20, 30}));

    std::list<int> transformed(2);
    ASSERT_TRUE(ReadShortArray<true>(
                    env.get(), reinterpret_cast<jshortArray>(&input),
                    transformed, [](jshort value) { return value * 2; }, 2)
                    .ok());
    EXPECT_TRUE(std::ranges::equal(transformed, std::array{60, 80}));
}

TEST(JavaArrayTest, ReadConvertsValuesInsteadOfCopyingBitPatterns) {
    FakeJniEnv env;
    FakeArray<jfloat> input(2);
    input.values = {1.0f, 2.0f};
    std::vector<uint32_t> output(2);

    ASSERT_TRUE(ReadFloatArray(env.get(),
                               reinterpret_cast<jfloatArray>(&input), output)
                    .ok());
    EXPECT_EQ(output, (std::vector<uint32_t>{1, 2}));
}

TEST(JavaArrayTest, BufferInputSafePathUsesArrayReader) {
    FakeJniEnv env;
    FakeArray<jbyte> input(4);
    input.values = {10, 20, 30, 40};
    constexpr uint64_t offset = 1;
    constexpr uint64_t size = 2;
    const auto flags = static_cast<jlong>((offset << 32) | size);

    auto buffer = BufferInput<true, true>::Get(
        env.get(), reinterpret_cast<jobject>(&input), flags);
    ASSERT_TRUE(buffer.ok()) << buffer.status();
    ASSERT_EQ(buffer->size(), 2);
    EXPECT_EQ(buffer->data()[0], 20);
    EXPECT_EQ(buffer->data()[1], 30);
}

TEST(JavaArrayTest, RejectsOutOfBoundsArrayRanges) {
    FakeJniEnv env;
    FakeArray<jshort> output(2);
    const std::array<jshort, 2> values{1, 2};

    EXPECT_EQ(WriteShortArray(env.get(),
                              reinterpret_cast<jshortArray>(&output), values, 1)
                  .code(),
              absl::StatusCode::kInvalidArgument);
    std::array<jshort, 1> read_output{};
    EXPECT_EQ(ReadShortArray(env.get(),
                             reinterpret_cast<jshortArray>(&output), read_output,
                             -1)
                  .code(),
              absl::StatusCode::kInvalidArgument);
}

TEST(JavaArrayTest, ExistingCreationHelperUsesWritePath) {
    FakeJniEnv env;
    const std::vector<uint16_t> values{1, 0xffff};

    auto array = ShortArray(env.get(), values);
    const auto& output = AsFakeArray<jshort>(array.get()).values;
    ASSERT_EQ(output.size(), 2);
    EXPECT_EQ(output[0], 1);
    EXPECT_EQ(std::bit_cast<uint16_t>(output[1]), 0xffff);
}

TEST(JavaArrayTest, CriticalArrayMovePreservesRegionOwnership) {
    FakeJniEnv env;
    FakeJniEnv::ResetCriticalReleaseCount();
    FakeArray<jshort> input(3);
    input.values = {10, 20, 30};

    auto result = CriticalShortArray<true>::Get(
        env.get(), reinterpret_cast<jshortArray>(&input), 1, 1);
    ASSERT_TRUE(result.ok()) << result.status();
    auto array = std::move(*result);
    EXPECT_EQ(FakeJniEnv::CriticalReleaseCount(), 0);
    ASSERT_EQ(array.size(), 1);
    EXPECT_EQ(array.data()[0], 20);
    array.clear();
    EXPECT_EQ(FakeJniEnv::CriticalReleaseCount(), 1);

    EXPECT_EQ(CriticalShortArray<true>::Get(
                  env.get(), reinterpret_cast<jshortArray>(&input), 4)
                  .status()
                  .code(),
              absl::StatusCode::kInvalidArgument);
}

TEST(JavaArrayTest, PrimitiveMacroIncludesBooleanCriticalArray) {
    FakeJniEnv env;
    FakeArray<jboolean> input(1);
    input.values[0] = JNI_TRUE;

    auto result = CriticalBooleanArray<true>::Get(
        env.get(), reinterpret_cast<jbooleanArray>(&input));
    ASSERT_TRUE(result.ok()) << result.status();
    EXPECT_EQ(result->data()[0], JNI_TRUE);
}
}  // namespace
}  // namespace ysm::java

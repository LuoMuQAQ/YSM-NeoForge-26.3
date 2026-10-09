#include <gtest/gtest.h>
#include <jni.h>

#include <algorithm>
#include <stdexcept>
#include <string>
#include <type_traits>

#include <java/entry.h>

namespace ysm::java {
namespace {
absl::Status BooleanStatus(JNIEnv_*, jint value) {
    if (value > 0) {
        return OkStatus();
    }
    return absl::InvalidArgumentError("test boolean failure");
}

bool g_void_entry_called = false;

absl::Status VoidStatus(JNIEnv_*) {
    g_void_entry_called = true;
    return OkStatus();
}

absl::StatusOr<jint> IntResult(JNIEnv_*, jint value) {
    if (value >= 0) {
        return value;
    }
    return absl::InvalidArgumentError("test int failure");
}

absl::StatusOr<jlong> LongResult(JNIEnv_*, jlong value) {
    if (value >= 0) {
        return value;
    }
    return absl::InvalidArgumentError("test long failure");
}

absl::StatusOr<jint> ThrowingResult(JNIEnv_*) {
    throw std::runtime_error("test exception");
}

absl::Status ScopedLog(JNIEnv_*) {
    YSM_LOG(INFO, "scoped JNI log");
    return OkStatus();
}
}  // namespace

YSM_JNI_ENTRY("Ltest/AutoEntry;nRegistered(I)Z", (value)) {
    return BooleanStatus(env, value);
}

YSM_JNI_ENTRY("Ltest/AutoEntry;nZero()V", ()) {
    return OkStatus();
}

namespace {
using BooleanThunk = internal::EntryThunk<
    &BooleanStatus, "Ltest/Entry;nBooleanStatus(I)Z">;
using VoidThunk =
    internal::EntryThunk<&VoidStatus, "Ltest/Entry;nVoidStatus()V">;
using IntThunk =
    internal::EntryThunk<&IntResult, "Ltest/Entry;nIntResult(I)I">;
using LongThunk = internal::EntryThunk<
    &LongResult, "Ltest/Entry;nLongResult(J)J", jlong{-1}>;
using ThrowingThunk = internal::EntryThunk<
    &ThrowingResult, "Ltest/Entry;nThrowingResult()I">;
using ScopedLogThunk = internal::EntryThunk<
    &ScopedLog, "Ltest/image/Image$Native;nProbe()V">;
using AutoRegistrar = EntryRegistrar<
    &BooleanStatus, "Ltest/AutoEntry;nRegistered(I)Z">;

using ProbeDescriptor = internal::EntryDescriptor<
    "Lcom/example/image/Image$Native;nProbe(I)J">;
using ComplexArguments = internal::EntryArgumentListT<
    "Ltest/Entry;nTypes(Ljava/lang/String;[I[[Ljava/lang/Object;[B)V">;

static_assert(internal::IsEntryDescriptor<
              "Ltest/Entry;nValid(Ljava/lang/Object;)V">());
static_assert(!internal::IsEntryDescriptor<"test/Entry;invalid()V">());
static_assert(internal::MethodStartsWithN<"Ltest/Entry;nValid()V">());
static_assert(!internal::MethodStartsWithN<"Ltest/Entry;invalid()V">());
static_assert(ProbeDescriptor::kClassName.View() ==
              "com/example/image/Image$Native");
static_assert(ProbeDescriptor::kMethodName.View() == "nProbe");
static_assert(ProbeDescriptor::kSignature.View() == "(I)J");
static_assert(ProbeDescriptor::kReturnDescriptor.View() == "J");
static_assert(ProbeDescriptor::kSimpleClassName.View() == "Image$Native");
static_assert(ProbeDescriptor::kLoggingScopeName.View() ==
              "Image$Native.nProbe");
static_assert(internal::ReturnTypeMatchesDescriptor<
              jobjectArray,
              "Ltest/Entry;nObjects()[Ljava/lang/Object;">());
static_assert(!internal::ReturnTypeMatchesDescriptor<
              jlong, "Ltest/Entry;nWrong()I">());
static_assert(internal::EntrySignature<
                  "Ltest/Entry;nTypes(Ljava/lang/String;[I[[Ljava/lang/Object;[B)V">::
                  kArgumentCount == 4);
static_assert(std::is_same_v<
              ComplexArguments,
              internal::EntryArgumentList<jstring, jintArray, jobjectArray,
                                          jbyteArray>>);
static_assert(std::is_same_v<
              internal::EntryReturnT<
                  "Ltest/Entry;nStrings()[Ljava/lang/String;">,
              jobjectArray>);
static_assert(std::is_same_v<
              internal::EntryImplementationPointerT<
                  "Ltest/Entry;nValue(Ljava/lang/String;[I)J">,
              absl::StatusOr<jlong> (*)(JNIEnv_*, jstring, jintArray)>);
static_assert(std::is_same_v<
              internal::EntryImplementationPointerT<
                  "Ltest/Entry;nSuccess(I)Z">,
              absl::Status (*)(JNIEnv_*, jint)>);

static_assert(std::is_same_v<
              decltype(&BooleanThunk::Invoke),
              jboolean(JNICALL*)(JNIEnv_*, jclass, jint)>);
static_assert(std::is_same_v<decltype(&VoidThunk::Invoke),
                             void(JNICALL*)(JNIEnv_*, jclass)>);
static_assert(std::is_nothrow_default_constructible_v<AutoRegistrar>);
static_assert(std::is_trivially_destructible_v<AutoRegistrar>);
static_assert(!std::is_copy_constructible_v<AutoRegistrar>);
static_assert(!std::is_move_constructible_v<AutoRegistrar>);

TEST(JavaEntryTest, StaticRegistrarLinksEntryMetadataAndThunk) {
    auto entries_or =
        internal::CollectAndValidateEntries(internal::RegisteredEntries());
    ASSERT_TRUE(entries_or.ok()) << entries_or.status();

    const auto& entries = entries_or.value();
    auto entry = std::find_if(entries.begin(), entries.end(), [](auto* item) {
        return item->class_name == "test/AutoEntry" &&
               item->method_name == "nRegistered" &&
               item->signature == "(I)Z";
    });
    ASSERT_NE(entry, entries.end());
    using RegisteredFunction =
        jboolean(JNICALL*)(JNIEnv_*, jclass, jint);
    auto function = reinterpret_cast<RegisteredFunction>((*entry)->func);
    EXPECT_EQ(function(nullptr, nullptr, 1), JNI_TRUE);
    EXPECT_EQ(function(nullptr, nullptr, 0), JNI_FALSE);
}

TEST(JavaEntryTest, CollectRejectsEmptyRegistry) {
    auto entries_or = internal::CollectAndValidateEntries(nullptr);
    EXPECT_EQ(entries_or.status().code(),
              absl::StatusCode::kFailedPrecondition);
}

TEST(JavaEntryTest, CollectSortsByClassMethodAndSignature) {
    internal::EntryNode third{{"test/B", "nThird", "()V", nullptr}};
    internal::EntryNode overload_long{
        {"test/A", "nOverload", "(J)V", nullptr}, &third};
    internal::EntryNode first{{"test/A", "nFirst", "()V", nullptr},
                              &overload_long};
    internal::EntryNode overload_int{
        {"test/A", "nOverload", "(I)V", nullptr}, &first};

    auto entries_or =
        internal::CollectAndValidateEntries(&overload_int);
    ASSERT_TRUE(entries_or.ok()) << entries_or.status();

    const auto& entries = entries_or.value();
    ASSERT_EQ(entries.size(), 4);
    EXPECT_EQ(entries[0]->class_name, "test/A");
    EXPECT_EQ(entries[0]->method_name, "nFirst");
    EXPECT_EQ(entries[1]->signature, "(I)V");
    EXPECT_EQ(entries[2]->signature, "(J)V");
    EXPECT_EQ(entries[3]->class_name, "test/B");
}

TEST(JavaEntryTest, CollectRejectsExactDuplicate) {
    internal::EntryNode duplicate{
        {"test/A", "nSame", "(I)V", nullptr}};
    internal::EntryNode original{
        {"test/A", "nSame", "(I)V", nullptr}, &duplicate};

    auto entries_or = internal::CollectAndValidateEntries(&original);
    EXPECT_EQ(entries_or.status().code(),
              absl::StatusCode::kAlreadyExists);
}

TEST(JavaEntryTest, RegistryCanOnlyBeginBindingOnce) {
    internal::EntryNode node{{"test/A", "nEntry", "()V", nullptr}};
    internal::EntryRegistry registry;
    registry.Link(&node);

    EXPECT_EQ(registry.entries(), &node);
    EXPECT_TRUE(registry.BeginBinding().ok());
    EXPECT_EQ(registry.phase(), internal::EntryRegistryPhase::kBinding);
    EXPECT_EQ(registry.BeginBinding().code(),
              absl::StatusCode::kFailedPrecondition);
    registry.FinishBinding(true);
    EXPECT_EQ(registry.phase(), internal::EntryRegistryPhase::kBound);
    EXPECT_EQ(registry.BeginBinding().code(),
              absl::StatusCode::kFailedPrecondition);
}

TEST(JavaEntryTest, RegistryRecordsBindingFailure) {
    internal::EntryRegistry registry;
    ASSERT_TRUE(registry.BeginBinding().ok());
    registry.FinishBinding(false);
    EXPECT_EQ(registry.phase(), internal::EntryRegistryPhase::kFailed);
}

TEST(JavaEntryTest, StatusUsesBooleanDescriptorForSuccessFlag) {
#if GTEST_HAS_STREAM_REDIRECTION
    testing::internal::CaptureStdout();
#endif
    EXPECT_EQ(BooleanThunk::Invoke(nullptr, nullptr, 1), JNI_TRUE);
    EXPECT_EQ(BooleanThunk::Invoke(nullptr, nullptr, 0), JNI_FALSE);
#if GTEST_HAS_STREAM_REDIRECTION
    auto output = testing::internal::GetCapturedStdout();
    EXPECT_NE(output.find("Entry.nBooleanStatus]"), std::string::npos);
#endif
}

TEST(JavaEntryTest, StatusUsesVoidDescriptor) {
    g_void_entry_called = false;

    VoidThunk::Invoke(nullptr, nullptr);

    EXPECT_TRUE(g_void_entry_called);
}

TEST(JavaEntryTest, StatusOrReturnsValueAndDefaultFallback) {
    EXPECT_EQ(IntThunk::Invoke(nullptr, nullptr, 42), 42);
    EXPECT_EQ(IntThunk::Invoke(nullptr, nullptr, -1), 0);
}

TEST(JavaEntryTest, StatusOrSupportsCustomFallback) {
    EXPECT_EQ(LongThunk::Invoke(nullptr, nullptr, 42), 42);
    EXPECT_EQ(LongThunk::Invoke(nullptr, nullptr, -1), -1);
}

TEST(JavaEntryTest, ConvertsCppExceptionToFallback) {
#if GTEST_HAS_STREAM_REDIRECTION
    testing::internal::CaptureStdout();
#endif
    EXPECT_EQ(ThrowingThunk::Invoke(nullptr, nullptr), 0);
#if GTEST_HAS_STREAM_REDIRECTION
    auto output = testing::internal::GetCapturedStdout();
    EXPECT_NE(output.find("nThrowingResult"), std::string::npos);
    EXPECT_EQ(output.find("Entry.nThrowingResult/"), std::string::npos);
#endif
}

TEST(JavaEntryTest, AddsSimpleClassAndNativeMethodLoggingScope) {
#if GTEST_HAS_STREAM_REDIRECTION
    testing::internal::CaptureStdout();
#endif

    ScopedLogThunk::Invoke(nullptr, nullptr);
    YSM_LOG(INFO, "unscoped JNI log");

#if GTEST_HAS_STREAM_REDIRECTION
    auto output = testing::internal::GetCapturedStdout();
    constexpr std::string_view kScope = "Image$Native.nProbe]";
    auto scope_pos = output.find(kScope);
    ASSERT_NE(scope_pos, std::string::npos);
    EXPECT_EQ(output.find(kScope, scope_pos + 1), std::string::npos);
    EXPECT_NE(output.find("unscoped JNI log"), std::string::npos);
#endif
}
}  // namespace
}  // namespace ysm::java

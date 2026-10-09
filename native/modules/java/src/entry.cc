#include "java/entry.h"

#include <algorithm>
#include <format>
#include <string_view>
#include <tuple>
#include <vector>

namespace ysm::java {
namespace {
constinit internal::EntryRegistry g_registry;

auto EntryKey(const internal::EntryRegistration* entry) noexcept {
    return std::tuple{std::string_view{entry->class_name},
                      std::string_view{entry->method_name},
                      std::string_view{entry->signature}};
}

struct ClassBinding {
    CStringView class_name;
    jclass clazz;
    std::vector<JNINativeMethod> methods;
};

class BindingAttempt final {
   public:
    explicit BindingAttempt(internal::EntryRegistry* registry) noexcept
        : registry_(registry) {}

    BindingAttempt(const BindingAttempt&) = delete;
    BindingAttempt& operator=(const BindingAttempt&) = delete;

    ~BindingAttempt() {
        if (registry_ != nullptr) {
            registry_->FinishBinding(false);
        }
    }

    void Succeed() noexcept {
        registry_->FinishBinding(true);
        registry_ = nullptr;
    }

   private:
    internal::EntryRegistry* registry_;
};
}  // namespace

void internal::EntryRegistry::Link(EntryNode* node) noexcept {
    if (phase_ != EntryRegistryPhase::kCollecting || node == nullptr) {
        std::terminate();
    }
    node->next = head_;
    head_ = node;
}

absl::Status internal::EntryRegistry::BeginBinding() {
    if (phase_ != EntryRegistryPhase::kCollecting) {
        return absl::FailedPreconditionError(
            "JNI entry registry can only be bound once");
    }
    phase_ = EntryRegistryPhase::kBinding;
    return OkStatus();
}

void internal::EntryRegistry::FinishBinding(bool success) noexcept {
    if (phase_ != EntryRegistryPhase::kBinding) {
        std::terminate();
    }
    phase_ = success ? EntryRegistryPhase::kBound
                     : EntryRegistryPhase::kFailed;
}

void internal::LinkEntry(EntryNode* node) noexcept {
    g_registry.Link(node);
}

const internal::EntryNode* internal::RegisteredEntries() noexcept {
    return g_registry.entries();
}

absl::StatusOr<std::vector<const internal::EntryRegistration*>>
internal::CollectAndValidateEntries(const EntryNode* head) {
    std::vector<const EntryRegistration*> entries;
    for (auto* node = head; node != nullptr; node = node->next) {
        entries.emplace_back(&node->registration);
    }
    if (entries.empty()) {
        return absl::FailedPreconditionError(
            "No JNI entries were registered");
    }

    std::sort(entries.begin(), entries.end(), [](const auto* lhs,
                                                  const auto* rhs) {
        return EntryKey(lhs) < EntryKey(rhs);
    });
    for (std::size_t i = 1; i < entries.size(); ++i) {
        if (EntryKey(entries[i - 1]) == EntryKey(entries[i])) {
            const auto* entry = entries[i];
            return absl::AlreadyExistsError(std::format(
                "Duplicate JNI entry: L{};{}{}", entry->class_name,
                entry->method_name, entry->signature));
        }
    }
    return entries;
}

absl::Status BindEntry(JNIEnv_* env) {
    if (env == nullptr) {
        return absl::InvalidArgumentError("JNI environment is null");
    }
    auto begin_status = g_registry.BeginBinding();
    if (!begin_status.ok()) {
        return begin_status;
    }
    BindingAttempt binding_attempt(&g_registry);

    auto entries_or = internal::CollectAndValidateEntries(
        g_registry.entries());
    if (!entries_or.ok()) {
        return entries_or.status();
    }
    const auto& entries = entries_or.value();

    std::vector<ClassBinding> classes;
    for (std::size_t begin = 0; begin < entries.size();) {
        const auto class_name = entries[begin]->class_name;
        std::size_t end = begin + 1;
        while (end < entries.size() &&
               entries[end]->class_name ==
                   std::string_view{class_name}) {
            ++end;
        }

        auto clazz_or = FindClass(env, class_name);
        if (!clazz_or.ok()) {
            return clazz_or.status();
        }

        ClassBinding binding{class_name, clazz_or.value(), {}};
        binding.methods.reserve(end - begin);
        for (std::size_t i = begin; i < end; ++i) {
            const auto* entry = entries[i];
            binding.methods.emplace_back(
                const_cast<char*>(entry->method_name.c_str()),
                const_cast<char*>(entry->signature.c_str()), entry->func);
        }
        classes.emplace_back(std::move(binding));
        begin = end;
    }

    for (auto& binding : classes) {
        if (env->RegisterNatives(
                binding.clazz, binding.methods.data(),
                static_cast<jint>(binding.methods.size())) != JNI_OK) {
            return absl::InternalError(std::format(
                "Failed to register native methods for class {}",
                binding.class_name));
        }
    }

    binding_attempt.Succeed();
    return OkStatus();
}
}  // namespace ysm::java

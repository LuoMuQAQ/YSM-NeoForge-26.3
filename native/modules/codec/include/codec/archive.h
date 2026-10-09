#pragma once

#include "hierarchy/tree.h"

#include "buffer.h"
#include "err.h"
#include "fs.h"

namespace ysm::codec {
template <typename ArchiveType, typename EntryType = ArchiveType::EntryType>
class Archive final : NonCopyable {
    using TreeType = hierarchy::Tree<EntryType>;
    using NodeType = TreeType::Node;

    ArchiveType archive_;
    std::vector<EntryType> entries_;
    TreeType tree_;
    NodeType& root_;

   public:
    using EntryList = decltype(std::views::keys(root_.values));

    static constexpr std::string_view kRoot{};

    explicit Archive(BufferViewR archive_buffer)
        : archive_(archive_buffer), root_(BuildTree()) {}

    explicit Archive(const fs::path& path)
        : archive_(path), root_(BuildTree()) {}

    absl::Status Ok() const { return archive_.Ok(); }

    auto Files(std::string_view path = kRoot) const {
        static decltype(root_.values) empty;
        if (path.empty()) {
            return std::views::keys(root_.values);
        }
        if (auto node = root_.GetChild(path)) {
            return std::views::keys(node.value().values);
        }
        return std::views::keys(empty);
    }

    auto Directories(std::string_view path = kRoot) const {
        static decltype(root_.children) empty;
        if (path.empty()) {
            return std::views::keys(root_.children);
        }
        if (auto node = root_.GetChild(path)) {
            return std::views::keys(node.value().children);
        }
        return std::views::keys(empty);
    }

    gch::optional_ref<const EntryType> operator[](
        std::string_view file_name) const {
        return root_[file_name];
    }

    absl::Status Extract(const EntryType& entry, BufferManaged& buffer) {
        return archive_.Extract(entry, buffer);
    }

    absl::Status Extract(std::string_view file_name, BufferManaged& buffer) {
        if (auto entry = root_[file_name]) {
            buffer.resize(entry->Size());
            return archive_.Extract(entry, buffer);
        }
        return absl::NotFoundError("File not found");
    }

   private:
    NodeType& BuildTree() {
        [[maybe_unused]] auto visit_status = archive_.Visit(
            [&](auto&& entry) { entries_.emplace_back(std::move(entry)); });
        for (auto& entry : entries_) {
            tree_.emplace(entry.FullName(), entry);
        }
        auto root = &tree_.root();
        while (root->values.empty() && root->children.size() == 1) {
            root = &root->children.begin()->second;
        }
        return *root;
    }
};
}  // namespace ysm::codec

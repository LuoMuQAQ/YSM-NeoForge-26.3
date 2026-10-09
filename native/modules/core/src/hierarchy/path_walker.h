#pragma once

#include <cstdint>
#include <string_view>

namespace ysm::hierarchy {
struct PathWalker {
    class Iterator;
    const std::string_view path;

    explicit PathWalker(std::string_view path) : path(ProcessFilePath(path)) {}

    Iterator begin() const noexcept;
    static Iterator& end() noexcept;

   private:
    static std::string_view ProcessFilePath(std::string_view path) {
        while (path.starts_with('/')) {
            path = path.substr(1);
        }
        while (path.ends_with('/')) {
            path = path.substr(0, path.size() - 1);
        }
        return path;
    }
};

class PathWalker::Iterator {
    constexpr static std::string_view kEmpty = "";
    static Iterator end_;

    std::string_view path_;
    uint32_t offset_;
    uint32_t length_;

    static uint32_t FindFirst(std::string_view path) {
        if (auto splitter = path.find_first_of('/');
            splitter != std::string_view::npos) {
            return static_cast<uint32_t>(splitter);
        }
        return static_cast<uint32_t>(path.size());
    }

    Iterator(std::string_view path)
        : path_(path != kEmpty ? path : kEmpty),
          offset_(0),
          length_(FindFirst(path)) {}

    constexpr Iterator() : path_(kEmpty), offset_(0), length_(0) {}

    friend struct PathWalker;

   public:
    bool operator!=(const Iterator& other) const {
        return path_.data() != other.path_.data();
    }

    bool operator==(const Iterator& other) const {
        return path_.data() == other.path_.data();
    }

    Iterator& operator++() {
        auto cur = offset_ + length_;
        if (cur != path_.size()) [[likely]] {
            offset_ = cur + 1;
            if (auto splitter = path_.substr(offset_).find_first_of('/');
                splitter != std::string_view::npos) {
                length_ = static_cast<uint32_t>(splitter);
            } else {
                length_ = static_cast<uint32_t>(path_.size() - offset_);
            }
        } else {
            path_ = kEmpty;
            offset_ = 0;
            length_ = 0;
        }
        return *this;
    }

    std::string_view operator*() const noexcept {
        return path_.substr(offset_, length_);
    }

    std::string_view hierarchy() const noexcept {
        auto size = offset_ + length_;
        if (size < path_.size()) {
            return path_.substr(0, size + 1);
        } else {
            return path_.substr(0, size);
        }
    }
};

inline PathWalker::Iterator PathWalker::Iterator::end_ = Iterator();

inline PathWalker::Iterator PathWalker::begin() const noexcept {
    return {path};
}

inline PathWalker::Iterator& PathWalker::end() noexcept {
    return Iterator::end_;
}
}  // namespace ysm::hierarchy

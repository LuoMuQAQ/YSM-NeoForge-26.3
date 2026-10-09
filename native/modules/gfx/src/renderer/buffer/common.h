#pragma once

#include "non_copyable.h"
#include "quad_ref.h"
#include "gfx/renderer/render.h"

namespace ysm::gfx::renderer::buffer {
    template <typename VertexType>
    class CommonVertexWriter : NonCopyable {
        const std::span<VertexType> vertices_;

        std::span<VertexType, 4> current_quad_{static_cast<VertexType*>(nullptr), 4};
        uint32_t vertex_ptr_ = 0;

    public:
        YSM_INLINE explicit CommonVertexWriter(const std::span<VertexType> &vertices) noexcept :
                vertices_(vertices) {
        }

        YSM_INLINE void BeginQuad() {
            current_quad_ = vertices_.subspan(vertex_ptr_, 4).template subspan<0, 4>();
        }

        YSM_INLINE VertexType& QuadVertex(size_t index) const {
            return current_quad_[index];
        }

        YSM_INLINE void EndQuad() noexcept {
            vertex_ptr_ += 4;
        }

        ~CommonVertexWriter() {
            if (vertex_ptr_ < vertices_.size()) {
                auto filled = vertices_.subspan(vertex_ptr_);
                std::memset(filled.data(), 0, filled.size() * sizeof(VertexType));
            }
        }
    };

    template <typename VertexTypeIn, bool kGui>
    class CommonBuffer {
        std::span<VertexTypeIn> vertices_;

    public:
        using VertexType = VertexTypeIn;
        using WriterType = CommonVertexWriter<VertexType>;

        constexpr static size_t kVertexSize = sizeof(VertexTypeIn);
        constexpr static bool kTranslucent = false;
        constexpr static bool kIris = false;
        constexpr static bool kPosOnly = false;

        YSM_INLINE CommonBuffer(std::span<VertexType> vertices, auto&&) : vertices_(vertices) {}

        [[nodiscard]] YSM_INLINE auto CreateVertexWriter(uint32_t vertexOffset, uint32_t expectedVertexCount) const {
            return CommonVertexWriter<VertexType>{vertices_.subspan(vertexOffset, expectedVertexCount)};
        }

        auto CreateVertexWriter(const std::span<VertexType>& vertices) const noexcept {
            return CommonVertexWriter<VertexType>{vertices};
        }

        YSM_INLINE void WriteSortedQuads(const std::span<VertexType> &vertices, const std::span<QuadRef>& indices, uint32_t vertex_offset) const {
            auto translucentVertices = vertices_.subspan(vertex_offset);
            for (auto i = 0; i < indices.size(); ++i) {
                std::memcpy(&translucentVertices[i * 4], &vertices[indices[i].vertex_offset], kVertexSize * 4);
            }
        }
    };
}

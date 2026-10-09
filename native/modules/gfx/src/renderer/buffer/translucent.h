#pragma once

#include <algorithm>

#include "buffer_managed.h"
#include "non_copyable.h"
#include "quad_ref.h"
#include "gfx/renderer/render.h"

namespace ysm::gfx::renderer::buffer {
    template <typename UnderlyingBuffer>
    class TranslucentProxyBuffer;

    template <typename UnderlyingBuffer>
    class TranslucentVertexWriter : NonCopyable {
        UnderlyingBuffer::WriterType underlying_writer_;
        const std::span<QuadRef> quad_indices_;
        const uint32_t global_vertex_offset_;
        uint32_t quad_ptr_ = 0;

    public:
        explicit TranslucentVertexWriter(
                const auto& underlying_buffer,
                std::span<typename UnderlyingBuffer::VertexType> vertices,
                const std::span<QuadRef> &quad_indices,
                uint32_t vertex_offset)
                : underlying_writer_(underlying_buffer.CreateVertexWriter(vertices))
                , quad_indices_(quad_indices)
                , global_vertex_offset_(vertex_offset) {
        }

        void BeginQuad() {
            underlying_writer_.BeginQuad();
        }

        void SetQuadDepth(float depth) const {
            quad_indices_[quad_ptr_] = {
                global_vertex_offset_ + quad_ptr_ * 4,
                NdcDepthSortKey(depth)
            };
        }

        auto&& QuadVertex(size_t index) const {
            return underlying_writer_.QuadVertex(index);
        }

        void EndQuad() {
            ++quad_ptr_;
            underlying_writer_.EndQuad();
        }

        ~TranslucentVertexWriter() {
            if (quad_ptr_ < quad_indices_.size()) {
                auto fillIndices = quad_indices_.subspan(quad_ptr_);
                auto globalVertexOffset = global_vertex_offset_ + quad_ptr_ * 4;
                for (auto i = 0; i < fillIndices.size(); ++i) {
                    fillIndices[i] = {
                        globalVertexOffset + i * 4,
                        kInvalidDepthSortKey
                    };
                }
            }
        }
    };

    namespace internal {
        static BufferManaged g_vertex_buffer;
        static std::vector<QuadRef> g_vertex_indices;
    }

    template <typename UnderlyingType>
    class TranslucentProxyBuffer {
        using VertexType = UnderlyingType::VertexType;
        constexpr static size_t kVertexSize = sizeof(VertexType);

        const UnderlyingType& underlying_buffer_;
        const uint32_t translucent_vertex_offset_;
        std::span<VertexType> vertices_;
        std::span<QuadRef> indices_;

    public:
        constexpr static bool kTranslucent = true;
        constexpr static bool kIris = UnderlyingType::kIris;
        constexpr static bool kPosOnly = UnderlyingType::kPosOnly;

        TranslucentProxyBuffer(UnderlyingType &underlying_buffer, uint32_t translucent_vertex_count, uint32_t translucent_vertex_offset)
                : underlying_buffer_(underlying_buffer)
                , translucent_vertex_offset_(translucent_vertex_offset) {
            auto quad_count = translucent_vertex_count / 4;
            internal::g_vertex_buffer.resize(translucent_vertex_count * sizeof(VertexType));
            internal::g_vertex_indices.resize(quad_count);

            vertices_ = {reinterpret_cast<VertexType *>(internal::g_vertex_buffer.data()), translucent_vertex_count};
            indices_ = {internal::g_vertex_indices.begin(), quad_count}; // NOLINT(*-dangling-handle)
        }

        [[nodiscard]] auto CreateVertexWriter(
                uint32_t vertex_offset, uint32_t expected_vertex_count) const {
            return TranslucentVertexWriter<UnderlyingType>{
                underlying_buffer_,
                vertices_.subspan(vertex_offset, expected_vertex_count),
                indices_.subspan(vertex_offset / 4, expected_vertex_count / 4),
                vertex_offset
            };
        }

        void Flush(bool sort = true) {
            if (sort) [[likely]] {
                std::span span(reinterpret_cast<uint64_t *>(indices_.data()), indices_.size());
                // 目前没有任何模型含大量半透明面
                std::ranges::sort(span.begin(), span.end());
            }
            underlying_buffer_.WriteSortedQuads(vertices_, indices_, translucent_vertex_offset_);
        }
    };
}

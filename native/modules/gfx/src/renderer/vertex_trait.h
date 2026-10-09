#pragma once

#include "gfx/renderer/vertex/kind.h"

#include "buffer/common.h"
#include "buffer/iris.h"

#include "vertex/fallback.h"
#include "vertex/iris_54.h"
#include "vertex/iris_55.h"
#include "vertex/iris_56.h"
#include "vertex/iris_56_ar.h"
#include "vertex/vanilla.h"

namespace ysm::gfx::renderer {
namespace internal {
template <VertexKind kKind, simd::Type, RenderContext>
struct VertexKindTraits {
    static_assert(static_cast<int>(kKind) == -1, "Unsupported vertex type");
};

template <simd::Type kSimdType, RenderContext kRenderContext>
struct VertexKindTraits<VertexKind::kVanilla, kSimdType, kRenderContext> {
    using VertexType = vertex::VanillaVertex;
    using BufferType = buffer::CommonBuffer<VertexType, kRenderContext == RenderContext::kGui>;
};

template <simd::Type kSimdType, RenderContext kRenderContext>
struct VertexKindTraits<VertexKind::kFallback, kSimdType, kRenderContext> {
    using VertexType = vertex::FallbackVertex;
    using BufferType = buffer::CommonBuffer<VertexType, kRenderContext == RenderContext::kGui>;
};

template <simd::Type kSimdType, RenderContext kRenderContext>
struct VertexKindTraits<VertexKind::kIris56, kSimdType, kRenderContext> {
    using VertexType = vertex::Iris56Vertex;
    using BufferType = buffer::IrisBuffer<VertexType, kSimdType, kRenderContext == RenderContext::kIrisShadow>;
};

template <simd::Type kSimdType, RenderContext kRenderContext>
struct VertexKindTraits<VertexKind::kIris56Ar, kSimdType, kRenderContext> {
    using VertexType = vertex::Iris56ArVertex;
    using BufferType = buffer::IrisBuffer<VertexType, kSimdType, kRenderContext == RenderContext::kIrisShadow>;
};

template <simd::Type kSimdType, RenderContext kRenderContext>
struct VertexKindTraits<VertexKind::kIris55, kSimdType, kRenderContext> {
    using VertexType = vertex::Iris55Vertex;
    using BufferType = buffer::IrisBuffer<VertexType, kSimdType, kRenderContext == RenderContext::kIrisShadow>;
};

template <simd::Type kSimdType, RenderContext kRenderContext>
struct VertexKindTraits<VertexKind::kIris54, kSimdType, kRenderContext> {
    using VertexType = vertex::Iris54Vertex;
    using BufferType = buffer::IrisBuffer<VertexType, kSimdType, kRenderContext == RenderContext::kIrisShadow>;
};
}

template <VertexKind kKind, simd::Type kSimdType, RenderContext kRenderContext>
using VertexType = internal::VertexKindTraits<kKind, kSimdType, kRenderContext>::VertexType;

template <VertexKind kKind, simd::Type kSimdType, RenderContext kRenderContext>
using VertexBufferType = internal::VertexKindTraits<kKind, kSimdType, kRenderContext>::BufferType;
}
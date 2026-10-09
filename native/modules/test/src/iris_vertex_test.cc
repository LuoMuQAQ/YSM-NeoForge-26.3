#include <gtest/gtest.h>

#include "renderer/vertex/iris_54.h"
#include "renderer/vertex/iris_55.h"
#include "renderer/vertex/iris_56.h"
#include "renderer/vertex/iris_56_ar.h"

namespace ysm::test {

TEST(IrisVertexTest, SetTangent) {
    gfx::renderer::vertex::Iris54Vertex iris54;
    gfx::renderer::vertex::Iris55Vertex iris55;
    gfx::renderer::vertex::Iris56Vertex iris56;
    gfx::renderer::vertex::Iris56ArVertex iris56_ar;
    iris54.SetIrisTangent(1);
    iris55.SetIrisTangent(2);
    iris56.SetIrisTangent(3);
    iris56_ar.SetIrisTangent(4);
    EXPECT_EQ(iris54.tangent, 1);
    EXPECT_EQ(iris55.tangent, 2);
    EXPECT_EQ(iris56.tangent, 3);
    EXPECT_EQ(iris56_ar.tangent, 4);
}

}  // namespace ysm::test

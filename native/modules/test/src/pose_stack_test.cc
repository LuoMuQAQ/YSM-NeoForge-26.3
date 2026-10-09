#include <numbers>

#include <gtest/gtest.h>

#include "cpu.h"
#include "gfx/math/pose_stack.h"

namespace ysm::test {
TEST(PoseStackTest, MaintainsDirectionStateAcrossHierarchy) {
    gfx::math::PoseStack stack;
    stack.Reserve(3);

    simd::GenericTag tag;

    stack.Translate(tag, 1.0f, 2.0f, 3.0f);
    stack.RotateZYX(tag, 0.0f, 0.0f, std::numbers::pi_v<float> * 0.5f);
    stack.Scale(tag, -2.0f, 2.0f, 2.0f);
    const auto& parent = stack.Last();
    EXPECT_TRUE(parent.uniform_scale);
    EXPECT_FLOAT_EQ(parent.tangent_orientation, -1.0f);
    EXPECT_FLOAT_EQ(parent.normal_scale, 2.0f);
    EXPECT_NEAR(parent.pose[0][1], -2.0f, 0.00001f);
    EXPECT_NEAR(parent.normal[0][1], -1.0f, 0.00001f);

    stack.PushPose();
    stack.Scale(tag, 1.0f, 2.0f, 1.0f);
    stack.Scale(tag, -1.0f, -1.0f, 1.0f);
    const auto& child = stack.Last();
    EXPECT_FALSE(child.uniform_scale);
    EXPECT_FLOAT_EQ(child.tangent_orientation, -1.0f);
    EXPECT_FLOAT_EQ(child.normal_scale, 2.0f);

    stack.PushPose();
    stack.Translate(tag, 3.0f, 4.0f, 5.0f);
    stack.PopPose(1);
    EXPECT_FALSE(stack.Last().uniform_scale);
    EXPECT_FLOAT_EQ(stack.Last().tangent_orientation, -1.0f);
    EXPECT_FLOAT_EQ(stack.Last().normal_scale, 2.0f);

    stack.PopPose(0);
    EXPECT_TRUE(stack.Last().uniform_scale);
    EXPECT_FLOAT_EQ(stack.Last().tangent_orientation, -1.0f);
    EXPECT_FLOAT_EQ(stack.Last().normal_scale, 2.0f);

    stack.PushPose();
    EXPECT_TRUE(stack.Last().uniform_scale);
    EXPECT_FLOAT_EQ(stack.Last().tangent_orientation, -1.0f);
    EXPECT_FLOAT_EQ(stack.Last().normal_scale, 2.0f);
    EXPECT_NEAR(stack.Last().pose[0][1], -2.0f, 0.00001f);
    EXPECT_NEAR(stack.Last().normal[0][1], -1.0f, 0.00001f);
}

TEST(PoseStackTest, CopiesPoseIntoNativeSnapshotLayout) {
    gfx::math::PoseStack stack;
    stack.Scale(simd::GenericTag{}, -2.0f, 3.0f, 4.0f);

    gfx::math::PoseStack::Pose snapshot;
    stack.Last().CopyTo(snapshot);
    EXPECT_FALSE(snapshot.uniform_scale);
    EXPECT_FLOAT_EQ(snapshot.tangent_orientation, -1.0f);
    EXPECT_FLOAT_EQ(snapshot.normal_scale, 1.0f);
    EXPECT_FLOAT_EQ(snapshot.pose[0][0], -2.0f);
    EXPECT_FLOAT_EQ(snapshot.normal[0][0], -0.5f);
    EXPECT_FLOAT_EQ(snapshot.normal[1][1], 1.0f / 3.0f);
    EXPECT_FLOAT_EQ(snapshot.normal[2][2], 0.25f);
}
}  // namespace ysm::test

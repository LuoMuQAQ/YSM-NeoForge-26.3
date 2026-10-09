# GFX 高级能力

[返回基建总览](../infrastructure_cn.md)

主要头文件：`<gfx/bake/baked_model.h>`、`<gfx/bake/baked_serializer.h>`、`<gfx/math/pose_stack.h>`、`<gfx/renderer/model_state.h>`、`<gfx/renderer/render.h>`、`<gfx/renderer/schedule.h>`、`<gfx/renderer/color.h>`、`<gfx/renderer/bone_attribute.h>`、`<gfx/renderer/vertex/kind.h>`。

```text
model payload + RGBA texture
        -> BakeModel
        -> BakedModel
        -> SerializeBakedModel / ReadBakedModel
        -> ModelState::Extract
        -> Render
```

- `BakeModel` 是 renderer 的烘焙入口；`TryBakeModel` 仅检查可烘焙性。
- `SerializeBakedModel` / `ReadBakedModel` 读写 baked cache。读取门面负责结构、索引、层级、有限浮点值和 SIMD 布局校验。
- `PoseStack` 维护 pose/normal、uniform scale、tangent orientation 和 normal scale。`PushPose` 复制当前状态；`PopPose(depth)` 回到保存的深度。
- `EulerZYX`、`Mat4Mul` 和 `AffineMul` 提供 generic/ISA-tagged 路径，`SignBit` 提供位级符号判断。ISA overload 仅由 runtime 分派路径调用。
- `ModelState::Extract` 生成 renderer 状态并更新 schedule；成功后才能交给 `Render`。
- `Render` 按 `VertexKind`、runtime SIMD、render context 和 schedule 写入调用方 vertex buffer；容量不足或状态非法时返回 status。

`Pixel`、`Color`、`BoneAttribute` 和 `BonePose` 具有固定大小、对齐或字段布局，不能作为普通 struct 重排或 reinterpret cast。

`RenderSchedule`、`RenderState`、`VertexKindTraits`、cube buffer 和 worker-ready 协议只供 renderer 内部跨 translation unit 协作。新增 vertex layout 或调度模式仍通过 `ModelState` / `Render` 门面闭环。

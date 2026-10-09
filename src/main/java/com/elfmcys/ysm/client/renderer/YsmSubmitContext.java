package com.elfmcys.ysm.client.renderer;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Camera of the entity submit currently running on the render thread.
 * Valid from {@code EntityRenderDispatcher.submit} entry until it returns.
 */
public final class YsmSubmitContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private YsmSubmitContext() {
    }

    public static Scope begin(EntityRenderState state, CameraRenderState camera) {
        Frame previous = CURRENT.get();
        boolean inventory = Boolean.TRUE.equals(state.getRenderData(YsmEntityLookup.INVENTORY));
        InventoryPose pose = inventory && state instanceof LivingEntityRenderState living
                ? new InventoryPose(living.bodyRot, living.yRot, living.xRot) : null;
        CURRENT.set(new Frame(camera, inventory, pose == null ? null : YsmEntityLookup.entity(state), pose));
        return new Scope(previous);
    }

    public static CameraRenderState camera() {
        Frame frame = CURRENT.get();
        return frame == null ? null : frame.camera();
    }

    public static boolean isInventory() {
        Frame frame = CURRENT.get();
        return frame != null && frame.inventory();
    }

    public static InventoryPose inventoryPose(Entity entity) {
        Frame frame = CURRENT.get();
        return frame != null && frame.entity() == entity ? frame.pose() : null;
    }

    public record InventoryPose(float bodyRot, float headYaw, float pitch) {
    }

    private record Frame(CameraRenderState camera, boolean inventory, Entity entity, InventoryPose pose) {
    }

    public static final class Scope implements AutoCloseable {
        private final Frame previous;

        private Scope(Frame previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}

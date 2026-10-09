package com.elfmcys.ysm.client.renderer;

import com.elfmcys.ysm.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class YsmEntityLookup {
    public static final ContextKey<Integer> ENTITY_ID = new ContextKey<>(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "render_entity_id"));
    public static final ContextKey<Boolean> INVENTORY = new ContextKey<>(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "render_inventory"));

    private YsmEntityLookup() {
    }

    public static @Nullable Entity entity(EntityRenderState state) {
        Integer id = state.getRenderData(YsmEntityLookup.ENTITY_ID);
        if (id == null && state instanceof AvatarRenderState avatar) {
            id = avatar.id;
        }
        if (id == null) {
            return null;
        }
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }
}

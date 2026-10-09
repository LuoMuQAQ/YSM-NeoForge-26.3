package com.elfmcys.ysm.client.renderer;

import com.elfmcys.ysm.client.gui.YsmModelPreviewState;
import com.elfmcys.ysm.client.gui.YsmPreviewRenderer;
import com.google.common.reflect.TypeToken;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

public final class YsmClientRenderSetup {
    private YsmClientRenderSetup() {
    }

    public static void registerRenderState(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) ->
                state.setRenderData(YsmEntityLookup.ENTITY_ID, entity.getId()));
    }

    public static void registerPreviewRenderer(RegisterPictureInPictureRenderersEvent event) {
        event.register(YsmModelPreviewState.class, YsmPreviewRenderer::new);
    }
}

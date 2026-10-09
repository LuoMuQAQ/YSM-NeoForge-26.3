// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */

package com.elfmcys.ysm.geckolib3.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

public class AnimationUtils {
    public static float convertTicksToSeconds(float ticks) {
        return ticks / 20;
    }

    public static float convertSecondsToTicks(float seconds) {
        return seconds * 20;
    }

    /**
     * 获取实体的 Renderer
     */
    public static EntityRenderer<?, ?> getRenderer(Entity entity) {
        EntityRenderDispatcher renderManager = Minecraft.getInstance().getEntityRenderDispatcher();
        return renderManager.getRenderer(entity);
    }
}
// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.client.renderer.YsmEntityLookup;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryScreen.class)
public class InventoryScreenMixin {
    @ModifyExpressionValue(
            method = "renderEntityInInventoryFollowsAngle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private static EntityRenderState ysm$tagInventoryState(EntityRenderState state) {
        state.setRenderData(YsmEntityLookup.INVENTORY, true);
        return state;
    }
}

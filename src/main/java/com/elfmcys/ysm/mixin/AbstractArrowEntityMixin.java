// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.mixin;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.accessor.IArrowExtraInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowEntityMixin implements IArrowExtraInfo {
    @Unique
    private String shootItemId = StringUtils.EMPTY;
    @Shadow
    protected abstract boolean isInGround();
    @Shadow
    protected int inGroundTime;

    @Unique
    @Override
    public boolean ysm$isInGround() {
        return isInGround();
    }

    @Unique
    @Override
    public int inGroundTime() {
        return inGroundTime;
    }

    @Unique
    @Override
    public String getShootItemId() {
        return shootItemId;
    }

    @Inject(at = @At("RETURN"), method = "setOwner(Lnet/minecraft/world/entity/Entity;)V")
    private void setOwner(Entity owner, CallbackInfo callbackInfo) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        // 设置 owner 时，缓存一下射击时主手物品 ID，用于 molang
        if (owner instanceof LivingEntity livingEntity) {
            Identifier key = RegistryIds.itemId(livingEntity.getMainHandItem().getItem());
            if (key != null) {
                shootItemId = key.toString();
            }
        }
    }
}

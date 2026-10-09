package com.elfmcys.ysm.mixin.client;

import com.elfmcys.ysm.accessor.IEntityMovementInfo;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMovementMixin implements IEntityMovementInfo {
    @Shadow protected abstract Entity.MovementEmission getMovementEmission();
    @Shadow public abstract boolean isPassenger();

    // Publish both tick endpoints together for parallel animation readers.
    @Unique private volatile WalkingDistance ysm$distance = new WalkingDistance(0, 0);

    @Override
    public WalkingDistance ysm$walkingDistance() {
        return ysm$distance;
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void ysm$beginMovementTick(CallbackInfo ci) {
        float current = ysm$distance.current();
        ysm$distance = new WalkingDistance(current, current);
    }

    @Inject(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBlockSpeedFactor()F"))
    private void ysm$accumulateWalkingDistance(MoverType moverType, Vec3 requestedMovement, CallbackInfo ci,
                                               @Local(name = "movement") Vec3 clippedMovement) {
        // Same emission/passenger gate and horizontal * 0.6 input as the old
        // walkDist. This point skips noPhysics, early returns and removed entities;
        // setPos/teleports and rendering never accumulate distance.
        if (getMovementEmission().emitsAnything() && !isPassenger()) {
            WalkingDistance previous = ysm$distance;
            ysm$distance = new WalkingDistance(previous.previous(),
                    previous.current() + (float) clippedMovement.horizontalDistance() * 0.6F);
        }
    }
}

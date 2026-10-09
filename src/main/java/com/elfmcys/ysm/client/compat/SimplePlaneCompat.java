// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import com.elfmcys.ysm.client.entity.CustomVehicleEntity;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.geckolib3.core.util.MathUtil;
import com.mojang.math.Axis;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Math;
import org.joml.Vector3f;

import java.util.Optional;

public class SimplePlaneCompat {
    private static final String MOD_ID = "simpleplanes";
    private static boolean INSTALLED;

    public static void init() {
        try {
            INSTALLED = LoadingModList.get().getModFileById(MOD_ID) != null;
        } catch (Throwable ignored) {
        }
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MOD_ID);
    }

    public static Optional<Vector3f> getRotation(AnimationEvent<CustomVehicleEntity> event) {
        if (!isInstalled()) return Optional.empty();
        return OptionalApi.query(MOD_ID, Optional.empty(), () -> {
        var planeEntity = event.getAnimatableEntity().getEntity();
        if (OptionalApi.instance("xyz.przemyk.simpleplanes.entities.PlaneEntity", planeEntity)) {
            var q = (org.joml.Quaternionf) OptionalApi.callStatic("xyz.przemyk.simpleplanes.misc.MathUtil", "lerpQ", event.getPartialTick(),
                    OptionalApi.call(planeEntity, "getQ_Prev"), OptionalApi.call(planeEntity, "getQ_Client"));

            q.premul(Axis.YP.rotation(-MathUtil.degreesToRadians(planeEntity.getViewYRot(event.getPartialTick()))));

            float timeSinceHitWithPartial = OptionalApi.number(OptionalApi.call(planeEntity, "getTimeSinceHit")) - event.getPartialTick();
            if (timeSinceHitWithPartial > 0.0F) {
                float angle = Math.clamp(timeSinceHitWithPartial / 10.0F, -30, 30);
                timeSinceHitWithPartial = planeEntity.tickCount + event.getPartialTick();
                q.rotateZ(Math.sin(timeSinceHitWithPartial) * angle);
            }

            var rot = new Vector3f();
            MathUtil.getEulerAnglesZYX(q, rot);
            rot.x = -rot.x;
            return Optional.of(rot);
        }
        return Optional.empty();
        });
    }
}

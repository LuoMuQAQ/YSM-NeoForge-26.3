// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat;

import com.elfmcys.ysm.client.compat.OptionalApi;

import com.elfmcys.ysm.client.entity.CustomVehicleEntity;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.geckolib3.core.util.MathUtil;
import com.mojang.math.Axis;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Vector3f;

import java.util.Optional;

public class ImmersiveAircraftCompat {
    private static final String MOD_ID = "immersive_aircraft";
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
        if (OptionalApi.instance("immersive_aircraft.entity.AircraftEntity", planeEntity)) {
            Vector3f effect = planeEntity.onGround() ? new Vector3f(0.0f, 0.0f, 0.0f) : (Vector3f) OptionalApi.call(planeEntity, "getWindEffect");
            var rot = new Vector3f();
            MathUtil.getEulerAnglesZYX(Axis.XP.rotationDegrees(effect.z).rotateZ(MathUtil.degreesToRadians(effect.x))
                    .rotateX(-MathUtil.degreesToRadians(planeEntity.getViewXRot(event.getPartialTick())))
                    .rotateZ(-MathUtil.degreesToRadians(OptionalApi.number(OptionalApi.call(planeEntity, "getRoll", event.getPartialTick())))), rot);
            return Optional.of(rot);
        }
        return Optional.empty();
        });
    }
}

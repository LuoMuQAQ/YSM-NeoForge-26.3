// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.model.locator;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.api.model.v0.ModelKind;
import com.elfmcys.ysm.api.model.v0.event.RegisterModelLocatorEvent;
import com.elfmcys.ysm.geckolib3.geo.render.built.GeoLocatorType;
import net.minecraft.resources.Identifier;

public class ProjectileLocator extends GeoLocatorType {
    private static ProjectileLocator INSTANCE;

    @SuppressWarnings("removal")
    private ProjectileLocator() {
        super(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "projectile"));
    }

    public static void init() {
        if (INSTANCE == null) {
            synchronized (ProjectileLocator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProjectileLocator();
                    YesSteveModel.postEvent(new RegisterModelLocatorEvent(ModelKind.PROJECTILE, INSTANCE::register));
                    INSTANCE.freeze();
                }
            }
        }
    }

    public static ProjectileLocator get() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        throw new IllegalStateException("ProjectileLocator has not been initialized");
    }
}

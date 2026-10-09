// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.geo.render.built;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeoLocatorTypeRegistry {
    private static final HashMap<Identifier, GeoLocatorType> MAP = new HashMap<>();
    private static final AtomicBoolean FROZEN = new AtomicBoolean(false);

    static void register(GeoLocatorType type) {
        if (FROZEN.getAcquire()) {
            throw new IllegalStateException("GeoLocatorTypeRegistry is already frozen");
        }
        synchronized (MAP) {
            if (MAP.computeIfAbsent(type.id(), id -> type) != type) {
                throw new IllegalStateException("GeoLocatorType " + type.id() + " is already registered");
            }
        }
    }

    public void freeze() {
        FROZEN.setRelease(true);
    }

    @Nullable
    public static GeoLocatorType get(Identifier id) {
        return MAP.get(id);
    }
}

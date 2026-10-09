// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.init;

import com.elfmcys.ysm.YesSteveModel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, YesSteveModel.MOD_ID);

    public static final SoundEvent CUSTOM = registerSound("custom");

    private static SoundEvent registerSound(String name) {
        var ev = SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, name), 16.0F);
        SOUNDS.register(name, () -> ev);
        return ev;
    }
}

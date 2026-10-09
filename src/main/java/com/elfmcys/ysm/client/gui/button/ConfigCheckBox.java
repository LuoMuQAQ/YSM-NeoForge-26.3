// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigCheckBox extends FlatCheckbox {

    public ConfigCheckBox(int pX, int pY, String key, ModConfigSpec.BooleanValue configSpec) {
        super(pX, pY, 400, Component.translatable("gui.yes_steve_model.config." + key), configSpec::set);
        setStateTriggered(configSpec.get());
    }
}

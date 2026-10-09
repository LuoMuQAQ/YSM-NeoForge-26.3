// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.slashblade;

import com.elfmcys.ysm.client.compat.OptionalApi;

public class SlashBladeUnsafe {
    static void initFiledOffset() {
        OptionalApi.query("slashblade", false, () -> {
            OptionalApi.type("mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState");
            return true;
        });
    }

    static String getOldComboStateName(Object state, long time) {
        var combo = OptionalApi.get(state, "comboSeq");
        if (!OptionalApi.instance("mods.flammpfeil.slashblade.capability.slashblade.ComboState", combo)) return "";
        if (time > OptionalApi.integer(OptionalApi.call(combo, "getTimeoutMS"))) return "";
        var name = (String) OptionalApi.call(combo, "getName");
        return "slashblade:" + (name.startsWith("ex_") ? name.substring(3) : name);
    }
}

// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.swem;

import com.elfmcys.ysm.client.animation.molang.CtrlBinding;

public class SwemCtrlBinding {
    static void addInnerBinding(CtrlBinding binding) {
        binding.livingEntityVar("swem_is_ride", ctx -> SwemCompatInner.isHorse(ctx.entity().getVehicle()));
        binding.livingEntityVar("swem_state", ctx -> {
            var horse = ctx.entity().getVehicle();
            return SwemCompatInner.isHorse(horse) ? SwemCompatInner.state(horse) : "";
        });
    }
}

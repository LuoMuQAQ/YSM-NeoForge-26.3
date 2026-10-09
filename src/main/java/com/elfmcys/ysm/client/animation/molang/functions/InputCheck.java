// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.molang.functions;

import com.elfmcys.ysm.client.event.ModInputEvent;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import com.elfmcys.ysm.molang.runtime.Function;
import com.elfmcys.ysm.util.InputCheckUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InputCheck {
    public static class Keyboard implements Function {
        @Override
        public @Nullable Object evaluate(@NotNull ExecutionContext<?> context, @NotNull ArgumentCollection arguments) {
            if (!InputCheckUtil.isInGame()) {
                return false;
            }
            for (var i = 0; i < arguments.size(); i++) {
                int keyCode = arguments.getAsInt(context, i);
                if (0 <= keyCode && keyCode < ModInputEvent.KEY_STATES.length && ModInputEvent.KEY_STATES[keyCode]) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean validateArgumentSize(int size) {
            return size >= 1;
        }
    }

    public static class Mouse implements Function {
        @Override
        public @Nullable Object evaluate(@NotNull ExecutionContext<?> context, @NotNull ArgumentCollection arguments) {
            if (!InputCheckUtil.isInGame()) {
                return false;
            }
            int button = arguments.getAsInt(context, 0);
            if (0 <= button && button < ModInputEvent.MOUSE_STATES.length) {
                return ModInputEvent.MOUSE_STATES[button];
            }
            return false;
        }

        @Override
        public boolean validateArgumentSize(int size) {
            return size == 1;
        }
    }
}

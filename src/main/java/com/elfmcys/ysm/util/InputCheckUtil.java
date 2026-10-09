// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.InputEvent;

public class InputCheckUtil {
    public static boolean keyIsMatch(InputEvent.Key event, KeyMapping keyMapping) {
        return keyMapping.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()));
    }

    public static boolean isInGame() {
        Minecraft mc = Minecraft.getInstance();
        // 不能是加载界面
        if (mc.gui.overlay() != null) {
            return false;
        }
        // 不能打开任何 GUI
        if (mc.gui.screen() != null) {
            return false;
        }
        // 当前窗口捕获鼠标操作
        if (!mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        // 选择了当前窗口
        return mc.isWindowActive();
    }
}

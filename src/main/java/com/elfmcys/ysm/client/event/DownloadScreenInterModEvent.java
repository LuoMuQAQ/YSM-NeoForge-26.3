// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.gui.DownloadScreen;
import com.elfmcys.ysm.client.gui.PlayerModelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@OnlyIn(Dist.CLIENT)
@net.neoforged.fml.common.EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT)
public class DownloadScreenInterModEvent {
    private static final String DOWNLOAD_SCREEN_METHOD = "DownloadScreen";
    private static @Nullable Screen DOWNLOAD_SCREEN;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInterModProcess(InterModProcessEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        InterModComms.getMessages(YesSteveModel.MOD_ID).findFirst().ifPresent(message -> {
            String method = message.method();
            if (DOWNLOAD_SCREEN_METHOD.equals(method) && message.messageSupplier().get() instanceof Screen screen) {
                DOWNLOAD_SCREEN = screen;
            }
        });
    }

    public static void openDownloadScreen(PlayerModelScreen modelScreen) {
        modelScreen.getMinecraft().gui.setScreen(Objects.requireNonNullElseGet(DOWNLOAD_SCREEN, () -> new DownloadScreen(modelScreen)));
    }
}

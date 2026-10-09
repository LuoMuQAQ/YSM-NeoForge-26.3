// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm;

import com.elfmcys.ysm.api.internal.event.YsmEventHandlerLoader;
import com.elfmcys.ysm.config.ClientConfig;
import com.elfmcys.ysm.config.ServerConfig;
import com.elfmcys.ysm.init.ModSounds;
import com.elfmcys.ysm.util.NativeLibUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

@Mod(YesSteveModel.MOD_ID)
@SuppressWarnings("removal")
public class YesSteveModel {
    public static final String MOD_ID = "ysm";
    public static ModContainer MOD;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static IEventBus EVENT_BUS;

    public YesSteveModel(IEventBus eventBus, ModContainer container) throws IOException {
        MOD = container;
        EVENT_BUS = eventBus;
        initConfig();
        com.elfmcys.ysm.capability.EntityAttachments.register(eventBus);
        EVENT_BUS.addListener(com.elfmcys.ysm.network.NetworkHandler::register);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            EVENT_BUS.addListener(com.elfmcys.ysm.client.renderer.YsmClientRenderSetup::registerRenderState);
            EVENT_BUS.addListener(com.elfmcys.ysm.client.renderer.YsmClientRenderSetup::registerPreviewRenderer);
            EVENT_BUS.addListener(com.elfmcys.ysm.client.event.RegisterEntityRenderersEvent::onAddClientReloadListeners);
        }

        NativeLibUtil.load();
        if (!NativeLibUtil.isAvailable()) {
            LOGGER.error(getUnavailableMessageString());
            return;
        }

        YsmEventHandlerLoader.attach(EVENT_BUS);
    }

    public static void registerEventHandler(Object handler) {
        EVENT_BUS.register(handler);
    }

    private static void initConfig() {
        MOD.registerConfig(ModConfig.Type.CLIENT, ClientConfig.init());
        MOD.registerConfig(ModConfig.Type.SYNCED, ServerConfig.init());
        ModSounds.SOUNDS.register(EVENT_BUS);
    }

    public static boolean postEvent(Event event) {
        EVENT_BUS.post(event);
        return event instanceof net.neoforged.bus.api.ICancellableEvent cancellable
                && cancellable.isCanceled();
    }

    public static boolean isAvailable() {
        return NativeLibUtil.isAvailable();
    }

    public static boolean isMobilePlatform() {
        return NativeLibUtil.isMobilePlatform();
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendUnavailableMessage() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(getUnavailableMessage());
        }
    }

    public static ModLoadingIssue getUnavailableWarning() {
        return NativeLibUtil.getUnavailableWarning();
    }

    public static Component getUnavailableMessage() {
        return NativeLibUtil.getUnsupportedMsg();
    }

    public static String getUnavailableMessageString() {
        return NativeLibUtil.getUnsupportedMsgStr();
    }
}

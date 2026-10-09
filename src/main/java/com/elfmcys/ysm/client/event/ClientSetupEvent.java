// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.animation.AnimationRegister;
import com.elfmcys.ysm.client.compat.ARCompat;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.CosmeticArmorCompat;
import com.elfmcys.ysm.client.compat.ElytraSlotCompat;
import com.elfmcys.ysm.client.compat.FirstPersonCompat;
import com.elfmcys.ysm.client.compat.ImmersiveAircraftCompat;
import com.elfmcys.ysm.client.compat.IrisCompat;
import com.elfmcys.ysm.client.compat.OptifineCompat;
import com.elfmcys.ysm.client.compat.PlayerAnimatorCompat;
import com.elfmcys.ysm.client.compat.SimplePlaneCompat;
import com.elfmcys.ysm.client.compat.backpack.sophisticated.SophisticatedCompat;
import com.elfmcys.ysm.client.compat.bettercombat.BetterCombatCompat;
import com.elfmcys.ysm.client.compat.carryon.CarryOnCompat;
import com.elfmcys.ysm.client.compat.create.CreateCompat;
import com.elfmcys.ysm.client.compat.curios.CuriosCompat;
import com.elfmcys.ysm.client.compat.immersivemelodies.ImmersiveMelodiesCompat;
import com.elfmcys.ysm.client.compat.ironsspellbooks.IronsSpellBooksCompat;
import com.elfmcys.ysm.client.compat.parcool.ParCoolCompat;
import com.elfmcys.ysm.client.compat.realcamera.RealCameraCompat;
import com.elfmcys.ysm.client.compat.simplehat.SimpleHatsCompat;
import com.elfmcys.ysm.client.compat.slashblade.SlashBladeCompat;
import com.elfmcys.ysm.client.compat.swarfare.SWarfareCompat;
import com.elfmcys.ysm.client.compat.swem.SwemCompat;
import com.elfmcys.ysm.client.compat.tacz.TACZCompat;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.TlmClientCompat;
import com.elfmcys.ysm.client.gui.overlay.ExtraPlayerScreen;
import com.elfmcys.ysm.client.input.AnimationRouletteKey;
import com.elfmcys.ysm.client.input.DebugAnimationKey;
import com.elfmcys.ysm.client.input.ExtraAnimationKey;
import com.elfmcys.ysm.client.input.ExtraPlayerConfigKey;
import com.elfmcys.ysm.client.input.PlayerModelScreenKey;
import com.elfmcys.ysm.client.model.locator.FirstPersonLocator;
import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.model.service.ClientModelService;
import com.elfmcys.ysm.client.model.locator.ProjectileLocator;
import com.elfmcys.ysm.client.model.locator.VehicleLocator;
import com.elfmcys.ysm.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

@net.neoforged.fml.common.EventBusSubscriber(value = Dist.CLIENT)
public class ClientSetupEvent {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }

        AnimationRegister.registerAnimationState();

        event.enqueueWork(() -> {
            OptionalApi.initialize("curios", CuriosCompat::init);
            OptionalApi.initialize("firstperson", FirstPersonCompat::init);
            OptionalApi.initialize("realcamera", RealCameraCompat::init);
            OptionalApi.initialize("player_animation_library", PlayerAnimatorCompat::init);
            OptionalApi.initialize("bettercombat", BetterCombatCompat::init);
            OptionalApi.initialize("iris", IrisCompat::init);
            OptionalApi.initialize("acceleratedrendering", ARCompat::init);
            OptionalApi.initialize("optifine", OptifineCompat::init);
            OptionalApi.initialize("cosmeticarmorreworked", CosmeticArmorCompat::init);
            OptionalApi.initialize("elytraslot", ElytraSlotCompat::init);
            OptionalApi.initialize("tacz", TACZCompat::init);
            OptionalApi.initialize("superbwarfare", SWarfareCompat::init);
            OptionalApi.initialize("touhou_little_maid", TlmClientCompat::init);
            OptionalApi.initialize("carryon", CarryOnCompat::init);
            OptionalApi.initialize("parcool", ParCoolCompat::init);
            OptionalApi.initialize("slashblade", SlashBladeCompat::init);
            OptionalApi.initialize("swem", SwemCompat::init);
            OptionalApi.initialize("create", CreateCompat::init);
            OptionalApi.initialize("sophisticatedbackpacks", SophisticatedCompat::init);
            OptionalApi.initialize("simplehats", SimpleHatsCompat::init);
            OptionalApi.initialize("immersive_melodies", ImmersiveMelodiesCompat::init);
            OptionalApi.initialize("irons_spellbooks", IronsSpellBooksCompat::init);
            OptionalApi.initialize("simpleplanes", SimplePlaneCompat::init);
            OptionalApi.initialize("immersive_aircraft", ImmersiveAircraftCompat::init);

            checkCompatibility(ParCoolCompat.getCompatibilityWarning());
            checkCompatibility(SophisticatedCompat.getCompatibilityWarning());
            if (ClientConfig.DISABLE_SELF_MODEL.get() &&
                    ClientConfig.DISABLE_OTHER_MODEL.get() &&
                    ClientConfig.DISABLE_SELF_HANDS.get()) {
                informIncompatible("epicfight", "Epic Fight");
            }

            // Model render target data is now owned by the Java model service.
            PlayerLocator.init();
            FirstPersonLocator.init();
            ProjectileLocator.init();
            VehicleLocator.init();
        });
    }

    @SubscribeEvent
    public static void onClientStarted(ClientStartedEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        // 26.3 setup work runs on modloading-sync-worker while the client waits
        // in ClientModLoader.finish(). The first-tick lifecycle boundary has a
        // live client/render owner that can drain required GPU publication work.
        YesSteveModel.LOGGER.info("Starting required client model service on client/render thread");
        ClientModelService.start();
        YesSteveModel.LOGGER.info("Client model service ready after required default texture publication");
    }

    private static void checkCompatibility(Optional<Pair<String, String>> infoHolder) {
        infoHolder.ifPresent(info -> {
            ModLoader.addLoadingIssue(ModLoadingIssue.warning(
                    "error.yes_steve_model.incompatible_mod_version", info.getKey(), info.getValue())
                    .withAffectedMod(YesSteveModel.MOD.getModInfo()));
        });
    }

    private static void informIncompatible(String modId, String modName) {
        if (LoadingModList.get().getModFileById(modId) != null) {
            ModLoader.addLoadingIssue(ModLoadingIssue.warning(
                    "error.yes_steve_model.incompatible_mod", modName)
                    .withAffectedMod(YesSteveModel.MOD.getModInfo()));
        }
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(com.elfmcys.ysm.client.input.YsmKeyMappings.CATEGORY);
        event.register(PlayerModelScreenKey.PLAYER_MODEL_KEY);

        if (!YesSteveModel.isAvailable()) {
            return;
        }

        event.register(AnimationRouletteKey.ANIMATION_ROULETTE_KEY);
        event.register(AnimationRouletteKey.LOCK_ROULETTE_KEY);
        event.register(DebugAnimationKey.DEBUG_ANIMATION_KEY);
        event.register(ExtraPlayerConfigKey.EXTRA_PLAYER_RENDER_KEY);
        ExtraAnimationKey.registerKeyBinding(event);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "loading_state"),
                new com.elfmcys.ysm.client.gui.overlay.LoadingStateScreen());
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "debug_animation"),
                com.elfmcys.ysm.client.gui.overlay.DebugAnimationScreen.getGuiOverlay());
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "extra_player"),
                new ExtraPlayerScreen());
    }

}

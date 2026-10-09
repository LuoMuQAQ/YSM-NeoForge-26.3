// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client;

import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.event.SyncCapability;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.event.YsmMaidScreenEvent;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.event.YsmMaidTickEvent;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.render.CustomYsmMaidRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import java.util.function.Function;

public class TlmClientCompatInner {
    private static CustomYsmMaidRenderer renderer;
    private static Object previousFactory;
    private static final java.util.List<Object> registeredListeners = new java.util.ArrayList<>();

    static boolean registerYsmEntityMaidRenderer() {
        return OptionalApi.query(MaidApi.ID, false, () -> {
            var contract = OptionalApi.type(MaidApi.ROOT + "geckolib3.geo.IGeoEntityRenderer");
            // Validate the real provider's submission boundary before replacing any factory.
            var draw = java.util.Arrays.stream(contract.getMethods()).filter(m -> m.getName().equals("geoRender")
                    && m.getParameterCount() == 6).findFirst().orElseThrow();
            if (!SubmitNodeCollector.class.isAssignableFrom(draw.getParameterTypes()[4])) {
                throw new IllegalStateException("Maid provider uses the removed immediate rendering API");
            }
            // Resolve the full registration boundary before changing provider state.
            providerEventType("OpenYsmMaidScreenEvent");
            providerEventType("YsmMaidClientTickEvent");
            var entityContract = OptionalApi.type(MaidApi.ROOT + "geckolib3.geo.IGeoEntity");
            if (!contract.isInterface() || !entityContract.isInterface()) {
                throw new IllegalStateException("Maid provider does not expose proxyable interfaces");
            }
            previousFactory = OptionalApi.getStatic(MaidApi.ROOT + "client.renderer.entity.EntityMaidRenderer", "YSM_ENTITY_MAID_RENDERER");
            Function<EntityRendererProvider.Context, Object> factory = context -> {
                renderer = new CustomYsmMaidRenderer(context);
                return renderer.asProviderRenderer();
            };
            OptionalApi.setStatic(MaidApi.ROOT + "client.renderer.entity.EntityMaidRenderer", "YSM_ENTITY_MAID_RENDERER", factory);
            return true;
        });
    }

    static void registerEvent() {
        registerProviderEvent("OpenYsmMaidScreenEvent", new YsmMaidScreenEvent()::onOpenYsmMaidScreen);
        registerProviderEvent("YsmMaidClientTickEvent", new YsmMaidTickEvent()::onTickYsmMaid);
        var sync = new SyncCapability();
        NeoForge.EVENT_BUS.register(sync);
        registeredListeners.add(sync);
    }

    static void restoreRendererFactory() {
        for (var listener : registeredListeners) NeoForge.EVENT_BUS.unregister(listener);
        registeredListeners.clear();
        OptionalApi.setStatic(MaidApi.ROOT + "client.renderer.entity.EntityMaidRenderer", "YSM_ENTITY_MAID_RENDERER", previousFactory);
        renderer = null;
    }

    private static Class<? extends Event> providerEventType(String name) {
        return OptionalApi.type(MaidApi.ROOT + "compat.ysm.event." + name).asSubclass(Event.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerProviderEvent(String name, Consumer<Object> listener) {
        var type = providerEventType(name);
        Consumer safeListener = event -> OptionalApi.query(MaidApi.ID, null, () -> { listener.accept(event); return null; });
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) type, safeListener);
        registeredListeners.add(safeListener);
    }

    static boolean isMaid(Entity entity) { return MaidApi.isMaid(entity); }
    static boolean hasMaidCap(Entity entity) { return YsmMaidCapabilityProvider.get(entity).isPresent() && MaidApi.flag(entity, "isYsmModel"); }
    static java.util.Optional<? extends com.elfmcys.ysm.client.entity.CustomEntity<?>> debugTarget(Entity entity) {
        return YsmMaidCapabilityProvider.existing(entity);
    }
    static void releaseAnimatable(Entity entity) { YsmMaidCapabilityProvider.release(entity); }
    static boolean isChair(Entity entity) { return MaidApi.is("entity.item.EntityChair", entity); }
    static boolean isSit(Entity entity) { return MaidApi.is("entity.item.EntitySit", entity); }
    static String getChairId(Entity entity) { return isChair(entity) ? MaidApi.text(entity, "getModelId") : ""; }
    static boolean maidIsFishing(LivingEntity entity) {
        return isMaid(entity) && OptionalApi.query(MaidApi.ID, false, () -> OptionalApi.get(entity, "fishing") != null);
    }
    static void markTacGunAnimationNeedReload(LivingEntity entity) {
        YsmMaidCapabilityProvider.existing(entity).ifPresent(cap -> cap.setTacGunAnimationNeedReload(true));
    }
    static boolean isGohei(Item item) { return MaidApi.is("item.ItemHakureiGohei", item); }
    static CustomYsmMaidRenderer getRenderer() { return renderer; }
}

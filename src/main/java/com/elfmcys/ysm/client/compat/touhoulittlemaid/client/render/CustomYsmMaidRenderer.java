// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client.render;

import com.elfmcys.ysm.capability.VehicleAnimatableCapabilityProvider;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.capability.YsmMaidCapabilityProvider;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.CustomYsmMaidEntity;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.elfmcys.ysm.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomYsmMaidRenderer extends GeoReplacedEntityRenderer<LivingEntity, CustomYsmMaidEntity> {
    private final List<Object> maidLayers = new CopyOnWriteArrayList<>();

    public CustomYsmMaidRenderer(EntityRendererProvider.Context context) {}

    public Object asProviderRenderer() {
        var contract = OptionalApi.type(MaidApi.ROOT + "geckolib3.geo.IGeoEntityRenderer");
        return java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (self, method, args) -> {
            return switch (method.getName()) {
                case "getGeoEntity" -> YsmMaidCapabilityProvider.get((LivingEntity) args[0]).orElseThrow().asProviderEntity();
                case "addGeoLayerRenderer" -> { maidLayers.add(args[0]); yield null; }
                case "geoRender" -> {
                    // An old immediate-buffer provider is incompatible with 26.3 submission.
                    if (!(args[4] instanceof SubmitNodeCollector collector)) {
                        throw new IllegalStateException("Maid provider has no 26.3 submit collector");
                    }
                    var entity = (LivingEntity) args[0];
                    YsmMaidCapabilityProvider.get(entity).ifPresent(owner -> submitAnimatable(owner, null,
                            ((Number) args[2]).floatValue(), (PoseStack) args[3], collector, ((Number) args[5]).intValue(), null));
                    yield null;
                }
                case "getTextureLocation" -> YsmMaidCapabilityProvider.get((LivingEntity) args[0])
                        .map(CustomYsmMaidEntity::getTextureLocation).orElse(MissingTextureAtlasSprite.getLocation());
                case "toString" -> "YSM maid submit adapter";
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> throw new UnsupportedOperationException(method.toString());
            };
        });
    }

    @Override
    protected void submitLayers(PoseStack pose, SubmitNodeCollector collector, CustomYsmMaidEntity animatable,
                                GeoRenderData data, AvatarRenderState avatar, int light, int overlay) {
        super.submitLayers(pose, collector, animatable, data, avatar, light, overlay);
        for (var layer : maidLayers) {
            OptionalApi.query(MaidApi.ID, null, () -> OptionalApi.call(layer, "render", pose, collector,
                    light, animatable.getEntity(), data.animationData.limbSwing, data.animationData.limbSwingAmount,
                    data.partialTicks, data.animationData.lerpedAge, data.animationData.netHeadYaw, data.animationData.headPitch));
        }
    }

    @Override
    protected void setupRotations(LivingEntity maid, PoseStack pose, float age, float yaw, float partialTick) {
        super.setupRotations(maid, pose, age, yaw, partialTick);
        if (MaidApi.flag(maid, "isMaidInSittingPose")) { pose.translate(0, -0.5, 0); return; }
        var vehicle = maid.getVehicle();
        if (vehicle instanceof Player) { pose.translate(-0.05, 0.19, 0.24); return; }
        if (vehicle != null && (vehicle instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat
                || vehicle instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
                || MaidApi.is("entity.item.EntityBroom", vehicle))
                && VehicleAnimatableCapabilityProvider.get(vehicle).isEmpty()) pose.translate(0, -0.5, 0);
    }
}

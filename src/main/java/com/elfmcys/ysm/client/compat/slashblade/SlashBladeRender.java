// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.slashblade;

import com.elfmcys.ysm.client.model.locator.PlayerLocator;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("removal")
public class SlashBladeRender {
    private static final Identifier RESOURCE_DEFAULT_MODEL = Identifier.fromNamespaceAndPath("slashblade", "model/blade.obj");
    private static final Identifier RESOURCE_DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath("slashblade", "model/blade.png");

    private static void withBlade(ItemStack stack, java.util.function.Consumer<Object> render) {
        OptionalApi.query("slashblade", null, () -> {
            var state = SlashBladeAnimation.bladeState(stack);
            if (state != null) render.accept(state);
            return null;
        });
    }

    private static void draw(boolean luminous, ItemStack stack, Object model, String part,
                             Identifier texture, PoseStack pose, SubmitNodeCollector collector, int light) {
        OptionalApi.callStatic("mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState",
                luminous ? "renderOverridedLuminous" : "renderOverrided", stack, model, part, texture, pose, collector, light);
    }

    public static void renderSlashBlade(PoseStack matrixStack, SubmitNodeCollector bufferIn, int lightIn, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        withBlade(stack, bladeState -> {
            Identifier texture = ((java.util.Optional<Identifier>) OptionalApi.call(bladeState, "getTexture")).orElse(RESOURCE_DEFAULT_TEXTURE);
            var manager = OptionalApi.callStatic("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager", "getInstance");
        var obj = OptionalApi.call(manager, "getModel", ((java.util.Optional<Identifier>) OptionalApi.call(bladeState, "getModel")).orElse(RESOURCE_DEFAULT_MODEL));
            String part;
            if (OptionalApi.bool(OptionalApi.call(bladeState, "isBroken"))) {
                part = "blade_damaged";
            } else {
                part = "blade";
            }
            draw(false, stack, obj, part, texture, matrixStack, bufferIn, lightIn);
            draw(true, stack, obj, part + "_luminous", texture, matrixStack, bufferIn, lightIn);
            draw(false, stack, obj, "sheath", texture, matrixStack, bufferIn, lightIn);
            draw(true, stack, obj, "sheath_luminous", texture, matrixStack, bufferIn, lightIn);
        });
    }

    public static void renderMainhandSlashBlade(LivingEntity livingEntity, GeoRenderData data, PoseStack matrixStack,
                                                SubmitNodeCollector bufferIn, int lightIn, ItemStack stack) {
        if (SlashBladeCompat.isSlashBladeItem(stack)) {
            // 如果没有 bladeBones 和 sheathBones，说明是旧版渲染
            if (data.modelState.locatorGroupSize(PlayerLocator.get().blade) == 0 ||
                data.modelState.locatorGroupSize(PlayerLocator.get().sheath) == 0 ||
                data.modelState.locatorGroupSize(PlayerLocator.get().leftWaist) == 0) {
                oldMainhandSlashBlade(livingEntity, data, matrixStack, bufferIn, lightIn, stack);
            } else {
                withBlade(stack, bladeState -> {
                    newMainhandSlashBlade(bladeState, data, matrixStack, bufferIn, lightIn, stack);
                });
            }
        }
    }

    private static void newMainhandSlashBlade(Object bladeState, GeoRenderData data, PoseStack matrixStack,
                                              SubmitNodeCollector bufferIn, int lightIn, ItemStack stack) {
        Identifier texture = ((java.util.Optional<Identifier>) OptionalApi.call(bladeState, "getTexture")).orElse(RESOURCE_DEFAULT_TEXTURE);
        var manager = OptionalApi.callStatic("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager", "getInstance");
        var obj = OptionalApi.call(manager, "getModel", ((java.util.Optional<Identifier>) OptionalApi.call(bladeState, "getModel")).orElse(RESOURCE_DEFAULT_MODEL));
        String part;
        if (OptionalApi.bool(OptionalApi.call(bladeState, "isBroken"))) {
            part = "blade_damaged";
        } else {
            part = "blade";
        }

        // 定位点定在刀中心，刀朝向前方（默认）
        data.modelState.visitLocatorGroup(PlayerLocator.get().leftWaist, matrixStack, locatorPose -> {
            locatorPose.translate(0, 0.025, -0.6);
            locatorPose.scale(0.01F, 0.01F, 0.01F);
            locatorPose.rotate(Axis.YP.rotationDegrees(-90));
            locatorPose.rotate(Axis.ZP.rotationDegrees(180));

            draw(false, stack, obj, part, texture, locatorPose, bufferIn, lightIn);
            draw(true, stack, obj, part + "_luminous", texture, locatorPose, bufferIn, lightIn);
            draw(false, stack, obj, "sheath", texture, locatorPose, bufferIn, lightIn);
            draw(true, stack, obj, "sheath_luminous", texture, locatorPose, bufferIn, lightIn);
        });

        // 定位点定在刀柄中心，刀朝向前方（默认）
        data.modelState.visitLocatorGroup(PlayerLocator.get().blade, matrixStack, locatorPose -> {
            locatorPose.translate(0, 0.035, 0);
            locatorPose.scale(0.01F, 0.01F, 0.01F);
            locatorPose.rotate(Axis.YP.rotationDegrees(-90));
            locatorPose.rotate(Axis.XP.rotationDegrees(180));

            draw(false, stack, obj, part, texture, locatorPose, bufferIn, lightIn);
            draw(true, stack, obj, part + "_luminous", texture, locatorPose, bufferIn, lightIn);
        });

        // 定位点定在刀鞘最末端，刀朝向前方（默认）
        data.modelState.visitLocatorGroup(PlayerLocator.get().sheath, matrixStack, locatorPose -> {
            locatorPose.translate(0, 0.025, -0.6);
            locatorPose.scale(0.01F, 0.01F, 0.01F);
            locatorPose.rotate(Axis.YP.rotationDegrees(-90));
            locatorPose.rotate(Axis.ZP.rotationDegrees(180));

            draw(false, stack, obj, "sheath", texture, locatorPose, bufferIn, lightIn);
            draw(true, stack, obj, "sheath_luminous", texture, locatorPose, bufferIn, lightIn);
        });
    }

    private static void oldMainhandSlashBlade(LivingEntity livingEntity, GeoRenderData data, PoseStack matrixStack, SubmitNodeCollector bufferIn,
                                              int lightIn, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        withBlade(stack, bladeState -> {
            Identifier texture = ((java.util.Optional<Identifier>) OptionalApi.call(bladeState, "getTexture")).orElse(RESOURCE_DEFAULT_TEXTURE);
            var manager = OptionalApi.callStatic("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager", "getInstance");
        var obj = OptionalApi.call(manager, "getModel", ((java.util.Optional<Identifier>) OptionalApi.call(bladeState, "getModel")).orElse(RESOURCE_DEFAULT_MODEL));
            String part;
            if (OptionalApi.bool(OptionalApi.call(bladeState, "isBroken"))) {
                part = "blade_damaged";
            } else {
                part = "blade";
            }

            // 主手的刀渲染在左边
            data.modelState.visitLocatorGroupOrDefault(PlayerLocator.get().leftWaist, matrixStack, locatorPose -> {
                locatorPose.translate(0, 0, -0.7);
                locatorPose.scale(0.01F, 0.01F, 0.01F);
                locatorPose.rotate(Axis.YP.rotationDegrees(-90));
                locatorPose.rotate(Axis.ZP.rotationDegrees(180));

                draw(false, stack, obj, "sheath", texture, locatorPose, bufferIn, lightIn);
                draw(true, stack, obj, "sheath_luminous", texture, locatorPose, bufferIn, lightIn);
                long time = livingEntity.level().getGameTime() - OptionalApi.integer(OptionalApi.call(bladeState, "getLastActionTime"));
                if (time < 5) {
                    float i = time + data.partialTicks;
                    locatorPose.translate(0, 0, -0.5 / 0.007);
                    locatorPose.rotate(Axis.YP.rotationDegrees(60 + i * 48));
                    locatorPose.rotate(Axis.XP.rotationDegrees(90));
                }
                draw(false, stack, obj, part, texture, locatorPose, bufferIn, lightIn);
                draw(true, stack, obj, part + "_luminous", texture, locatorPose, bufferIn, lightIn);
            }, locatorPose -> {
                locatorPose.translate(-0.25, 1.25, 0);
                locatorPose.rotate(Axis.XP.rotationDegrees(20));
            });
        });
    }

    public static void renderOffhandSlashBlade(GeoRenderData data, PoseStack matrixStack, SubmitNodeCollector bufferIn, int lightIn, ItemStack stack) {
        if (SlashBladeCompat.isSlashBladeItem(stack)) {
            // 副手的刀渲染在右边
            data.modelState.visitLocatorGroupOrDefault(PlayerLocator.get().rightWaist, matrixStack, locatorPose -> {
                locatorPose.translate(0, 0, -0.7);
                locatorPose.scale(0.01F, 0.01F, 0.01F);
                locatorPose.rotate(Axis.YP.rotationDegrees(-90));
                locatorPose.rotate(Axis.ZP.rotationDegrees(180));
                SlashBladeRender.renderSlashBlade(locatorPose, bufferIn, lightIn, stack);
            }, locatorPose -> {
                locatorPose.translate(0.25, 1.25, 0);
                locatorPose.rotate(Axis.XP.rotationDegrees(5));
            });
        }
    }
}

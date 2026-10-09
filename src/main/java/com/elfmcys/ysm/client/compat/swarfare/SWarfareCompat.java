// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.swarfare;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.builder.LoopType;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

public class SWarfareCompat {
    private static final String MOD_ID = "superbwarfare";
    private static boolean INSTALLED = false;

    public static void init() {
        ModFileInfo modFileById = LoadingModList.get().getModFileById(MOD_ID);
        if (modFileById != null) {
            DefaultArtifactVersion modVersion = new DefaultArtifactVersion(modFileById.versionString());
            INSTALLED = modVersion.compareTo(new DefaultArtifactVersion("0.8.7.1")) >= 0;
        }
        if (isInstalled()) {
            NeoForge.EVENT_BUS.register(new ReplacePlayerArmRender());
        }
    }

    public static boolean isGun(ItemStack stack) {
        if (isInstalled()) {
            return SWarfareCompatInner.isGun(stack);
        }
        return false;
    }

    public static boolean shouldHidePlayerRender(Player player) {
        if (isInstalled()) {
            return SWarfareCompatInner.shouldHidePlayerRender(player);
        }
        return false;
    }

    public static boolean renderOffsetHand(ItemStack offhandItem, GeoRenderData data, LivingEntity livingEntity, PoseStack poseStack, SubmitNodeCollector buffer, int packedLight) {
        if (isInstalled() && SWarfareCompatInner.isGun(offhandItem)) {
            poseStack.pushPose();
            try {
                return SWarfareCompatInner.renderOffhandGun(offhandItem, data, livingEntity, poseStack, buffer, packedLight);
            } finally {
                poseStack.popPose();
            }
        }
        return false;
    }

    @Nullable
    public static PlayState playGunMainAnimation(LivingEntity livingEntity,
                                                 AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event,
                                                 String animationName, LoopType loopType) {
        if (isInstalled() && SWarfareCompatInner.isGun(livingEntity.getMainHandItem())) {
            return SWarfareCompatInner.playGunMainAnimation(event, animationName, loopType);
        }
        return null;
    }

    @Nullable
    public static PlayState playGunHoldAnimation(ItemStack mainHandItem,
                                                 AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event) {
        if (isInstalled()) {
            return SWarfareCompatInner.playGunHoldAnimation(event, mainHandItem);
        }
        return null;
    }

    @Nullable
    public static PlayState playGunOnceAnimation(ItemStack mainHandItem, AnimationEvent<? extends CustomHumanoidEntity<? extends LivingEntity>> event) {
        if (isInstalled() && SWarfareCompatInner.isGun(mainHandItem)) {
            return SWarfareCompatInner.playGunOnceAnimation(event, mainHandItem);
        }
        return null;
    }

    @Nullable
    public static Identifier getGunId(ItemStack stack) {
        if (isInstalled()) {
            return RegistryIds.itemId(stack.getItem());
        }
        return null;
    }

    public static boolean isInstalled() {
        return INSTALLED && !com.elfmcys.ysm.client.compat.OptionalApi.isDisabled(MOD_ID);
    }
}

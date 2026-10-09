// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client;

import com.elfmcys.ysm.client.animation.molang.TLMBinding;
import com.elfmcys.ysm.client.compat.OptionalApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.GeoMaidAnimatedRegister;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.MaidControllerCollection;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.molang.TLMBindingInner;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.animation.predicate.MaidVehiclePredicate;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.input.OpenRouletteScreen;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.client.render.CustomYsmMaidRenderer;
import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.model.resource.client.CommonAsset;
import com.elfmcys.ysm.model.resource.client.PlayerModelResources;
import com.elfmcys.ysm.geckolib3.core.PlayState;
import com.elfmcys.ysm.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class TlmClientCompat {
    private static final String MOD_ID = "touhou_little_maid";
    private static final VersionRange VERSION_RANGE;
    private static boolean INSTALLED = false;

    static {
        try {
            VERSION_RANGE = VersionRange.createFromVersionSpec("[1.1.15,)");
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {
        ModList.get().getModContainerById(MOD_ID).ifPresent(modContainer -> {
            ArtifactVersion version = modContainer.getModInfo().getVersion();
            if (VERSION_RANGE.containsVersion(version)) {
                INSTALLED = true;
            } else {
                // 开发环境下，version 是空的，所以需要额外判断
                INSTALLED = !FMLEnvironment.isProduction();
            }
            if (INSTALLED) {
                INSTALLED = TlmClientCompatInner.registerYsmEntityMaidRenderer();
                if (!INSTALLED) return;
                INSTALLED = OptionalApi.query(MaidApi.ID, false, () -> {
                    TlmClientCompatInner.registerEvent();
                    return true;
                });
                if (!INSTALLED) {
                    TlmClientCompatInner.restoreRendererFactory();
                    return;
                }
                // 注册主动画
                GeoMaidAnimatedRegister.registerAnimationState();
            }
        });
    }

    public static Object buildControllerFactory(PlayerModelResources model, CommonAsset assets) {
        if (isInstalled()) {
            return MaidControllerCollection.build(model, assets);
        }
        return null;
    }

    public static boolean isInstalled() {
        return INSTALLED && !OptionalApi.isDisabled(MaidApi.ID);
    }

    public static boolean isMaid(Entity entity) {
        return isInstalled() && TlmClientCompatInner.isMaid(entity);
    }

    public static java.util.Optional<? extends com.elfmcys.ysm.client.entity.CustomEntity<?>> debugTarget(Entity entity) {
        return isInstalled() ? TlmClientCompatInner.debugTarget(entity) : java.util.Optional.empty();
    }

    public static boolean hasMaidCap(Entity entity) {
        return isInstalled() && TlmClientCompatInner.hasMaidCap(entity);
    }

    public static void releaseAnimatable(Entity entity) {
        // Disabling provider calls must not retain already allocated model leases.
        TlmClientCompatInner.releaseAnimatable(entity);
    }

    public static boolean isChair(Entity entity) {
        return isInstalled() && TlmClientCompatInner.isChair(entity);
    }

    public static boolean isSit(Entity entity) {
        return isInstalled() && TlmClientCompatInner.isSit(entity);
    }

    public static boolean isGohei(Item item) {
        return isInstalled() && TlmClientCompatInner.isGohei(item);
    }

    public static String getChairId(Entity entity) {
        return isInstalled() ? TlmClientCompatInner.getChairId(entity) : StringUtils.EMPTY;
    }

    public static boolean isMaidFishing(LivingEntity entity) {
        return isInstalled() && TlmClientCompatInner.maidIsFishing(entity);
    }

    public static void addBinding(TLMBinding binding) {
        if (isInstalled()) {
            TLMBindingInner.addInnerBinding(binding);
        } else {
            addEmptyBinding(binding);
        }
    }

    /**
     * 没有安装此模组时，这些 molang 应该存在，否则会报错
     */
    private static void addEmptyBinding(TLMBinding binding) {
        binding.livingEntityVar("is_begging", ctx -> false);
        binding.livingEntityVar("is_sitting", ctx -> false);
        binding.livingEntityVar("has_backpack", ctx -> false);
        binding.livingEntityVar("favorability_point", ctx -> 0);
        binding.livingEntityVar("favorability_level", ctx -> 0);
        binding.livingEntityVar("task_id", ctx -> StringUtils.EMPTY);
        binding.livingEntityVar("schedule", ctx -> StringUtils.EMPTY);
        binding.livingEntityVar("activity", ctx -> StringUtils.EMPTY);
        binding.livingEntityVar("gomoku_win_count", ctx -> 0);
        binding.livingEntityVar("gomoku_rank", ctx -> 1);
        binding.livingEntityVar("game_statue", ctx -> StringUtils.EMPTY);
        binding.livingEntityVar("backpack_type", ctx -> StringUtils.EMPTY);
        binding.livingEntityVar("is_entity", ctx -> true);
        binding.livingEntityVar("is_statue", ctx -> false);
        binding.livingEntityVar("is_garage_kit", ctx -> false);
        binding.livingEntityVar("show_item", ctx -> StringUtils.EMPTY);
    }

    @Nullable
    public static PlayState getMaidVehicleAnimation(AnimationEvent<CustomHumanoidEntity<?>> event, LivingEntity entity, Entity vehicle) {
        if (isInstalled()) {
            return MaidVehiclePredicate.getMaidVehicleAnimation(event, entity, vehicle);
        }
        return null;
    }

    public static void markTacGunAnimationNeedReload(LivingEntity entity) {
        if (isInstalled()) {
            TlmClientCompatInner.markTacGunAnimationNeedReload(entity);
        }
    }

    public static CustomYsmMaidRenderer getRenderer() {
        if (isInstalled()) {
            return TlmClientCompatInner.getRenderer();
        }
        return null;
    }

    public static boolean pointToMaid() {
        return isInstalled() && OpenRouletteScreen.pointToMaid();
    }

    public static void onRouletteMainKeyPressed() {
        if (isInstalled()) {
            OpenRouletteScreen.onRouletteMainKeyPressed();
        }
    }
}

// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.entity;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.PlayerAnimatableCapability;
import com.elfmcys.ysm.client.animation.condition.FPArmConditionManager;
import com.elfmcys.ysm.client.model.locator.FirstPersonLocator;
import com.elfmcys.ysm.geckolib3.geo.GeoRenderData;
import com.elfmcys.ysm.geckolib3.model.AnimatedGeoBone;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.geckolib3.core.builder.Animation;
import com.elfmcys.ysm.geckolib3.core.builder.controller.AnimationControllerData;
import com.elfmcys.ysm.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.ysm.model.domain.RenderTargetIds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.LinkedHashMap;

public class CustomFirstPersonArmEntity extends CustomEntity<LocalPlayer> {
    private final PlayerAnimatableCapability mainModelEntity;
    private boolean warnedLocatorTypeMismatch;

    public CustomFirstPersonArmEntity(LocalPlayer player, PlayerAnimatableCapability mainModelEntity) {
        super(player, false);
        this.mainModelEntity = mainModelEntity;
        updateModelHash(mainModelEntity.getModelHash());
    }

    @Override
    protected void onSetupAnimationController() {
        getModelRenderTarget().playerResources().fpArmControllerFactory().accept(this);
    }

    public PlayerAnimatableCapability getMainModelEntity() {
        return mainModelEntity;
    }

    /** Extract per-hand visibility after the ordinary, frame-governed animation update. */
    public @Nullable GeoRenderData updateForArm(float partialTicks, HumanoidArm arm) {
        var data = update(partialTicks);
        var model = getLoadedGeoModel();
        if (data == null || model == null) {
            return null;
        }
        var locators = FirstPersonLocator.get();
        if (model.getModel().locatorType() != locators) {
            if (!warnedLocatorTypeMismatch) {
                YesSteveModel.LOGGER.warn("Skipped first-person arm replacement: model {} uses locator type {} instead of {}",
                        model.getModel().identity(), model.getModel().locatorType().id(), locators.id());
                warnedLocatorTypeMismatch = true;
            }
            return null;
        }
        warnedLocatorTypeMismatch = false;
        var selected = arm == HumanoidArm.LEFT ? locators.leftArm : locators.rightArm;
        if (model.locatorGroup(selected).isEmpty()) {
            return null;
        }
        var opposite = arm == HumanoidArm.LEFT ? locators.rightArm : locators.leftArm;
        var visibility = new LinkedHashMap<AnimatedGeoBone, boolean[]>();
        for (var bone : model.locatorGroup(opposite)) {
            visibility.put(bone, new boolean[]{bone.areCubesHidden(), bone.areChildrenHidden()});
        }
        for (var bone : model.locatorGroup(locators.background)) {
            visibility.putIfAbsent(bone, new boolean[]{bone.areCubesHidden(), bone.areChildrenHidden()});
        }
        try {
            // Native extraction respects whole subtrees, including arms below a shared parent.
            // Always extract: the ordinary update may reuse this frame's previous hand output.
            visibility.keySet().forEach(bone -> bone.setHidden(true));
            return data.modelState.extract(model) ? data : null;
        } finally {
            visibility.forEach((bone, hidden) -> bone.setHidden(hidden[0], hidden[1]));
        }
    }

    @Override
    public void checkModelUpdate() {
        if (!Objects.equals(mainModelEntity.getModelHash(), getModelHash())) {
            updateModelHash(mainModelEntity.getModelHash());
            return;
        }
        super.checkModelUpdate();
        var resources = getModelRenderTarget() == null ? null : getModelRenderTarget().playerResources();
        var variant = resources == null ? null : resources.variants().get(mainModelEntity.getTextureName());
        if (variant == null && resources != null) {
            variant = resources.defaultVariant();
        }
        var loadedModel = getLoadedGeoModel();
        if (variant != null && loadedModel != null && loadedModel.getModel() != variant.armModel()) {
            waitForAsyncUpdate();
            setGeoModelInplace(variant.armModel());
        }
    }

    @Override
    protected @Nullable ResourceHolder createResourceHolder(ResourceLease lease, boolean isFallback) {
        return new ResourceHolder(lease, isFallback);
    }

    @Override
    protected String requestedTextureName() {
        return mainModelEntity.getTextureName();
    }

    @Override
    protected String requestedRenderTargetId() {
        return RenderTargetIds.PLAYER;
    }

    @Override
    public @Nullable AnimationControllerData getAnimationControllerData(String animationControllerName) {
        return getModelRenderTarget().playerResources().animationControllers().get(animationControllerName);
    }

    @Override
    public Identifier getTextureLocation() {
        return mainModelEntity.getTextureLocation();
    }

    @Override
    public float getWidthScale() {
        return getModelRenderTarget().info().getPlayerSettings().widthScale();
    }

    @Override
    public float getHeightScale() {
        return getModelRenderTarget().info().getPlayerSettings().heightScale();
    }

    @Override
    public @Nullable Animation getAnimation(String name) {
        return getModelRenderTarget().playerResources().fpArmAnimations().get(name);
    }

    public FPArmConditionManager getFPArmConditionManager() {
        return getModelRenderTarget().playerResources().fpArmConditionManager();
    }

    @Override
    protected GeoModel getYsmGeoModel() {
        var resources = Objects.requireNonNull(getModelRenderTarget().playerResources());
        var variant = resources.variants().get(mainModelEntity.getTextureName());
        return (variant == null ? resources.defaultVariant() : variant).armModel();
    }

    @Override
    protected void preAnimationSetup(float seekTime, boolean shouldTick) {
        // 设置 roaming 变量
        getAnimationProcessor().putRemoteStruct(mainModelEntity.getRoamingStruct());
    }
}

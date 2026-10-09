// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.client;

import com.elfmcys.ysm.client.entity.CustomHumanoidEntity;
import com.elfmcys.ysm.model.resource.client.ResourceLease;
import com.elfmcys.ysm.molang.runtime.Struct;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 基于 CustomPlayerEntity 复制来的，基本上没做删除，试想尝试让女仆能调用轮盘动画之类的,所以就先预留着
 */
@OnlyIn(Dist.CLIENT)
public class CustomYsmMaidEntity extends CustomHumanoidEntity<LivingEntity> {
    private Object maidInfo = OptionalApi.construct(MaidApi.ROOT + "client.resource.pojo.MaidModelInfo");

    public CustomYsmMaidEntity(LivingEntity player, boolean asyncUpdate) {
        super(player, asyncUpdate);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onSetupAnimationController() {
        ((Consumer<CustomYsmMaidEntity>) getModelRenderTarget().playerResources().maidControllerFactory()).accept(this);
    }

    @Override
    protected @NotNull ResourceHolder createResourceHolder(ResourceLease lease, boolean isFallback) {
        return new HumanoidResourceHolder(lease, isFallback);
    }

    @Override
    protected MaidStateTracker createStateTracker(LivingEntity entity) {
        return new MaidStateTracker(entity);
    }

    @Override
    public MaidStateTracker getStateTracker() {
        return (MaidStateTracker) super.getStateTracker();
    }

    public boolean shouldResetRouletteAnim() {
        return OptionalApi.query(MaidApi.ID, false, () -> OptionalApi.bool(OptionalApi.get(entity, "rouletteAnimDirty")));
    }

    public void clearRouletteAnimDirty() {
        OptionalApi.query(MaidApi.ID, null, () -> { OptionalApi.set(entity, "rouletteAnimDirty", false); return null; });
    }

    public boolean isRouletteAnimPlaying() {
        return OptionalApi.query(MaidApi.ID, false, () -> OptionalApi.bool(OptionalApi.get(entity, "rouletteAnimPlaying")));
    }

    public String getRouletteAnim() {
        return OptionalApi.query(MaidApi.ID, "", () -> (String) OptionalApi.get(entity, "rouletteAnim"));
    }

    public void setRemoteStruct(Object2FloatOpenHashMap<String> roamingVars) {
        // TODO
    }

    public void updateRoamingVars(Object2FloatOpenHashMap<String> roamingVars) {
        // TODO
    }

    public Struct getRemoteStruct() {
        // TODO
        return null;
    }

    @Override
    protected void preAnimationSetup(float seekTime, boolean shouldTick) {
        super.preAnimationSetup(seekTime, shouldTick);

        getAnimationProcessor().putRemoteStruct(getRemoteStruct());
    }

    public Object asProviderEntity() {
        var contract = OptionalApi.type(MaidApi.ROOT + "geckolib3.geo.IGeoEntity");
        return java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (self, method, args) -> {
            return switch (method.getName()) {
                case "getMaid" -> getMaid();
                case "getMaidInfo" -> getMaidInfo();
                case "setMaidInfo" -> { setMaidInfo(args[0]); yield null; }
                case "getGeoModel" -> getGeoModel();
                case "setYsmModel" -> { setYsmModel((String) args[0], (String) args[1]); yield null; }
                case "toString" -> "YSM maid animation owner";
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> throw new UnsupportedOperationException(method.toString());
            };
        });
    }

    public LivingEntity getMaid() {
        return this.entity;
    }

    public Object getMaidInfo() {
        return maidInfo;
    }

    public void setMaidInfo(Object maidModelInfo) {
        if (this.maidInfo != maidModelInfo) {
            this.maidInfo = maidModelInfo;
        }
    }

    public Object getGeoModel() {
        // TODO
//        return this.getLoadedGeoModel().getTlmAnimatedGeoModel();
        return null;
    }

    public void setYsmModel(String modelId, String texture) {
        updateModelAndTexture(modelId, texture);
    }
}

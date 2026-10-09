// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.entity;

import com.elfmcys.ysm.client.compat.immersivemelodies.ImmersiveMelodiesCompat;
import com.elfmcys.ysm.geckolib3.model.EntityStateTracker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class HumanoidStateTracker<T extends LivingEntity> extends EntityStateTracker<T> {
    private ImmersiveMelodiesCompat.ImmersiveMelodiesData imData = new ImmersiveMelodiesCompat.ImmersiveMelodiesData();

    private ItemStack mainhandItemStack = ItemStack.EMPTY;
    private ItemStack offhandItemStack = ItemStack.EMPTY;
    @Nullable
    private LivingEntity.SwingDescription lastConsumedSwing;

    public HumanoidStateTracker(T entity) {
        super(entity);
    }

    @Override
    public void reset() {
        mainhandItemStack = ItemStack.EMPTY;
        offhandItemStack = ItemStack.EMPTY;
        lastConsumedSwing = null;
        super.reset();
    }

    @Override
    protected void updateRenderTickData(float currentRenderTick, float lastRenderTick, float partialTicks) {
        super.updateRenderTickData(currentRenderTick, lastRenderTick, partialTicks);
        ImmersiveMelodiesCompat.updateMelodyProgress(this.entity, imData);
    }

    public ItemStack getHandItem(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return mainhandItemStack;
        } else {
            return offhandItemStack;
        }
    }

    public void setHandItem(ItemStack stack, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.mainhandItemStack = stack;
        } else {
            this.offhandItemStack = stack;
        }
    }

    /**
     * Called within the animation owner's barrier. The host creates a new description
     * for every accepted swing start, even when hand, animation and duration are equal.
     * Keep its identity across ticks so repeated render observations cannot reload it.
     */
    public boolean consumeSwingStart(LivingEntity.SwingDescription swing) {
        if (lastConsumedSwing == swing) {
            return false;
        }
        lastConsumedSwing = swing;
        return true;
    }

    public ImmersiveMelodiesCompat.ImmersiveMelodiesData getImmersiveMelodiesData() {
        return imData;
    }
}

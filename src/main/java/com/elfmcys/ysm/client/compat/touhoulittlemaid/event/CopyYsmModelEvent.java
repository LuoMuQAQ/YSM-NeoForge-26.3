// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.touhoulittlemaid.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.ModelInfoCapabilityProvider;
import com.elfmcys.ysm.capability.ModelSelectionService;
import com.elfmcys.ysm.model.service.ServerModelService;
import net.minecraft.world.entity.LivingEntity;
import com.elfmcys.ysm.client.compat.touhoulittlemaid.MaidApi;
import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class CopyYsmModelEvent {
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        OptionalApi.query(MaidApi.ID, null, () -> {
        if (!YesSteveModel.isAvailable()) {
            return null;
        }
        Player player = event.getEntity();

        // 玩家必须是创造模式
        if (!player.isCreative()) {
            return null;
        }

        // 玩家是拿着御币的
        if (!(MaidApi.is("item.ItemHakureiGohei", player.getMainHandItem().getItem()))) {
            return null;
        }

        BlockHitResult result = event.getHitVec();
        BlockPos blockPos = result.getBlockPos();
        BlockEntity te = player.level().getBlockEntity(blockPos);

        if (MaidApi.is("tileentity.TileEntityGarageKit", te)) {
            applyGarageKitData(te, player);
        } else if (MaidApi.is("tileentity.TileEntityStatue", te)) {
            applyStatueData(te, player);
        }
        return null;
        });
    }

    private void applyStatueData(BlockEntity statue, Player player) {
        if (!MaidApi.flag(statue, "isCoreBlock")) {
            return;
        }
        CompoundTag data = (CompoundTag) MaidApi.read(statue, "getExtraMaidData");
        if (data == null) {
            return;
        }
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.parse(data.getStringOr("id", ""))).ifPresent(type -> {
            if (type.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "init.InitEntities", "MAID"), "get"))) {
                applyPlayerInfo(player, data);
                MaidApi.action(statue, "refresh");
            }
        });
    }

    private void applyGarageKitData(BlockEntity kit, Player player) {
        CompoundTag data = (CompoundTag) MaidApi.read(kit, "getExtraData");
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.parse(data.getStringOr("id", ""))).ifPresent(type -> {
            if (type.equals(OptionalApi.call(OptionalApi.getStatic(MaidApi.ROOT + "init.InitEntities", "MAID"), "get"))) {
                applyPlayerInfo(player, data);
                MaidApi.action(kit, "setData", MaidApi.read(kit, "getFacing"), data);
            }
        });
    }

    private void applyPlayerInfo(Player player, CompoundTag compound) {
        ModelInfoCapabilityProvider.get(player).ifPresent(cap -> {
            String modelId = ServerModelService.current().flatMap(ServerModelService::catalog)
                    .map(snapshot -> ModelSelectionService.displayId(cap, snapshot))
                    .orElseGet(() -> cap.getModelId() == null
                            ? "default" : cap.getModelId().toString());
            String texture = cap.getSelectTexture();

            compound.putBoolean(MaidApi.tag("IS_YSM_MODEL_TAG"), true);
            compound.putString(MaidApi.tag("YSM_MODEL_ID_TAG"), modelId);
            compound.putString(MaidApi.tag("YSM_MODEL_TEXTURE_TAG"), texture);
            compound.putInt(MaidApi.tag("YSM_ROAMING_UPDATE_FLAG_TAG"), compound.getIntOr(MaidApi.tag("YSM_ROAMING_UPDATE_FLAG_TAG"), 0) + 1);
            CompoundTag roamingVarsTag = new CompoundTag();
            // TODO: 复制 roaming 变量
            compound.put(MaidApi.tag("YSM_ROAMING_VARS_TAG"), roamingVarsTag);
        });
    }
}

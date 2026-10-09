// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.catalog.snapshot.ServerCatalog;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.MinecraftStateHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/** Retains offline-staged entity ownership until its exact source is admitted. */
public final class LegacyEntityData {
    private CompoundTag data = new CompoundTag();
    private CatalogSnapshot lastSnapshot;

    public CompoundTag serializeNBT() { return data.copy(); }

    public void deserializeNBT(CompoundTag tag) {
        data = tag.copy();
        lastSnapshot = null;
    }

    public static void apply(Entity entity, ServerCatalog catalog) {
        if (entity.level().isClientSide() || entity instanceof Player
                || !entity.hasData(EntityAttachments.LEGACY_ENTITY_DATA)) {
            return;
        }
        entity.getData(EntityAttachments.LEGACY_ENTITY_DATA).applyOncePerCatalog(entity, catalog);
    }

    /** A new authoritative owner supersedes a still-pending legacy slot. */
    public static void cancel(Entity entity, String slot) {
        if (entity.hasData(EntityAttachments.LEGACY_ENTITY_DATA)) {
            var migration = entity.getData(EntityAttachments.LEGACY_ENTITY_DATA);
            var done = migration.data.getCompoundOrEmpty("applied");
            done.putBoolean(slot, true);
            migration.data.put("applied", done);
        }
    }

    private void applyOncePerCatalog(Entity entity, ServerCatalog catalog) {
        if (data.getIntOr("schema", 0) != 1 || lastSnapshot == catalog.catalog()) {
            return;
        }
        lastSnapshot = catalog.catalog();
        var slot = entity instanceof Projectile ? "projectile_model_id" : "vehicle_model_id";
        var done = data.getCompoundOrEmpty("applied");
        data.put("applied", done);
        if (done.getBooleanOr(slot, false)) {
            return;
        }
        var original = data.getCompoundOrEmpty("original");
        if (!original.contains(slot)) {
            return;
        }
        var oldState = original.getCompoundOrEmpty(slot);
        var owner = oldState.getStringOr("owner_model_id", "");
        var resolved = owner.equals("default") ? catalog.defaultModel()
                : LegacyPlayerData.resolveSource(data.getCompoundOrEmpty("paths")
                        .getCompoundOrEmpty(owner), catalog);
        resolved.ifPresent(model -> {
            var restored = oldState.copy();
            restored.remove("owner_model_id");
            restored.putString("owner_model_hash", model.modelId().toString());
            if (entity instanceof Projectile) {
                var cap = entity.getData(EntityAttachments.PROJECTILE_MODEL_INFO);
                cap.deserializeNBT(restored);
                done.putBoolean(slot, true);
                if (cap.isInitialized()) {
                    NetworkHandler.broadcastToVisiblePlayers(
                            MinecraftStateHandler.projectile(entity.getId(), cap), entity);
                }
            } else {
                var cap = entity.getData(EntityAttachments.VEHICLE_MODEL_INFO);
                cap.deserializeNBT(restored);
                done.putBoolean(slot, true);
                if (cap.isInitialized()) {
                    NetworkHandler.broadcastToVisiblePlayers(
                            MinecraftStateHandler.vehicle(entity.getId(), cap), entity);
                }
            }
        });
    }
}

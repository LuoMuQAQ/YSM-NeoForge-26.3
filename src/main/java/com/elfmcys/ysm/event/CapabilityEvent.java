// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.event;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.capability.AuthModelsCapability;
import com.elfmcys.ysm.capability.AuthModelsCapabilityProvider;
import com.elfmcys.ysm.capability.ModelInfoCapability;
import com.elfmcys.ysm.capability.ModelInfoCapabilityProvider;
import com.elfmcys.ysm.capability.ModelInfoSyncAssembler;
import com.elfmcys.ysm.capability.ProjectileModelInfoCapabilityProvider;
import com.elfmcys.ysm.capability.StarModelsCapability;
import com.elfmcys.ysm.capability.StarModelsCapabilityProvider;
import com.elfmcys.ysm.capability.VehicleModelInfoCapabilityProvider;
import com.elfmcys.ysm.config.ServerConfig;
import com.elfmcys.ysm.model.ModelRuntime;
import com.elfmcys.ysm.model.service.ServerModelService;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.MinecraftStateHandler;
import com.elfmcys.ysm.network.forge.PlayerStateHandler;
import com.elfmcys.ysm.network.forge.SessionProtocolHandler;
import com.elfmcys.ysm.network.protocol.StarredModelSnapshots;
import com.elfmcys.ysm.proto.network.PlayerStateUpdate;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@net.neoforged.fml.common.EventBusSubscriber
@SuppressWarnings("removal")
public final class CapabilityEvent {
    @SubscribeEvent
    public static void onTrackingPlayer(PlayerEvent.StartTracking event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getTarget() instanceof ServerPlayer trackPlayer) {
            final Player player = event.getEntity();
            getModelInfoCap(trackPlayer).ifPresent(cap -> {
                if (!NetworkHandler.isPlayerChannelPresent(trackPlayer) && !cap.isMandatory()) {
                    return;
                }
                buildModelInfoPacket(trackPlayer, cap).ifPresent(packet -> {
                    PlayerStateHandler.sendToObserver(packet, player);
                });
            });
        } else if (event.getTarget() instanceof Projectile projectile) {
            ProjectileModelInfoCapabilityProvider.get(projectile).ifPresent(cap -> {
                if (cap.isInitialized()) {
                    NetworkHandler.sendToClientPlayer(
                            MinecraftStateHandler.projectile(projectile.getId(), cap), event.getEntity());
                }
            });
        } else if (event.getTarget() != null) {
            VehicleModelInfoCapabilityProvider.get(event.getTarget()).ifPresent(cap -> {
                if (cap.isInitialized()) {
                    NetworkHandler.sendToClientPlayer(
                            MinecraftStateHandler.vehicle(event.getTarget().getId(), cap), event.getEntity());
                }
            });
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            getModelInfoCap(serverPlayer).ifPresent(modelInfoCap -> {
                if (!NetworkHandler.isPlayerChannelPresent(serverPlayer) && !modelInfoCap.isMandatory()) {
                    modelInfoCap.consumeDirty();
                    return;
                }
                modelInfoCap.stopAnimation(serverPlayer);
                modelInfoCap.consumeDirty();
            });

            getAuthModelsCap(serverPlayer).ifPresent(authModelsCap ->
                    SessionProtocolHandler.refreshGrants(serverPlayer));

            getStarModelsCap(serverPlayer).ifPresent(starModelCap -> {
                NetworkHandler.sendToClientPlayer(
                        StarredModelSnapshots.create(starModelCap.getStarModels()), serverPlayer);
            });
        } else if (!event.getLevel().isClientSide()) {
            ServerModelService.current().flatMap(ServerModelService::catalog).ifPresent(catalog ->
                    com.elfmcys.ysm.capability.LegacyEntityData.apply(event.getEntity(), catalog));
        }
    }

    /**
     * 同步客户端服务端数据
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        var players = event.getServer().getPlayerList().getPlayers();
        var lowBandwidthUsage = ServerConfig.LOW_BANDWIDTH_USAGE.get();
        var migrationCatalog = ServerModelService.current()
                .flatMap(ServerModelService::catalog).orElse(null);
        for (ServerPlayer player : players) {
            if (migrationCatalog != null) {
                com.elfmcys.ysm.capability.LegacyPlayerData.apply(player, migrationCatalog);
            }
            getModelInfoCap(player).ifPresent(cap -> {
                if (!NetworkHandler.isPlayerChannelPresent(player) && !cap.isMandatory()) {
                    cap.consumeDirty();
                    return;
                }
                if (cap.consumeDirty()) {
                    cap.getPropertiesTracker().tick(player, false, lowBandwidthUsage);
                    buildModelInfoPacket(player, cap).ifPresent(packet -> {
                        PlayerStateHandler.broadcast(player, packet);
                        if (player.getVehicle() != null && player.getVehicle().getFirstPassenger() == player) {
                            CapabilityEvent.onVehicleSetModel(player.getVehicle(), player);
                        }
                    });
                } else {
                    cap.getPropertiesTracker().tick(player, true, lowBandwidthUsage);
                }
            });
        }
        ServerModelService.current().ifPresent(service -> {
            if (FMLEnvironment.getDist()
                    == Dist.DEDICATED_SERVER) {
                var system = ModelRuntime.system();
                system.tickCatalog();
                system.tickServerRuntime();
            }
            service.tick();
        });
    }

    public static void onProjectileSetOwner(Projectile projectile, ServerPlayer owner) {
        ModelInfoCapabilityProvider.get(owner).ifPresent(ownerCap -> {
            if (!NetworkHandler.isPlayerChannelPresent(owner) && !ownerCap.isMandatory()) {
                return;
            }
            ProjectileModelInfoCapabilityProvider.get(projectile).ifPresent(cap -> {
                ownerCap.executeWithMolangVars(molangVars -> {
                    cap.init(ownerCap.getModelId(), molangVars);
                    com.elfmcys.ysm.capability.LegacyEntityData.cancel(projectile, "projectile_model_id");
                    NetworkHandler.broadcastToVisiblePlayers(
                            MinecraftStateHandler.projectile(projectile.getId(), cap), projectile);
                });
            });
        });
    }

    public static void onVehicleSetModel(Entity vehicle, ServerPlayer owner) {
        ModelInfoCapabilityProvider.get(owner).ifPresent(ownerCap -> {
            if (!NetworkHandler.isPlayerChannelPresent(owner) && !ownerCap.isMandatory()) {
                return;
            }
            VehicleModelInfoCapabilityProvider.get(vehicle).ifPresent(cap -> {
                // 失败就丢弃
                ownerCap.getMolangVars().ifPresent(molangVars -> {
                    cap.update(ownerCap.getModelId(), molangVars);
                    com.elfmcys.ysm.capability.LegacyEntityData.cancel(vehicle, "vehicle_model_id");
                    NetworkHandler.broadcastToVisiblePlayers(
                            MinecraftStateHandler.vehicle(vehicle.getId(), cap), vehicle);
                });
            });
        });
    }

    private static Optional<ModelInfoCapability> getModelInfoCap(Player player) {
        return ModelInfoCapabilityProvider.get(player);
    }

    private static Optional<PlayerStateUpdate> buildModelInfoPacket(
            ServerPlayer player, ModelInfoCapability capability) {
        return ServerModelService.current().flatMap(ServerModelService::catalog)
                .flatMap(snapshot -> ModelInfoSyncAssembler.build(
                        player, capability, snapshot));
    }

    private static Optional<AuthModelsCapability> getAuthModelsCap(Player player) {
        return AuthModelsCapabilityProvider.get(player);
    }

    private static Optional<StarModelsCapability> getStarModelsCap(Player player) {
        return StarModelsCapabilityProvider.get(player);
    }
}

package com.elfmcys.ysm.capability;

import com.elfmcys.ysm.YesSteveModel;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client animation ownership; projectile/vehicle state is created only after a model update. */
@OnlyIn(Dist.CLIENT)
final class ClientEntityAttachments {
    private ClientEntityAttachments() {}

    private static boolean accepts(Entity entity) {
        return entity != null && entity.level().isClientSide() && YesSteveModel.isAvailable();
    }

    private static State state(Entity entity) {
        return entity.getData(EntityAttachments.CLIENT_RUNTIME)
                .getOrCreate(State.class, () -> new State(entity));
    }

    private static Optional<State> existingState(Entity entity) {
        if (!accepts(entity)) { return Optional.empty(); }
        return entity.getExistingData(EntityAttachments.CLIENT_RUNTIME)
                .flatMap(slot -> slot.existing(State.class));
    }

    static Optional<PlayerAnimatableCapability> player(Entity entity) {
        if (!accepts(entity) || !(entity instanceof AbstractClientPlayer)) { return Optional.empty(); }
        return Optional.of(state(entity).player());
    }

    static Optional<PlayerAnimatableCapability> existingPlayer(Entity entity) {
        return existingState(entity).flatMap(State::existingPlayer);
    }

    static Optional<ProjectileAnimatableCapability> projectile(Entity entity, boolean initialize) {
        if (!accepts(entity) || !(entity instanceof Projectile)) { return Optional.empty(); }
        return initialize ? Optional.of(state(entity).projectile())
                : existingState(entity).flatMap(State::existingProjectile);
    }

    static Optional<VehicleAnimatableCapability> vehicle(Entity entity, boolean initialize) {
        if (!accepts(entity) || entity instanceof Player) { return Optional.empty(); }
        return initialize ? Optional.of(state(entity).vehicle())
                : existingState(entity).flatMap(State::existingVehicle);
    }

    private static final class State {
        private final Entity entity;
        private PlayerAnimatableCapability player;
        private ProjectileAnimatableCapability projectile;
        private VehicleAnimatableCapability vehicle;

        private State(Entity entity) { this.entity = entity; }

        synchronized PlayerAnimatableCapability player() {
            if (player == null) { player = new PlayerAnimatableCapability((AbstractClientPlayer) entity); }
            return player;
        }

        synchronized ProjectileAnimatableCapability projectile() {
            if (projectile == null) { projectile = new ProjectileAnimatableCapability((Projectile) entity); }
            return projectile;
        }

        synchronized VehicleAnimatableCapability vehicle() {
            if (vehicle == null) { vehicle = new VehicleAnimatableCapability(entity); }
            return vehicle;
        }

        synchronized Optional<PlayerAnimatableCapability> existingPlayer() { return Optional.ofNullable(player); }
        synchronized Optional<ProjectileAnimatableCapability> existingProjectile() { return Optional.ofNullable(projectile); }
        synchronized Optional<VehicleAnimatableCapability> existingVehicle() { return Optional.ofNullable(vehicle); }
    }
}

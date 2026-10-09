// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.compat.top;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.compat.OptionalApi;
import java.lang.reflect.Proxy;
import com.elfmcys.ysm.capability.ModelInfoCapabilityProvider;
import com.elfmcys.ysm.capability.VehicleModelInfoCapabilityProvider;
import com.elfmcys.ysm.model.service.ServerModelService;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.proto.mixel.manifest.Manifest;
import com.elfmcys.ysm.proto.mixel.manifest.asset.RenderTargetKind;
import com.elfmcys.ysm.proto.mixel.manifest.info.Metadata;
import com.elfmcys.ysm.util.ModelIdUtil;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public final class TopPlugin implements Function<Object, Void> {
    @Nullable
    @Override
    public Void apply(@Nullable Object probe) {
        if (probe != null) {
            OptionalApi.query("theoneprobe", null, () -> {
                var provider = new YSMProvider();
                var contract = OptionalApi.type("mcjty.theoneprobe.api.IProbeInfoEntityProvider");
                Object proxy = Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (self, method, args) -> {
                    return switch (method.getName()) {
                        case "getID" -> provider.getID();
                        case "addProbeEntityInfo" -> {
                            OptionalApi.query("theoneprobe", null, () -> {
                                provider.addProbeEntityInfo(args[1], (Entity) args[4]);
                                return null;
                            });
                            yield null;
                        }
                        case "toString" -> "YSM TOP entity provider";
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                });
                OptionalApi.call(probe, "registerEntityProvider", proxy);
                return null;
            });
        }
        return null;
    }

    private static class YSMProvider {
        @SuppressWarnings("removal")
        private static final String ID = (Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "model_info")).toString();

        public void addProbeEntityInfo(Object probeInfo, Entity entity) {
            if (entity instanceof ServerPlayer player) {
                ModelInfoCapabilityProvider.get(player).ifPresent(cap -> {
                    if (cap.isMandatory() || NetworkHandler.isPlayerChannelPresent(player)) {
                        var hash = cap.getModelId();
                        if (hash == null) {
                            return;
                        }
                        ServerModelService.instance().catalog().flatMap(snapshot -> snapshot.find(hash)).ifPresent(m -> {
                            var metadata = m.view().getManifest().info().metadata()
                                    .map(Metadata::name).orElse("");
                            textRow(probeInfo, Component.translatable("top.yes_steve_model.model_info.id").append(
                                            StringUtils.defaultIfBlank(metadata, ModelIdUtil.getFileNameFromPath(
                                                    m.location().path().value()))));
                        });
                    }
                });
            } else {
                VehicleModelInfoCapabilityProvider.get(entity).ifPresent(cap -> {
                    if (cap.isInitialized()) {
                        var hash = cap.getOwnerModelHash();
                        if (hash == null) {
                            return;
                        }
                        ServerModelService.instance().catalog().flatMap(snapshot -> snapshot.find(hash))
                                .filter(m -> hasVehicle(m.view().getManifest(), entity))
                                .ifPresent(m -> {
                                    var metadata = m.view().getManifest().info().metadata()
                                            .map(Metadata::name).orElse("");
                                    textRow(probeInfo, Component.translatable("top.yes_steve_model.model_info.id").append(
                                                    StringUtils.defaultIfBlank(metadata, ModelIdUtil.getFileNameFromPath(m.location().path().value()))));
                                });
                    }
                });
            }
        }

        public String getID() {
            return ID;
        }

        private static void textRow(Object probeInfo, Component text) {
            var alignment = OptionalApi.getStatic("mcjty.theoneprobe.api.ElementAlignment", "ALIGN_CENTER");
            var style = OptionalApi.call(OptionalApi.call(probeInfo, "defaultLayoutStyle"), "alignment", alignment);
            var row = OptionalApi.call(probeInfo, "horizontal", style);
            OptionalApi.call(row, "text", text);
        }

        private static boolean hasVehicle(Manifest manifest,
                                          Entity entity) {
            var id = entity.getType().builtInRegistryHolder().key().identifier();
            for (var replacement : manifest.renderTargets()) {
                if (replacement.kind() != RenderTargetKind.RENDER_TARGET_KIND_VEHICLE) {
                    continue;
                }
                if (replacement.match().isEmpty()) {
                    continue;
                }
                for (var match : replacement.match()) {
                    if (id.toString().equals(match)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}

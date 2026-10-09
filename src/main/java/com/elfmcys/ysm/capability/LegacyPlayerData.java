// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import com.elfmcys.ysm.AssetPaths;
import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.catalog.snapshot.ServerCatalog;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.storage.ManagedContainer;
import com.elfmcys.ysm.model.service.ServerModelService;
import com.elfmcys.ysm.model.session.server.state.Selection;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.SessionProtocolHandler;
import com.elfmcys.ysm.network.protocol.StarredModelSnapshots;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** One-way application of a provenance-bound, offline-staged legacy player record. */
public final class LegacyPlayerData {
    private CompoundTag data = new CompoundTag();
    private CatalogSnapshot lastSnapshot;

    public CompoundTag serializeNBT() { return data.copy(); }

    public void deserializeNBT(CompoundTag tag) {
        data = tag.copy();
        lastSnapshot = null;
    }

    public static void apply(ServerPlayer player, ServerCatalog catalog) {
        if (!player.hasData(EntityAttachments.LEGACY_PLAYER_DATA)) {
            return;
        }
        player.getData(EntityAttachments.LEGACY_PLAYER_DATA).applyOncePerCatalog(player, catalog);
    }

    public static void cancelSelection(ServerPlayer player) {
        if (player.hasData(EntityAttachments.LEGACY_PLAYER_DATA)) {
            var migration = player.getData(EntityAttachments.LEGACY_PLAYER_DATA);
            var done = migration.data.getCompoundOrEmpty("applied");
            done.putBoolean("selection", true);
            migration.data.put("applied", done);
        }
    }

    private void applyOncePerCatalog(ServerPlayer player, ServerCatalog catalog) {
        if (data.getIntOr("schema", 0) != 1 || lastSnapshot == catalog.catalog()) {
            return;
        }
        var session = ServerModelService.current().flatMap(service -> service.session(player)).orElse(null);
        if (session == null || !session.active() || !session.hasPublishedCatalog()) {
            return;
        }
        lastSnapshot = catalog.catalog();
        var original = data.getCompoundOrEmpty("original");
        var done = data.getCompoundOrEmpty("applied");
        data.put("applied", done);
        var auth = player.getData(EntityAttachments.AUTH_MODELS);
        var stars = player.getData(EntityAttachments.STAR_MODELS);
        if (applyList(original, done, "own_models", catalog, auth::addModel)) {
            SessionProtocolHandler.refreshGrants(player);
        }
        if (applyList(original, done, "star_models", catalog, stars::addModel)) {
            NetworkHandler.sendToClientPlayer(StarredModelSnapshots.create(stars.getStarModels()), player);
        }
        var selection = original.getCompoundOrEmpty("model_id");
        if (!done.getBooleanOr("selection", false)) {
            var path = selection.getStringOr("model_id", "");
            if (path.isEmpty()) {
                done.putBoolean("selection", true);
            } else {
                resolve(path, catalog).ifPresent(model -> {
                    var texture = selection.getStringOr("select_texture", "");
                    if (texture.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                        texture = texture.substring(0, texture.length() - 4);
                    }
                    if (!SessionProtocolHandler.restoreLegacySelection(player,
                            new Selection.Model(model.modelId(), texture))) {
                        return;
                    }
                    var cap = player.getData(EntityAttachments.MODEL_INFO);
                    var selected = selection.copy();
                    selected.putString("model_hash", model.representation().modelId().toString());
                    // Legacy command selections never had an ignore-grants bit.
                    selected.putBoolean("ignore_grants", false);
                    var variables = selected.getCompoundOrEmpty("molang_storage");
                    var mapping = data.getCompoundOrEmpty("paths").getCompoundOrEmpty(path);
                    var oldKey = mapping.getStringOr("old_roaming_key", "");
                    var newKey = Integer.toString(model.representation().modelId().roamingHash());
                    if (!oldKey.isEmpty() && !oldKey.equals(newKey) && variables.contains(oldKey)) {
                        variables.put(newKey, variables.getCompoundOrEmpty(oldKey).copy());
                    }
                    selected.put("molang_storage", variables);
                    cap.deserializeNBT(selected);
                    cap.markDirty();
                    done.putBoolean("selection", true);
                });
            }
        }
    }

    private boolean applyList(CompoundTag original, CompoundTag done, String key,
                           ServerCatalog catalog, Consumer<Hash256> accept) {
        var applied = done.getCompoundOrEmpty(key);
        done.put(key, applied);
        var before = applied.size();
        for (var tag : original.getListOrEmpty(key)) {
            var path = tag.asString().orElse("");
            if (path.isEmpty() || applied.getBooleanOr(path, false)) {
                continue;
            }
            resolve(path, catalog).ifPresent(model -> {
                accept.accept(model.representation().modelId());
                applied.putBoolean(path, true);
            });
        }
        return applied.size() != before;
    }

    private Optional<ManagedContainer> resolve(String legacyPath, ServerCatalog catalog) {
        if (legacyPath.equals("default")) {
            return catalog.defaultModel();
        }
        var mapping = data.getCompoundOrEmpty("paths").getCompoundOrEmpty(legacyPath);
        return resolveSource(mapping, catalog);
    }

    /** Resolve only an unchanged source already admitted by the current catalog. */
    static Optional<ManagedContainer> resolveSource(CompoundTag mapping, ServerCatalog catalog) {
        var relative = mapping.getStringOr("path", "");
        var expected = mapping.getStringOr("source_sha256", "");
        if (relative.isEmpty() || expected.length() != 64) {
            return Optional.empty();
        }
        try {
            var rootKind = CatalogRootKind.valueOf(mapping.getStringOr("root", "CUSTOM"));
            if (rootKind != CatalogRootKind.CUSTOM && rootKind != CatalogRootKind.AUTH) {
                return Optional.empty();
            }
            var root = (rootKind == CatalogRootKind.CUSTOM ? AssetPaths.customModelsRoot()
                    : AssetPaths.authModelsRoot()).toRealPath();
            var path = root.resolve(new ModelPath(relative).value()).toRealPath();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                return Optional.empty();
            }
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                var bytes = new byte[64 * 1024];
                for (int count; (count = input.read(bytes)) >= 0;) {
                    digest.update(bytes, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) {
                return Optional.empty();
            }
            var known = mapping.getStringOr("verified_model_hash", "");
            return known.isEmpty()
                    ? catalog.find(new CatalogModelLocation(rootKind, new ModelPath(relative)))
                    : catalog.find(Hash256.parse(known));
        } catch (IOException | IllegalArgumentException failure) {
            // An absent or changed source stays pending; the complete old record
            // remains in this registered attachment and can survive another save.
            return Optional.empty();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}

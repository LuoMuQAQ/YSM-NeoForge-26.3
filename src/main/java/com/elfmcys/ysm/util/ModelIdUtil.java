// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import com.elfmcys.ysm.YesSteveModel;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public final class ModelIdUtil {
    private static final Set<String> KNOWN_EXT = Sets.newHashSet(
            ".zip",
            ".7z",
            ".ysm",
            ".mxc"
    );

    /**
     * "dir1/dir2/id" -> {"id", "dir1/dir2/"}
     * <p/>
     * "id" -> {"id", ""}
     */
    public static Pair<String, String> splitModelPath(String modelPath) {
        var lastSlash = modelPath.lastIndexOf('/');
        if (lastSlash == -1) {
            return Pair.of(modelPath, "");
        } else {
            return Pair.of(modelPath.substring(lastSlash + 1), modelPath.substring(0, lastSlash + 1));
        }
    }

    public static String getFileNameFromPath(String modelPath) {
        var lastSlash = modelPath.lastIndexOf('/');
        String modelName;
        if (lastSlash == -1) {
            modelName = modelPath;
        } else {
            modelName = modelPath.substring(lastSlash + 1);
        }
        var lastDot = modelName.lastIndexOf('.');
        if (lastDot < 1 || !KNOWN_EXT.contains(modelName.substring(lastDot).toLowerCase())) {
            return modelName;
        }

        return modelName.substring(0, lastDot);
    }

    public static String getLastFolderName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = trimmed.lastIndexOf('/');
        return idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
    }

    @SuppressWarnings("removal")
    public static Identifier getModelPackIconId(String hierarchy) {
        return Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "model_pack_icon/" + hierarchy.hashCode());
    }

    @SuppressWarnings({"DataFlowIssue", "deprecation"})
    public static Set<Identifier> getEntityIdMatch(String[] matches) {
        var set = new HashSet<Identifier>();
        for (var match : matches) {
            if (match.startsWith("#")) {
                var tagId = Identifier.tryParse(match.substring(1));
                if (tagId == null) {
                    continue;
                }
                var tagKey = RegistryIds.entityTypeTag(tagId);
                BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tagKey).forEach(holder ->
                        holder.unwrapKey().map(ResourceKey::identifier).ifPresent(set::add));
            } else {
                var entityId = Identifier.tryParse(match);
                if (entityId == null) {
                    continue;
                }
                set.add(entityId);
            }
        }
        return set;
    }
}

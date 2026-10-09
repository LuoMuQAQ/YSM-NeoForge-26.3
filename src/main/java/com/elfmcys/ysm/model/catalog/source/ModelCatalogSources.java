// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.model.catalog.source;

import com.elfmcys.ysm.AssetPaths;

import java.nio.file.Path;
import java.util.List;

public final class ModelCatalogSources {
    private ModelCatalogSources() {
    }

    public static List<ModelCatalogSource> sources() {
        var reloadable = reloadableSources();
        return List.of(builtin(), reloadable.get(0), reloadable.get(1));
    }

    public static ModelCatalogSource builtin() {
        return new ModelCatalogSource(CatalogRootKind.BUILTIN,
                AssetPaths.builtinModelsResource(), false);
    }

    public static Path builtinIndex() {
        return AssetPaths.builtinIndexResource();
    }

    public static List<ModelCatalogSource> reloadableSources() {
        return List.of(
                new ModelCatalogSource(CatalogRootKind.CUSTOM, AssetPaths.customModelsRoot(), true),
                new ModelCatalogSource(CatalogRootKind.AUTH, AssetPaths.authModelsRoot(), true));
    }

    public static Path customPath() {
        return AssetPaths.customModelsRoot();
    }
}

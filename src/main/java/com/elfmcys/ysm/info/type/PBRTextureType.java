// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.info.type;

import net.minecraft.resources.Identifier;

public enum PBRTextureType {
    NORMAL("_n"),
    SPECULAR("_s");

    static final PBRTextureType[] VALUES = values();

    private final String suffix;

    PBRTextureType(String suffix) {
        this.suffix = suffix;
    }

    public Identifier getId(Identifier uvId) {
        return Identifier.fromNamespaceAndPath(uvId.getNamespace(), uvId.getPath() + suffix);
    }
}

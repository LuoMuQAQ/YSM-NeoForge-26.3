package com.elfmcys.ysm.client.input;

import com.elfmcys.ysm.YesSteveModel;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class YsmKeyMappings {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "controls"));

    private YsmKeyMappings() {
    }
}

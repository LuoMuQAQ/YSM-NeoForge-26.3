// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.geckolib3.core.molang.builtin.query;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.function.entity.EntityFunction;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;

import net.minecraft.world.level.biome.Biome;

public class BiomeHasAnyTag extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        Entity entity = context.entity().entity();
        Holder<Biome> biome = entity.level().getBiome(entity.blockPosition());

        for (int i = 0; i < arguments.size(); i++) {
            Identifier id = arguments.getAsResourceLocation(context, i);
            if (id == null) {
                return null;
            }
            TagKey<Biome> tag = RegistryIds.biomeTag(id);
            if (biome.is(tag)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 1;
    }
}

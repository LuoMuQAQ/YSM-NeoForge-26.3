// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.molang.functions;

import net.minecraft.core.registries.BuiltInRegistries;
import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.geckolib3.core.molang.context.IContext;
import com.elfmcys.ysm.geckolib3.core.molang.function.entity.EntityFunction;
import com.elfmcys.ysm.geckolib3.util.MolangUtils;
import com.elfmcys.ysm.molang.runtime.ExecutionContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;

public class DumpRelativeBlock extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> ctx, ArgumentCollection arguments) {
        if (!ctx.entity().isDebugEnabled()) {
            return null;
        }

        var block = MolangUtils.getRelativeBlock(ctx, arguments);
        if (block == null) {
            return null;
        }
        Identifier blockId = RegistryIds.blockId(block.getBlock());
        if (blockId == null) {
            return null;
        }
        ctx.entity().debugPrint(Component.literal("Display ").append(ComponentUtils.copyOnClickText(block.getBlock().getName().getString(99))));
        ctx.entity().debugPrint(Component.literal("Name ").append(ComponentUtils.copyOnClickText(blockId.toString())));
        BuiltInRegistries.BLOCK.wrapAsHolder(block.getBlock()).tags().forEach(key -> {
            ctx.entity().debugPrint(Component.literal("Tag ").append(ComponentUtils.copyOnClickText(key.location().toString())));
        });

        return null;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }
}

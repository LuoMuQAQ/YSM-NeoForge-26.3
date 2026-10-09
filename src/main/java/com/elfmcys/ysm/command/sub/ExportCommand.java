package com.elfmcys.ysm.command.sub;

import com.elfmcys.ysm.event.CommandRegistry;
import com.elfmcys.ysm.model.service.ModelExportService;
import com.elfmcys.ysm.util.CommandUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ExportCommand {
    private static final String MODEL_ID = "model_id";
    private static final String EXTRA = "extra";

    private ExportCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        return Commands.literal("export")
                .requires(source -> CommandUtil.hasPermission(source, 2))
                .executes(context -> export(context, null));
//                .then(Commands.argument(MODEL_ID, StringArgumentType.string())
//                        .suggests(CommandRegistry.ALL_MODELS)
//                        .executes(context -> export(context, null))
//                        .then(Commands.argument(EXTRA, StringArgumentType.greedyString())
//                                .executes(context -> export(context,
//                                        StringArgumentType.getString(context, EXTRA)))));
    }

    private static int export(CommandContext<CommandSourceStack> context, String extra) {
        context.getSource().sendFailure(Component.literal("当前模型格式尚未稳定，禁止导出"));
//        var source = context.getSource();
//        var model = StringArgumentType.getString(context, MODEL_ID);
//        ModelExportService.export(model, extra)
//                .whenComplete((output, failure) -> source.getServer().execute(() -> {
//                    if (failure == null) {
//                        source.sendSuccess(() -> Component.translatable(
//                                "commands.yes_steve_model.export.success", output), false);
//                    } else {
//                        var cause = failure.getCause() == null ? failure : failure.getCause();
//                        source.sendFailure(Component.translatable(
//                                "commands.yes_steve_model.export.failure",
//                                Objects.requireNonNullElse(
//                                        cause.getMessage(), cause.getClass().getSimpleName())));
//                    }
//                }));
        return Command.SINGLE_SUCCESS;
    }
}

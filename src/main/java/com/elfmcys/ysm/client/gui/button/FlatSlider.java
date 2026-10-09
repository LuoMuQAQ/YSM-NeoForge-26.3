// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui.button;

import com.elfmcys.ysm.YesSteveModel;
import com.elfmcys.ysm.client.animation.molang.CustomMolangParser;
import com.elfmcys.ysm.config.ServerConfig;
import com.elfmcys.ysm.geckolib3.core.molang.value.IValue;
import com.elfmcys.ysm.geckolib3.model.AnimatableEntity;
import com.elfmcys.ysm.molang.parser.ParseException;
import com.elfmcys.ysm.network.NetworkHandler;
import com.elfmcys.ysm.network.forge.ClientProtocolGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import com.elfmcys.ysm.client.gui.GuiDrawing;

import java.text.DecimalFormat;

@SuppressWarnings("removal")
public class FlatSlider extends ExtendedSlider implements IConfigFormsButton {
    private static final Identifier BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    private final AnimatableEntity<?> animatableEntity;
    private final String molang;
    private boolean refreshingDisplay;

    public FlatSlider(int x, int y, Component prefix, double currentValue, AnimatableEntity<?> animatableEntity, String molang,
                      double step, double min, double max) {
        super(x, y, 115, 15, prefix, Component.empty(), min, max, currentValue, step, 0, true);
        this.animatableEntity = animatableEntity;
        this.molang = molang;
    }

    @Override
    protected void applyValue() {
        if (refreshingDisplay) {
            return;
        }
        try {
            String molangExpress = molang + "=" + getValue();
            IValue parsed = CustomMolangParser.parseSingleExpressionUnsafe(molangExpress);
            this.animatableEntity.executeMolangExp(parsed, true, false, null);
            if (!CustomMolangParser.hasOnlyRoamingAssignment(molangExpress) && NetworkHandler.isRemoteChannelPresent() && !ServerConfig.LOW_BANDWIDTH_USAGE.get()) {
                // 同步到周围的玩家
                ClientProtocolGateway.submitRouletteExpression(this.animatableEntity.getEntity(), molangExpress);
            }
        } catch (ParseException exception) {
            YesSteveModel.LOGGER.error(exception);
        }
    }

    @Override
    public String getValueString() {
        return DECIMAL_FORMAT.format(this.getValue());
    }

    public void setDisplayedValue(double value) {
        // Projection refresh must not write the observed value back to Molang.
        refreshingDisplay = true;
        try {
            this.setValue(value);
        } finally {
            refreshingDisplay = false;
        }
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        GuiDrawing.nineSlice(guiGraphics, BUTTON_TEXTURE, getX(), getY(), width, height, 0,
                isFocused() && !canChangeValue ? 44 : 24, 200, 15, 2, 3, 2, 2);
        GuiDrawing.nineSlice(guiGraphics, BUTTON_TEXTURE, getX() + (int) (value * (width - 8)), getY(), 8, height,
                0, isHoveredOrFocused() ? 44 : 24, 200, 15, 2, 3, 2, 2);
        extractScrollingStringOverContents(guiGraphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE),
                getMessage().copy().withStyle(style -> style.withColor(active ? 0xFFFFFF : 0xA0A0A0)), 2);
    }
}

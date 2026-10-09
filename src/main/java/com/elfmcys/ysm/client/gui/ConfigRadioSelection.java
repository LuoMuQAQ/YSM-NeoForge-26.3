// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.gui;

import com.elfmcys.ysm.geckolib3.core.molang.binding.PrimaryBinding;
import com.elfmcys.ysm.molang.MolangEngine;
import com.elfmcys.ysm.molang.parser.ParseException;
import com.elfmcys.ysm.molang.parser.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Projects a radio's observed value onto its labels without executing label actions. */
final class ConfigRadioSelection {
    private final List<Float> values;

    private ConfigRadioSelection(List<Float> values) {
        this.values = List.copyOf(values);
    }

    static ConfigRadioSelection from(String readSource, List<String> actions) {
        // Parse the read and actions in one binding scope so v/variable aliases share targets.
        var engine = MolangEngine.fromCustomBinding(new PrimaryBinding(Map.of()));
        try {
            var reads = engine.parse(readSource);
            if (reads.size() != 1) {
                return new ConfigRadioSelection(List.of());
            }
            Expression read = reads.getFirst();
            if (read instanceof UnaryExpression unary && unary.op() == UnaryExpression.Op.RETURN) {
                read = unary.expression();
            }
            if (!(read instanceof AssignableVariableExpression || read instanceof StructAccessExpression)) {
                return new ConfigRadioSelection(List.of());
            }
            var values = new ArrayList<Float>(actions.size());
            for (String action : actions) {
                Float assigned = null;
                for (Expression expression : engine.parse(action)) {
                    // Conditional writes, calls and other dynamic actions cannot imply a selection.
                    if (!(expression instanceof BinaryExpression binary)
                            || binary.op() != BinaryExpression.Op.ASSIGN
                            || constant(binary.right()) == null) {
                        return new ConfigRadioSelection(List.of());
                    }
                    if (sameTarget(read, binary.left())) {
                        assigned = constant(binary.right());
                        if (assigned == null) {
                            return new ConfigRadioSelection(List.of());
                        }
                    }
                }
                if (assigned == null) {
                    return new ConfigRadioSelection(List.of());
                }
                values.add(assigned);
            }
            return new ConfigRadioSelection(values);
        } catch (ParseException ignored) {
            // Display projection is optional; execution still uses the full production bindings.
            return new ConfigRadioSelection(List.of());
        }
    }

    int selectedIndex(String result, int preferredIndex) {
        if (values.isEmpty()) {
            return preferredIndex;
        }
        Float observed = AnimationRouletteScreen.transformNumber(result);
        if (observed == null) {
            return -1;
        }
        if (preferredIndex >= 0 && preferredIndex < values.size()
                && values.get(preferredIndex).floatValue() == observed.floatValue()) {
            return preferredIndex;
        }
        int match = -1;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).floatValue() == observed.floatValue()) {
                if (match >= 0) {
                    return -1;
                }
                match = i;
            }
        }
        return match;
    }

    private static boolean sameTarget(Expression left, Expression right) {
        if (left instanceof AssignableVariableExpression a && right instanceof AssignableVariableExpression b) {
            return a.target() == b.target();
        }
        if (left instanceof StructAccessExpression a && right instanceof StructAccessExpression b) {
            return a.path() == b.path() && sameTarget(a.left(), b.left());
        }
        return false;
    }

    private static Float constant(Expression expression) {
        if (expression instanceof FloatExpression literal && Float.isFinite(literal.value())) {
            return literal.value();
        }
        if (expression instanceof UnaryExpression unary
                && unary.op() == UnaryExpression.Op.ARITHMETICAL_NEGATION) {
            Float value = constant(unary.expression());
            return value == null ? null : -value;
        }
        return null;
    }
}

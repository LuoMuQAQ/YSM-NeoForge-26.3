package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.proto.mixel.common.Program;

import java.util.List;

/**
 * Builds source-bearing {@link Program} envelopes for the in-Java Molang interpreter.
 *
 * <p>The wire envelope can also carry engine bytecode, but this project never produces it: every
 * execution field is written as {@code Program{format = 0, source = <Molang text>}}, which is what
 * the native legacy importer emits as well.</p>
 */
final class SourcePrograms {
    /** Statement separators recognised when deciding whether to append a terminating semicolon. */
    private static final String TRAILING_WHITESPACE = " \t\r\n";

    private SourcePrograms() {
    }

    static Program of(String source) {
        return Program.newBuilder()
                .setSource(source == null ? "" : source)
                .build();
    }

    /** Joins statements the same way the native importer does, so both producers agree byte for byte. */
    static Program statements(List<String> sources) {
        var combined = new StringBuilder();
        for (var source : sources) {
            var statement = source == null ? "" : source;
            combined.append(statement);
            var last = lastNonSeparator(statement);
            if (last < 0 || statement.charAt(last) != ';') {
                combined.append(';');
            }
            combined.append('\n');
        }
        return of(combined.toString());
    }

    private static int lastNonSeparator(String statement) {
        for (var index = statement.length() - 1; index >= 0; index--) {
            if (TRAILING_WHITESPACE.indexOf(statement.charAt(index)) < 0) {
                return index;
            }
        }
        return -1;
    }
}

package com.elfmcys.ysm.format.schema.model;

import com.elfmcys.ysm.proto.mixel.common.Program;
import com.elfmcys.ysm.util.ProtoBytes;
import com.elfmcys.ysm.util.ProtoUtil;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import us.hebi.quickbuf.ProtoSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes the {@code mixel.common.Program} oneof tags against the native legacy importer.
 *
 * <p>The native side projects every execution field as {@code Program{format = 0, source = ...}} and
 * its own test asserts the same byte sequences; a change here breaks the shared container wire.</p>
 */
class ProgramEnvelopeWireTest {
    @Test
    void writesSourceOnFieldThreeWithoutFormatOrBytecode() throws Exception {
        var program = Program.newBuilder().setSource("q.life").build();

        assertArrayEquals(HexFormat.of().parseHex("1a06712e6c696665"),
                ProtoUtil.serializeToArray(program));
        assertEquals(0, program.format());
        assertFalse(program.hasBytecode());
        assertTrue(program.hasSource());
    }

    @Test
    void readsTheNativeSourceEnvelopeBack() throws Exception {
        var parsed = Program.parseFrom(ProtoSource.newInstance(
                HexFormat.of().parseHex("1a06712e6c696665")));

        assertEquals("q.life", parsed.source());
        assertFalse(parsed.hasBytecode());
    }

    @Test
    void keepsTheBytecodeTagReservedOnFieldTwo() throws Exception {
        var parsed = Program.parseFrom(ProtoSource.newInstance(
                HexFormat.of().parseHex("0801120200ff")));

        assertEquals(1, parsed.format());
        assertTrue(parsed.hasBytecode());
        assertArrayEquals(new byte[]{0, (byte) 0xff},
                ProtoBytes.copy(parsed.bytecode()));
    }

    @Test
    void defaultEnvelopeIsAnEmptyProgram() throws Exception {
        assertArrayEquals(new byte[0], ProtoUtil.serializeToArray(Program.newBuilder().build()));
        assertFalse(Program.newBuilder().build().hasSource());
        assertFalse(Program.newBuilder().build().hasBytecode());
    }
}

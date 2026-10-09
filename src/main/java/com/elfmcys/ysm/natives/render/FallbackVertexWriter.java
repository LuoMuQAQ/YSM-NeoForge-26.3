// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.natives.render;

import com.elfmcys.ysm.natives.NativeProfiler;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

class FallbackVertexWriter {
    private static final int STRIDE = 8;

    private static final int INDEX_COLOR = 0;
    private static final int INDEX_NORMAL = 1;
    private static final int INDEX_X = 2;
    private static final int INDEX_Y = 3;
    private static final int INDEX_Z = 4;
    private static final int INDEX_TEX_U = 5;
    private static final int INDEX_TEX_V = 6;
    private static final int INDEX_LIGHT = 7;

    private static int[] VERTEX_DATA;

    static int[] getVertexData(int vertexCount) {
        var size = vertexCount * STRIDE;
        if (VERTEX_DATA == null || VERTEX_DATA.length < size) {
            VERTEX_DATA = new int[size];
        }
        return VERTEX_DATA;
    }

    static int[] copyVertexData(int vertexCount) {
        var size = vertexCount * STRIDE;
        var copy = new int[size];
        System.arraycopy(VERTEX_DATA, 0, copy, 0, size);
        return copy;
    }

    static void writeOwned(VertexConsumer vertexBuffer, int[] vertexData, int vertexCount, int overlayUv) {
        writeVertices(vertexBuffer, vertexData, vertexCount, overlayUv);
    }

    static void writeOutline(VertexConsumer consumer, int[] vertexData, int vertexCount, int outlineColor) {
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * STRIDE;
            consumer.addVertex(Float.intBitsToFloat(vertexData[offset + INDEX_X]),
                            Float.intBitsToFloat(vertexData[offset + INDEX_Y]),
                            Float.intBitsToFloat(vertexData[offset + INDEX_Z]))
                    .setColor(outlineColor)
                    .setUv(Float.intBitsToFloat(vertexData[offset + INDEX_TEX_U]),
                            Float.intBitsToFloat(vertexData[offset + INDEX_TEX_V]));
        }
    }

    static void write(VertexConsumer vertexBuffer, int vertexCount, int overlayUv) {
        writeVertices(vertexBuffer, VERTEX_DATA, vertexCount, overlayUv);
    }

    private static void writeVertices(VertexConsumer vertexBuffer, int[] vertexData, int vertexCount, int overlayUv) {
        try (var ignored = NativeProfiler.beginFallbackVertexWrite()) {
            for (int i = 0; i < vertexCount; i++) {
                var offset = i * STRIDE;

                var color = vertexData[offset + INDEX_COLOR];
                var argb = ARGB.color(color >>> 24, color & 0xFF, (color >>> 8) & 0xFF,
                        (color >>> 16) & 0xFF);

                var normal = vertexData[offset + INDEX_NORMAL];
                var normalX = ((byte) ((normal & 0xFF))) / 127f;
                var normalY = ((byte) ((normal >>> 8) & 0xFF)) / 127f;
                var normalZ = ((byte) ((normal >>> 16) & 0xFF)) / 127f;

                vertexBuffer.addVertex(
                        Float.intBitsToFloat(vertexData[offset + INDEX_X]),
                        Float.intBitsToFloat(vertexData[offset + INDEX_Y]),
                        Float.intBitsToFloat(vertexData[offset + INDEX_Z]),
                        argb,
                        Float.intBitsToFloat(vertexData[offset + INDEX_TEX_U]),
                        Float.intBitsToFloat(vertexData[offset + INDEX_TEX_V]),
                        overlayUv,
                        vertexData[offset + INDEX_LIGHT],
                        normalX,
                        normalY,
                        normalZ);
            }
        }
    }
}

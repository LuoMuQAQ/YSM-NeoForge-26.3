package com.elfmcys.ysm.util;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.proto.mixel.asset.model.ModelData;
import com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationFile;
import com.elfmcys.ysm.proto.mixel.asset.model.data.CubeLegacy;
import com.elfmcys.ysm.proto.mixel.manifest.info.Properties;
import com.elfmcys.ysm.proto.network.MetadataPrefixRequest;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuffersImmutableSnapshotTest {
    @Test
    void builderReuseAndWithersDoNotMutateOlderSnapshots() {
        var source = new byte[]{1, 2, 3};
        var builder = MetadataPrefixRequest.newBuilder()
                .setDataTransferId(1)
                .addContainerIds(ByteBuffer.wrap(source));
        var first = builder.build();

        source[0] = 9;
        var second = builder.addContainerIds(ByteBuffer.wrap(new byte[]{4})).build();
        var changedId = first.withDataTransferId(2);
        var rebuilt = first.toBuilder()
                .addContainerIds(ByteBuffer.wrap(new byte[]{5}))
                .build();

        assertEquals(1, first.dataTransferId());
        assertEquals(2, changedId.dataTransferId());
        assertEquals(1, first.containerIds().size());
        assertEquals(2, second.containerIds().size());
        assertEquals(2, rebuilt.containerIds().size());
        assertArrayEquals(new byte[]{1, 2, 3}, ProtoBytes.copy(first.containerIds().get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> first.containerIds().add(ByteBuffer.wrap(new byte[]{6})));
    }

    @Test
    void specializedMapsAreSnapshotIsolatedAndImmutable() {
        var builder = ModelData.newBuilder()
                .putGeoModels("main", ByteBuffer.wrap(new byte[]{1}));
        var first = builder.build();

        var second = builder.putGeoModels("arm", ByteBuffer.wrap(new byte[]{2})).build();

        assertEquals(1, first.geoModels().size());
        assertEquals(2, second.geoModels().size());
        assertThrows(UnsupportedOperationException.class,
                () -> first.geoModels().put("other", ByteBuffer.wrap(new byte[]{3})));
    }

    @Test
    void byteBufferConstructionCopiesTheRemainingRange() throws Exception {
        var backing = new byte[]{9, 1, 2, 3, 8};
        var source = ByteBuffer.wrap(backing);
        source.position(1).limit(4);
        var value = Properties.newBuilder()
                .setModelId(source)
                .setFree(false)
                .setOriginVer("1.0")
                .build();
        var wire = ProtoUtil.serializeToArray(value);

        backing[1] = 7;
        source.position(3);

        var first = value.modelId();
        var second = value.modelId();
        assertTrue(first.isReadOnly());
        assertEquals(0, first.position());
        assertEquals(3, first.limit());
        assertNotSame(first, second);
        first.position(2);
        assertEquals(0, second.position());
        assertArrayEquals(new byte[]{1, 2, 3}, ProtoBytes.copy(second));
        assertArrayEquals(wire, ProtoUtil.serializeToArray(value));
    }

    @Test
    void parseCopiesBytesBeforeTheInputOwnerAndCursorAreReleased() throws Exception {
        var original = Properties.newBuilder()
                .setModelId(ByteBuffer.wrap(new byte[]{1, 2, 3, 4}))
                .setFree(true)
                .setOriginVer("2.0")
                .build();
        var wire = ProtoUtil.serializeToArray(original);
        Properties parsed;
        try (var inputOwner = ArrayBuffer.move(wire)) {
            var cursor = ProtoUtil.source(inputOwner);
            parsed = Properties.parseFrom(cursor);
            cursor.rewindTo(0);
            cursor.setInput(new byte[0]);
        }

        Arrays.fill(wire, (byte) 0);

        assertArrayEquals(new byte[]{1, 2, 3, 4}, ProtoBytes.copy(parsed.modelId()));
        assertEquals("2.0", parsed.originVer());
        assertTrue(parsed.free());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyPublishedCollectionRouteRejectsMutation() {
        var repeated = com.elfmcys.ysm.proto.network.MetadataPrefixRequest
                .newBuilder()
                .setDataTransferId(1)
                .addContainerIds(ByteBuffer.wrap(new byte[]{1}))
                .addContainerIds(ByteBuffer.wrap(new byte[]{2}))
                .build()
                .containerIds();
        assertThrows(UnsupportedOperationException.class,
                () -> repeated.add(ByteBuffer.wrap(new byte[]{3})));
        var repeatedRemoveIterator = repeated.iterator();
        repeatedRemoveIterator.next();
        assertThrows(UnsupportedOperationException.class,
                repeatedRemoveIterator::remove);
        var repeatedIterator = repeated.listIterator();
        repeatedIterator.next();
        assertThrows(UnsupportedOperationException.class,
                () -> repeatedIterator.set(ByteBuffer.wrap(new byte[]{3})));

        var packed = CubeLegacy.newBuilder()
                .setFaceCount(1)
                .addPos(1).addPos(2)
                .build()
                .pos();
        assertThrows(UnsupportedOperationException.class, () -> packed.set(0, 3));
        var packedIterator = packed.iterator();
        packedIterator.nextFloat();
        assertThrows(UnsupportedOperationException.class, packedIterator::remove);

        var model = ModelData.newBuilder()
                .putGeoModels("main", ByteBuffer.wrap(new byte[]{1}))
                .putGeoModels("arm", ByteBuffer.wrap(new byte[]{2}))
                .putAnimationFiles("main",
                        AnimationFile.newBuilder().build())
                .putAnimationFiles("arm",
                        AnimationFile.newBuilder().build())
                .build();
        var bytesMap = model.geoModels();
        assertThrows(UnsupportedOperationException.class,
                () -> bytesMap.put("other", ByteBuffer.wrap(new byte[]{3})));
        var entries = bytesMap.object2ObjectEntrySet();
        var entryIterator = entries.iterator();
        var entry = entryIterator.next();
        assertThrows(UnsupportedOperationException.class,
                () -> entry.setValue(ByteBuffer.wrap(new byte[]{3})));
        assertThrows(UnsupportedOperationException.class, entryIterator::remove);

        var fastEntries = (Object2ObjectMap.FastEntrySet<String, ByteBuffer>) entries;
        var fastEntry = fastEntries.fastIterator().next();
        assertThrows(UnsupportedOperationException.class,
                () -> fastEntry.setValue(ByteBuffer.wrap(new byte[]{4})));

        assertThrows(UnsupportedOperationException.class,
                () -> model.forEachGeoModels(callbackEntry ->
                        callbackEntry.setValue(ByteBuffer.wrap(new byte[]{5}))));
        model.forEachGeoModels(callbackEntry -> assertTrue(callbackEntry.getValue().isReadOnly()));

        var messageEntries = model.animationFiles().object2ObjectEntrySet();
        assertThrows(UnsupportedOperationException.class,
                () -> messageEntries.iterator().next().setValue(
                        AnimationFile.newBuilder().build()));
        assertThrows(UnsupportedOperationException.class,
                () -> model.forEachAnimationFiles(callbackEntry ->
                        callbackEntry.setValue(
                                AnimationFile.newBuilder().build())));

        var firstBytes = bytesMap.get("main");
        var secondBytes = bytesMap.get("main");
        assertTrue(firstBytes.isReadOnly());
        assertNotSame(firstBytes, secondBytes);
        firstBytes.position(firstBytes.limit());
        assertEquals(0, secondBytes.position());
        assertFalse(bytesMap.isEmpty());
    }
}

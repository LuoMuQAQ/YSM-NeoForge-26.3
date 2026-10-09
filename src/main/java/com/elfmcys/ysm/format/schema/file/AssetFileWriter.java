package com.elfmcys.ysm.format.schema.file;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.container.AssetContainerWriter;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.natives.image.Image;
import com.elfmcys.ysm.util.ProtoUtil;
import it.unimi.dsi.fastutil.shorts.ShortReferencePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import us.hebi.quickbuf.ProtoMessage;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AssetFileWriter implements AutoCloseable {
    private String schemaId;
    private String summary = "";
    private final List<ShortReferencePair<String>> properties = new ArrayList<>(4);

    private final List<Triple<String, Integer, UniBuffer>> rawList = new ArrayList<>(4);
    private final List<Pair<String, Image>> images = new ArrayList<>(4);   // 不包含 texture
    private final List<BlobEntry> blobList = new ArrayList<>(4);
    private final List<UniBuffer> streamList = new ArrayList<>(4);
    private final List<StoredEntry> storedList = new ArrayList<>(4);

    protected void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    protected void addProtoChunk(String type, ProtoMessage<?> value, int compressLevel) throws IOException {
        try (var bufScope = ArrayBuffer.allocateWithScope(value.getSerializedSize())) {
            value.writeTo(ProtoUtil.sink(bufScope.get()));
            rawList.add(Triple.of(type, compressLevel, bufScope.release()));
        }
    }

    protected void addRawChunk(String type, UniBuffer value, int compressLevel) {
        rawList.add(Triple.of(type, compressLevel, value.acquire()));
    }

    public void addStoredChunk(AssetContainerView.ChunkInfo chunk, UniBuffer stored) {
        Objects.requireNonNull(chunk, "chunk");
        if (chunk.hash() == null) {
            throw new IllegalArgumentException("Stored chunk has no logical hash");
        }
        storedList.add(new StoredEntry(chunk.type(), chunk.encoding(), chunk.decodeSize(),
                chunk.alignmentShift(), chunk.flags(), stored.acquire(), chunk.hash().clone()));
    }

    public int addProtoBlob(ProtoMessage<?> value, int compressLevel) throws IOException {
        try (var bufScope = ArrayBuffer.allocateWithScope(value.getSerializedSize())) {
            value.writeTo(ProtoUtil.sink(bufScope.get()));
            return addBlob(bufScope.get(), compressLevel);
        }
    }

    public int addBlob(UniBuffer buffer, int compressLevel) {
        blobList.add(new BlobEntry(compressLevel, "", 0, buffer.acquire()));
        return blobList.size();
    }

    public int addImageBlob(Image image) {
        Objects.requireNonNull(image, "image");
        if (image.format() == Image.Format.RGBA) {
            throw new IllegalArgumentException("Image blob must use compressed storage");
        }
        blobList.add(new BlobEntry(0, image.format().name(),
                packImageSize(image), image.data().acquire()));
        return blobList.size();
    }

    public int addStream(UniBuffer buffer) {
        streamList.add(buffer.acquire());
        return streamList.size();
    }

    protected void addImage(String type, Image image) {
        images.add(Pair.of(type, image.share()));
    }

    protected void setProperty(short type, String value) {
        properties.add(ShortReferencePair.of(type, value));
    }

    protected void setSummary(String summary) {
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    public void write(WritableByteChannel output) throws IOException {
        try (var writer = new AssetContainerWriter()) {
            writer.setSchema(schemaId);
            writer.setSummary(summary);
            for (var prop : properties) {
                writer.setSchemaProperty(prop.leftShort(), prop.right());
            }
            for (var proto : rawList) {
                writer.addChunk(proto.getLeft(), "", 0, 0, 0,
                        proto.getRight(), proto.getMiddle());
            }
            for (var stored : storedList) {
                writer.addStoredChunk(stored.type(), stored.encoding(), stored.decodeSize(),
                        stored.alignmentShift(), stored.flags(), stored.data(), stored.hash());
            }
            for (var img : images) {
                writer.addChunk(img.getKey(),
                        img.getValue().format().name(), packImageSize(img.getRight()), 0, 0,
                        img.getValue().data(), 0);
            }
            for (var i = 0; i < blobList.size(); i++) {
                var blob = blobList.get(i);
                writer.addChunk(AssetFileConstant.BLOB_CHUNK_PREFIX + (i + 1),
                        blob.encoding(), blob.decodeSize(), 0, 0,
                        blob.data(), blob.compressLevel());
            }
            for (var i = 0; i < streamList.size(); i++) {
                var buf = streamList.get(i);
                writer.addChunk(AssetFileConstant.STREAM_CHUNK_PREFIX + (i + 1), "", 0, 0, 0,
                        buf, 0);
            }
            writer.write(output);
        }
    }

    private int packImageSize(Image image) {
        return (image.width() << 16) | (image.height() & 0xFFFF);
    }

    @Override
    public void close() {
        for (var pair : rawList) {
            pair.getRight().close();
        }
        for (var blob : blobList) {
            blob.data().close();
        }
        for (var buf : streamList) {
            buf.close();
        }
        for (var stored : storedList) {
            stored.data().close();
        }
        for (var pair : images) {
            pair.getRight().close();
        }
    }

    private record BlobEntry(int compressLevel, String encoding,
                             int decodeSize, UniBuffer data) {
    }

    private record StoredEntry(String type, String encoding, int decodeSize,
                               int alignmentShift, int flags, UniBuffer data,
                               byte[] hash) {
    }
}

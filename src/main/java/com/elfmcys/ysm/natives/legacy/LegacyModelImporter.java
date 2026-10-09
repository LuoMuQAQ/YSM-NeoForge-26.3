package com.elfmcys.ysm.natives.legacy;

import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.format.container.AssetContainerConstant;
import com.elfmcys.ysm.format.container.InlineChunkReader;
import com.elfmcys.ysm.format.media.SupportedAudioProbe;
import com.elfmcys.ysm.format.parser.RawCompileResult;
import com.elfmcys.ysm.format.schema.file.AssetFileConstant;
import com.elfmcys.ysm.format.schema.model.ModelFileConstant;
import com.elfmcys.ysm.format.schema.model.ModelFileView;
import com.elfmcys.ysm.format.schema.model.ModelFileWriter;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.natives.image.Image;
import com.elfmcys.ysm.proto.mixel.asset.model.ModelData;
import com.elfmcys.ysm.proto.mixel.asset.strings.StringData;
import com.elfmcys.ysm.proto.mixel.common.Sound;
import com.elfmcys.ysm.proto.mixel.manifest.Manifest;
import com.elfmcys.ysm.util.ProtoBytes;
import com.elfmcys.ysm.util.ProtoUtil;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Owns native result validation and the mandatory staged legacy representation. */
public final class LegacyModelImporter {
    public RawCompileResult stage(Path source, Path outputDirectory) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final NativeLegacyProtocol.Response response;
        try {
            response = NativeLegacyImporter.invoke(source);
        } catch (NativeLegacyProtocol.ProtocolException failure) {
            throw new LegacyModelImportException(
                    NativeLegacyStatus.RESULT_PROTOCOL, failure.getMessage(), failure);
        }
        try (response) {
            if (response instanceof NativeLegacyProtocol.Failure failure) {
                throw new LegacyModelImportException(
                        failure.status(), failure.diagnostic());
            }
            return stageSuccess(
                    (NativeLegacyProtocol.Success) response, outputDirectory);
        }
    }

    private static RawCompileResult stageSuccess(
            NativeLegacyProtocol.Success success, Path outputDirectory) {
        Path output = null;
        try {
            var descriptor = success.descriptor();
            var records = descriptor.records();
            var payloads = success.payloads();
            var manifest = parseManifest(payloads.get(0));
            var soundDescriptors = soundDescriptors(manifest);
            var soundStreams = new HashSet<Integer>();
            var modelId = new Hash256(descriptor.modelId());
            validateManifestIdentity(manifest, modelId);

            Files.createDirectories(outputDirectory);
            output = outputDirectory.resolve(modelId + ".mxc");
            try (var writer = new ModelFileWriter()) {
                writer.setManifestExact(manifest, payloads.get(0));
                for (var index = 1; index < records.size(); index++) {
                    var record = records.get(index);
                    var payload = payloads.get(index);
                    switch (record.kind()) {
                        case STRING_DATA -> {
                            parseStringData(payload);
                            requireBlobId(writer.addBlob(payload, 16), record);
                        }
                        case MODEL_DATA -> {
                            parseModelData(payload);
                            requireBlobId(writer.addBlob(payload, 16), record);
                        }
                        case BLOB_IMAGE -> {
                            try (var image = image(record, payload)) {
                                requireBlobId(writer.addImageBlob(image), record);
                            }
                        }
                        case NAMED_IMAGE -> {
                            try (var image = image(record, payload)) {
                                if (record.name().equals(
                                        ModelFileConstant.THUMB_BUTTON_CHUNK_NAME)) {
                                    writer.setThumbnail(image);
                                } else {
                                    writer.setIcon(image);
                                }
                            }
                        }
                        case SOUND_STREAM -> {
                            validateSound(record, payload.nio(), soundDescriptors);
                            soundStreams.add(record.logicalId());
                            requireStreamId(writer.addStream(payload), record);
                        }
                        case MANIFEST -> throw new IOException(
                                "Legacy manifest is not the first and only manifest record");
                    }
                }
                if (soundStreams.size() != soundDescriptors.size()) {
                    throw new NativeLegacyProtocol.ProtocolException(
                            "Legacy manifest and sound payloads do not have the same streams");
                }
                try (var channel = FileChannel.open(output,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    writer.write(channel);
                }
            }
            if (Files.size(output) > AssetContainerConstant.MAX_FILE_SIZE) {
                throw new IOException("Legacy staged container exceeds the file limit");
            }
            reopen(output, modelId, success);
            return new RawCompileResult(modelId, output);
        } catch (LegacyModelImportException failure) {
            deleteStaged(output, failure);
            throw failure;
        } catch (NativeLegacyProtocol.ProtocolException failure) {
            var wrapped = new LegacyModelImportException(
                    NativeLegacyStatus.RESULT_PROTOCOL,
                    failure.getMessage(), failure);
            deleteStaged(output, wrapped);
            throw wrapped;
        } catch (IOException | RuntimeException failure) {
            var wrapped = new LegacyModelImportException(
                    NativeLegacyStatus.PUBLICATION_FAILED,
                    "Failed to stage legacy model", failure);
            deleteStaged(output, wrapped);
            throw wrapped;
        }
    }

    private static Manifest parseManifest(
            LegacyPayloadBuffer payload) {
        try (var array = payload.acquireArray()) {
            return Manifest.parseFrom(ProtoUtil.source(array));
        } catch (IOException failure) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy manifest payload is invalid", failure);
        }
    }

    private static void parseStringData(LegacyPayloadBuffer payload) {
        try (var array = payload.acquireArray()) {
            StringData.parseFrom(ProtoUtil.source(array));
        } catch (IOException failure) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy StringData payload is invalid", failure);
        }
    }

    private static void parseModelData(LegacyPayloadBuffer payload) {
        try (var array = payload.acquireArray()) {
            ModelData.parseFrom(ProtoUtil.source(array));
        } catch (IOException failure) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy ModelData payload is invalid", failure);
        }
    }

    private static void validateManifestIdentity(
            Manifest manifest, Hash256 modelId) {
        var properties = manifest.info().properties();
        if (properties.modelId().remaining() != Hash256.SIZE
                || !ProtoBytes.equals(modelId, properties.modelId())) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy manifest identity does not match the descriptor");
        }
    }

    private static Image image(NativeLegacyProtocol.PayloadRecord record,
                               LegacyPayloadBuffer payload) {
        var expected = switch (record.encoding()) {
            case PNG -> Image.Format.PNG;
            case JPEG -> Image.Format.JPEG;
            case WEBP -> Image.Format.WEBP;
            case AVIF -> Image.Format.AVIF;
            case ZTX -> Image.Format.ZTX;
            default -> throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy image uses a non-image encoding");
        };
        try (var bytes = payload.acquire(); var probed = Image.probe(bytes)) {
            if (probed.format() != expected || probed.width() != record.meta0()
                    || probed.height() != record.meta1()) {
                throw new NativeLegacyProtocol.ProtocolException(
                        "Legacy image descriptor does not match its bytes");
            }
            return probed.share();
        } catch (UnsupportedEncodingException failure) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy image payload is invalid", failure);
        }
    }

    private static Map<Integer, Sound> soundDescriptors(
            Manifest manifest) {
        var result = new HashMap<Integer, Sound>();
        for (var sound : manifest.commonAssets().sounds()) {
            if (sound.streamId() == 0 || result.putIfAbsent(sound.streamId(), sound) != null) {
                throw new NativeLegacyProtocol.ProtocolException(
                        "Legacy manifest has duplicate or zero sound stream id");
            }
        }
        return Map.copyOf(result);
    }

    static void validateSound(
            NativeLegacyProtocol.PayloadRecord record, ByteBuffer payload,
            Map<Integer, Sound> descriptors) {
        var sound = descriptors.get(record.logicalId());
        if (sound == null || !sound.name().equals(record.name())) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy sound payload has no matching manifest descriptor");
        }
        final SupportedAudioProbe.Encoding declared;
        try {
            declared = SupportedAudioProbe.Encoding.valueOf(sound.encoding());
        } catch (IllegalArgumentException error) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy sound descriptor has an invalid encoding token", error);
        }
        var expected = record.encoding() == NativeLegacyProtocol.PayloadEncoding.OGG_VORBIS
                ? SupportedAudioProbe.Encoding.OGG_VORBIS
                : SupportedAudioProbe.Encoding.OGG_OPUS;
        if (declared != expected || sound.samples() < 0) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy sound descriptor does not match its payload record");
        }
        var inspection = SupportedAudioProbe.admit(payload, declared, sound.channels(),
                Integer.toUnsignedLong(sound.sampleRate()), sound.samples());
        if (!inspection.playable()) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy sound stream is not valid declared Ogg media: "
                            + inspection.diagnostic());
        }
        var actual = inspection.media().encoding();
        if (actual != expected) {
            throw new NativeLegacyProtocol.ProtocolException(
                    "Legacy sound stream encoding does not match its bytes");
        }
    }

    private static void requireBlobId(
            int actual, NativeLegacyProtocol.PayloadRecord record) throws IOException {
        if (actual != record.logicalId()) {
            throw new IOException("Legacy blob id does not match canonical order");
        }
    }

    private static void reopen(Path output, Hash256 modelId,
                               NativeLegacyProtocol.Success success) throws IOException {
        try (var channel = FileChannel.open(output, StandardOpenOption.READ)) {
            var view = new ModelFileView(channel);
            if (!view.getModelHash().equals(modelId)) {
                throw new IOException("Legacy staged model identity changed");
            }
            var asset = view.getFileView().getAssetView();
            var records = success.descriptor().records();
            var payloads = success.payloads();
            var manifestChunk = asset.getChunkInfo(ModelFileConstant.MANIFEST_CHUNK_NAME);
            try (var stored = InlineChunkReader.readPayload(
                    channel, manifestChunk, BufferType.ARRAY)) {
                if (!equal(stored.nio(), payloads.get(0).nio())) {
                    throw new IOException("Legacy manifest logical bytes changed");
                }
            }
            for (var index = 1; index < records.size(); index++) {
                var record = records.get(index);
                var chunkType = switch (record.kind()) {
                    case STRING_DATA, MODEL_DATA, BLOB_IMAGE ->
                            AssetFileConstant.BLOB_CHUNK_PREFIX
                                    + Integer.toUnsignedLong(record.logicalId());
                    case NAMED_IMAGE -> record.name();
                    case SOUND_STREAM -> AssetFileConstant.STREAM_CHUNK_PREFIX
                            + Integer.toUnsignedLong(record.logicalId());
                    case MANIFEST -> throw new IOException(
                            "Legacy result contains an extra manifest record");
                };
                if (chunkType == null) {
                    continue;
                }
                var chunk = asset.getChunkInfo(chunkType);
                if (chunk == null) {
                    throw new IOException("Legacy staged chunk is missing: " + chunkType);
                }
                if (record.kind() == NativeLegacyProtocol.PayloadKind.SOUND_STREAM) {
                    if (!chunk.encoding().isEmpty() || chunk.decodeSize() != 0
                            || chunk.size() != record.payloadSize()) {
                        throw new IOException("Legacy sound stream storage changed");
                    }
                } else if (record.kind() == NativeLegacyProtocol.PayloadKind.STRING_DATA
                        || record.kind() == NativeLegacyProtocol.PayloadKind.MODEL_DATA) {
                    if (!chunk.encoding().equals("zstd")
                            || chunk.decodeSize() != record.payloadSize()) {
                        throw new IOException(
                                "Legacy protobuf blob storage is not mandatory zstd");
                    }
                } else if (!chunk.encoding().equals(record.encoding().name())
                        || chunk.decodeSize() != packedImageSize(record)
                        || chunk.size() != record.payloadSize()) {
                    throw new IOException("Legacy image storage metadata changed");
                }
                try (var logical = InlineChunkReader.readPayload(
                        channel, chunk, BufferType.ARRAY)) {
                    if (!equal(logical.nio(), payloads.get(index).nio())) {
                        throw new IOException(
                                "Legacy staged logical payload changed: " + chunkType);
                    }
                }
            }
        }
    }

    private static boolean equal(ByteBuffer left, ByteBuffer right) {
        return left.duplicate().equals(right.duplicate());
    }

    private static int packedImageSize(NativeLegacyProtocol.PayloadRecord record) {
        return (record.meta0() << 16) | (record.meta1() & 0xffff);
    }

    private static void requireStreamId(
            int actual, NativeLegacyProtocol.PayloadRecord record) throws IOException {
        if (actual != record.logicalId()) {
            throw new IOException("Legacy stream id does not match canonical order");
        }
    }

    private static void deleteStaged(Path output, Throwable failure) {
        if (output == null) {
            return;
        }
        try {
            Files.deleteIfExists(output);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}

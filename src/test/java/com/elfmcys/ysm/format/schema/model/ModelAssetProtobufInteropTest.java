package com.elfmcys.ysm.format.schema.model;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.buffer.BufferType;
import com.elfmcys.ysm.format.container.AssetContainerReader;
import com.elfmcys.ysm.format.container.InlineChunkReader;
import com.elfmcys.ysm.proto.mixel.asset.model.ModelData;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Animation;
import com.elfmcys.ysm.proto.mixel.asset.model.data.AnimationFile;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Bone;
import com.elfmcys.ysm.proto.mixel.asset.model.data.ExpressionValue;
import com.elfmcys.ysm.proto.mixel.asset.model.data.GeoModel;
import com.elfmcys.ysm.proto.mixel.asset.model.data.GeoProperties;
import com.elfmcys.ysm.proto.mixel.asset.model.data.LoopType;
import com.elfmcys.ysm.proto.mixel.common.Image;
import com.elfmcys.ysm.proto.mixel.common.Program;
import com.elfmcys.ysm.proto.mixel.manifest.Manifest;
import com.elfmcys.ysm.proto.mixel.manifest.asset.Common;
import com.elfmcys.ysm.proto.mixel.manifest.asset.PBRTextureSet;
import com.elfmcys.ysm.proto.mixel.manifest.asset.RenderTarget;
import com.elfmcys.ysm.proto.mixel.manifest.asset.RenderTargetKind;
import com.elfmcys.ysm.proto.mixel.manifest.info.Info;
import com.elfmcys.ysm.proto.mixel.manifest.info.LanguageFile;
import com.elfmcys.ysm.proto.mixel.manifest.info.License;
import com.elfmcys.ysm.proto.mixel.manifest.info.Metadata;
import com.elfmcys.ysm.proto.mixel.manifest.info.ModelSettings;
import com.elfmcys.ysm.proto.mixel.manifest.info.ModelStats;
import com.elfmcys.ysm.proto.mixel.manifest.info.Properties;
import com.elfmcys.ysm.proto.mixel.manifest.info.Settings;
import com.elfmcys.ysm.testutil.ProtobufJavaOracle;
import com.elfmcys.ysm.util.ProtoBytes;
import com.elfmcys.ysm.util.ProtoUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.DynamicMessage;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.hebi.quickbuf.ProtoSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAssetProtobufInteropTest {
    @Test
    void bothCodecsAgreeOnRepresentativeModelValues() throws Exception {
        var manifest = manifest("quickbuf");
        var protobufManifest = ProtobufJavaOracle.parse(
                "mixel.manifest.Manifest", ProtoUtil.serializeToArray(manifest));
        var protobufTarget = repeatedMessage(protobufManifest, "render_targets", 0);
        var protobufTextures = mapMessage(protobufTarget, "textures", "main");
        assertEquals("player", field(protobufTarget, "target_id"));
        assertFalse(protobufTextures.hasField(
                ProtobufJavaOracle.field(protobufTextures.getDescriptorForType(), "normal")));
        assertTrue(protobufTextures.hasField(
                ProtobufJavaOracle.field(protobufTextures.getDescriptorForType(), "specular")));
        assertArrayEquals(hash(), ((ByteString) field(messageField(
                messageField(protobufManifest, "info"), "properties"), "model_id")).toByteArray());

        var modifiedManifest = replaceNestedString(
                protobufManifest, "info", "metadata", "name", "protobuf-java");
        var quickbufManifest = Manifest.parseFrom(
                ProtoSource.newInstance(modifiedManifest.toByteArray()));
        assertEquals("protobuf-java", quickbufManifest.info().metadataUnsafe().name());
        assertFalse(quickbufManifest.renderTargets().get(0)
                .textures().get("main").hasNormal());
        assertTrue(quickbufManifest.renderTargets().get(0)
                .textures().get("main").hasSpecular());
        assertArrayEquals(hash(), ProtoBytes.copy(
                quickbufManifest.info().properties().modelId()));

        var geometry = geometry();
        var protobufGeometry = ProtobufJavaOracle.parse(
                "mixel.asset.model.data.GeoModel", ProtoUtil.serializeToArray(geometry));
        var protobufBone = repeatedMessage(protobufGeometry, "bones", 0);
        assertEquals(3, repeatedCount(protobufBone, "pivot"));
        assertEquals(3, repeatedCount(protobufBone, "rotate"));
        assertTrue(protobufBone.hasField(
                ProtobufJavaOracle.field(protobufBone.getDescriptorForType(), "debug")));
        assertEquals(ByteString.copyFrom(new byte[]{10, 11, 12}), field(protobufGeometry, "cubes"));

        var protobufProperties = messageField(protobufGeometry, "properties");
        var changedProperties = protobufProperties.toBuilder()
                .setField(ProtobufJavaOracle.field(
                        protobufProperties.getDescriptorForType(), "texture_height"),
                        128.0F)
                .build();
        var changedGeometry = protobufGeometry.toBuilder()
                .setField(ProtobufJavaOracle.field(
                        protobufGeometry.getDescriptorForType(), "properties"),
                        changedProperties)
                .build();
        var quickbufGeometry = GeoModel.parseFrom(
                ProtoSource.newInstance(changedGeometry.toByteArray()));
        assertEquals(128.0F, quickbufGeometry.properties().textureHeight());
        assertArrayEquals(new float[]{1, 2, 3},
                quickbufGeometry.bones().get(0).pivot().toFloatArray());
        assertTrue(quickbufGeometry.bones().get(0).hasDebug());
        assertFalse(quickbufGeometry.bones().get(0).debug().orElseThrow());
        assertArrayEquals(new byte[]{10, 11, 12}, ProtoBytes.copy(quickbufGeometry.cubes()));

        var animation = animation("query.speed");
        var protobufAnimation = ProtobufJavaOracle.parse(
                "mixel.asset.model.data.Animation", ProtoUtil.serializeToArray(animation));
        var protobufBlend = messageField(protobufAnimation, "blend_weight");
        assertEquals("program", protobufBlend.getOneofFieldDescriptor(
                protobufBlend.getDescriptorForType().getOneofs().get(0)).getName());
        assertEquals("query.speed", field(messageField(protobufBlend, "program"), "source"));

        var changedProgram = messageField(protobufBlend, "program").toBuilder()
                .setField(ProtobufJavaOracle.field(
                        messageField(protobufBlend, "program").getDescriptorForType(), "source"),
                        "query.modified")
                .build();
        var changedBlend = protobufBlend.toBuilder()
                .setField(ProtobufJavaOracle.field(
                        protobufBlend.getDescriptorForType(), "program"), changedProgram)
                .build();
        var changedAnimation = protobufAnimation.toBuilder()
                .setField(ProtobufJavaOracle.field(
                        protobufAnimation.getDescriptorForType(), "blend_weight"), changedBlend)
                .build();
        var quickbufAnimation = Animation.parseFrom(
                ProtoSource.newInstance(changedAnimation.toByteArray()));
        var quickbufBlend = quickbufAnimation.blendWeight().orElseThrow();
        assertEquals(ExpressionValue.ValueCase.PROGRAM,
                quickbufBlend.value_Case());
        assertEquals("query.modified", quickbufBlend.program().source());

        var modelData = ModelData.newBuilder()
                .putGeoModels("main", ByteBuffer.wrap(new byte[]{1, 2}))
                .putAnimationFiles("main",
                        AnimationFile.newBuilder()
                                .addAnimations(animation)
                                .build())
                .build();
        var protobufModelData = ProtobufJavaOracle.parse(
                "mixel.asset.model.ModelData", ProtoUtil.serializeToArray(modelData));
        assertEquals(ByteString.copyFrom(new byte[]{1, 2}),
                mapScalar(protobufModelData, "geo_models", "main"));
        var geoModels = ProtobufJavaOracle.field(
                protobufModelData.getDescriptorForType(), "geo_models");
        var entry = DynamicMessage.newBuilder(geoModels.getMessageType())
                .setField(ProtobufJavaOracle.field(geoModels.getMessageType(), "key"), "arm")
                .setField(ProtobufJavaOracle.field(geoModels.getMessageType(), "value"),
                        ByteString.copyFrom(new byte[]{3, 4}))
                .build();
        var changedModelData = protobufModelData.toBuilder()
                .addRepeatedField(geoModels, entry)
                .build();
        var quickbufModelData = ModelData.parseFrom(
                ProtoSource.newInstance(changedModelData.toByteArray()));
        assertArrayEquals(new byte[]{3, 4}, ProtoBytes.copy(
                quickbufModelData.geoModels().get("arm")));
        assertEquals("query.speed", quickbufModelData.animationFiles().get("main")
                .animations().get(0).blendWeight().orElseThrow().program().source());
    }

    @Test
    void productionModelFileSeamAcceptsBytesFromBothCodecs(@TempDir Path temp)
            throws Exception {
        var quickbufFile = temp.resolve("quickbuf.ysm");
        writeQuickbufModelFile(quickbufFile, manifest("quickbuf"));
        var quickbufBytes = readManifestBytes(quickbufFile);
        var protobufManifest = ProtobufJavaOracle.parse("mixel.manifest.Manifest", quickbufBytes);
        assertEquals("quickbuf", field(messageField(
                messageField(protobufManifest, "info"), "metadata"), "name"));
        try (var input = FileChannel.open(quickbufFile, StandardOpenOption.READ)) {
            assertEquals("quickbuf", new ModelFileView(input)
                    .getManifest().info().metadataUnsafe().name());
        }

        var protobufBytes = replaceNestedString(
                protobufManifest, "info", "metadata", "name", "protobuf-java")
                .toByteArray();
        var protobufFile = temp.resolve("protobuf-java.ysm");
        writeProtobufModelFile(protobufFile, protobufBytes);
        try (var input = FileChannel.open(protobufFile, StandardOpenOption.READ)) {
            var view = new ModelFileView(input);
            assertEquals("protobuf-java", view.getManifest().info().metadataUnsafe().name());
            assertEquals("player", view.getPlayer().id());
            assertTrue(view.getPlayer().textureDescriptor("main").hasSpecular());
        }
    }

    private static void writeQuickbufModelFile(Path file, Manifest manifest)
            throws Exception {
        try (var writer = new ModelFileWriter();
             var output = FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            writer.setManifest(manifest);
            writer.write(output);
        }
    }

    private static void writeProtobufModelFile(Path file, byte[] protobufBytes)
            throws Exception {
        var manifest = Manifest.parseFrom(
                ProtoSource.newInstance(protobufBytes));
        try (var logicalBytes = ArrayBuffer.move(protobufBytes);
             var writer = new ModelFileWriter();
             var output = FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            writer.setManifestExact(manifest, logicalBytes);
            writer.write(output);
        }
    }

    private static byte[] readManifestBytes(Path file) throws Exception {
        try (var input = FileChannel.open(file, StandardOpenOption.READ)) {
            var container = AssetContainerReader.read(input);
            var chunk = container.getChunkInfo(ModelFileConstant.MANIFEST_CHUNK_NAME);
            try (var bytes = InlineChunkReader.readPayload(input, chunk, BufferType.ARRAY)) {
                return ProtoBytes.copy(bytes.nio());
            }
        }
    }

    private static Manifest manifest(String name) {
        var image = Image.newBuilder()
                .setBlobId(1)
                .setFormat("png")
                .setWidth(16)
                .setHeight(16)
                .setFrameCount(1)
                .build();
        var texture = PBRTextureSet.newBuilder()
                .setUv(image)
                .setSpecular(image.withBlobId(2))
                .build();
        var target = RenderTarget.newBuilder()
                .setTargetId("player")
                .setKind(RenderTargetKind.RENDER_TARGET_KIND_PLAYER)
                .addMatch("minecraft:player")
                .setBlobId(1)
                .putTextures("main", texture)
                .setSettings(ModelSettings.newBuilder()
                        .setHeightScale(1)
                        .setWidthScale(1)
                        .setRenderLayersFirst(true)
                        .setForceCulling(true)
                        .setGuiNoLighting(true)
                        .setMergeMultilineExpr(true)
                        .build())
                .setStats(ModelStats.newBuilder()
                        .setBones(1)
                        .setCubes(1)
                        .setFaces(6)
                        .build())
                .build();
        var info = Info.newBuilder()
                .addLanguageFiles(LanguageFile.newBuilder()
                        .setLocale("en_us")
                        .putEntries("model.name", name)
                        .build())
                .setSettings(Settings.newBuilder()
                        .setDefaultTexture("main")
                        .setPreviewAnimation("idle")
                        .setDisablePreviewRotation(true)
                        .build())
                .setMetadata(Metadata.newBuilder()
                        .setName(name)
                        .setTips("tip")
                        .setLicense(License.newBuilder()
                                .setType("CC0-1.0")
                                .setDesc("public domain")
                                .build())
                        .build())
                .setProperties(Properties.newBuilder()
                        .setModelId(ByteBuffer.wrap(hash()))
                        .setFree(true)
                        .setOriginVer("1.0")
                        .build())
                .build();
        return Manifest.newBuilder()
                .addRenderTargets(target)
                .setCommonAssets(Common.newBuilder()
                        .setStringsBlobId(3)
                        .build())
                .setInfo(info)
                .build();
    }

    private static GeoModel geometry()
            throws Exception {
        return GeoModel.newBuilder()
                .addBones(Bone.newBuilder()
                        .setName("root")
                        .addPivot(1).addPivot(2).addPivot(3)
                        .addRotate(4).addRotate(5).addRotate(6)
                        .setDebug(false)
                        .setCubeCount(1)
                        .build())
                .setProperties(GeoProperties.newBuilder()
                        .setTextureHeight(64)
                        .setTextureWidth(64)
                        .build())
                .setCubes(ByteBuffer.wrap(new byte[]{10, 11, 12}))
                .build();
    }

    private static Animation animation(String blend) {
        return Animation.newBuilder()
                .setName("idle")
                .setLength(1)
                .setLoop(LoopType.LOOP_TYPE_LOOP)
                .setBlendWeight(ExpressionValue.newBuilder()
                        .setProgram(Program.newBuilder()
                                .setSource(blend)
                                .build())
                        .build())
                .build();
    }

    private static byte[] hash() {
        var hash = new byte[32];
        for (var index = 0; index < hash.length; index++) {
            hash[index] = (byte) index;
        }
        return hash;
    }

    private static Object field(DynamicMessage message, String name) {
        return message.getField(ProtobufJavaOracle.field(
                message.getDescriptorForType(), name));
    }

    private static DynamicMessage messageField(DynamicMessage message, String name) {
        return (DynamicMessage) field(message, name);
    }

    private static DynamicMessage repeatedMessage(
            DynamicMessage message, String name, int index) {
        var descriptor = ProtobufJavaOracle.field(message.getDescriptorForType(), name);
        return (DynamicMessage) message.getRepeatedField(descriptor, index);
    }

    private static int repeatedCount(DynamicMessage message, String name) {
        return message.getRepeatedFieldCount(ProtobufJavaOracle.field(
                message.getDescriptorForType(), name));
    }

    private static Object mapScalar(
            DynamicMessage message, String fieldName, String key) {
        var descriptor = ProtobufJavaOracle.field(
                message.getDescriptorForType(), fieldName);
        for (var index = 0; index < message.getRepeatedFieldCount(descriptor); index++) {
            var entry = (DynamicMessage) message.getRepeatedField(descriptor, index);
            if (key.equals(field(entry, "key"))) {
                return field(entry, "value");
            }
        }
        throw new AssertionError("Missing map key: " + key);
    }

    private static DynamicMessage mapMessage(
            DynamicMessage message, String fieldName, String key) {
        return (DynamicMessage) mapScalar(message, fieldName, key);
    }

    private static DynamicMessage replaceNestedString(
            DynamicMessage root,
            String firstField,
            String secondField,
            String stringField,
            String value) {
        var firstDescriptor = ProtobufJavaOracle.field(
                root.getDescriptorForType(), firstField);
        var first = (DynamicMessage) root.getField(firstDescriptor);
        var secondDescriptor = ProtobufJavaOracle.field(
                first.getDescriptorForType(), secondField);
        var second = (DynamicMessage) first.getField(secondDescriptor);
        var changedSecond = second.toBuilder()
                .setField(ProtobufJavaOracle.field(
                        second.getDescriptorForType(), stringField), value)
                .build();
        var changedFirst = first.toBuilder()
                .setField(secondDescriptor, changedSecond)
                .build();
        return root.toBuilder()
                .setField(firstDescriptor, changedFirst)
                .build();
    }
}

package com.elfmcys.ysm.format.parser;

import com.elfmcys.ysm.format.parser.pojo.manifest.ModelManifest;
import com.elfmcys.ysm.format.parser.pojo.manifest.metadata.ModelMetadata;
import com.elfmcys.ysm.format.parser.pojo.manifest.settings.ConfigForms;
import com.elfmcys.ysm.format.parser.pojo.manifest.settings.ExtraAnimationButton;
import com.elfmcys.ysm.format.parser.pojo.manifest.settings.ExtraAnimationClassify;
import com.elfmcys.ysm.format.parser.pojo.manifest.settings.ModelProperties;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.proto.mixel.common.Image;
import com.elfmcys.ysm.proto.mixel.common.StringPair;
import com.elfmcys.ysm.proto.mixel.manifest.info.Author;
import com.elfmcys.ysm.proto.mixel.manifest.info.ConfigLabel;
import com.elfmcys.ysm.proto.mixel.manifest.info.Info;
import com.elfmcys.ysm.proto.mixel.manifest.info.License;
import com.elfmcys.ysm.proto.mixel.manifest.info.Metadata;
import com.elfmcys.ysm.proto.mixel.manifest.info.ModelSettings;
import com.elfmcys.ysm.proto.mixel.manifest.info.Properties;
import com.elfmcys.ysm.proto.mixel.manifest.info.Settings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

final class ModelInfoBuilder {
    private ModelInfoBuilder() {
    }

    static @Nullable Info.Builder buildInfo(
                          boolean buildOutput, ModelManifest sourceManifest,
                          ImageBlobReader imageReader) throws IOException {
        var info = buildOutput ? Info.newBuilder() : null;
        var properties = sourceManifest.properties;

        var settings = info == null ? null : Settings.newBuilder();
        if (info != null) {
            settings.setDefaultTexture(Objects.requireNonNull(properties.defaultTexture, ""))
                    .setPreviewAnimation(Objects.requireNonNull(properties.previewAnimation, ""))
                    .setDisablePreviewRotation(properties.disablePreviewRotation);
            addStringPairs(settings::addExtraAnimation, properties.extraAnimation);
            for (var button : properties.extraAnimationButtons) {
                if (button != null) {
                    settings.addExtraAnimationButtons(toProto(button));
                }
            }
            for (var classify : properties.extraAnimationClassify) {
                if (classify != null) {
                    settings.addExtraAnimationClassify(toProto(classify));
                }
            }
        }

        if (properties.guiForeground != null) {
            imageReader.read(properties.guiForeground, "gui-foreground",
                    RawImageCompressor.GUI_IMAGE, settings == null ? null : settings::setGuiForeground);
        }
        if (properties.guiBackground != null) {
            imageReader.read(properties.guiBackground, "gui-background",
                    RawImageCompressor.GUI_IMAGE, settings == null ? null : settings::setGuiBackground);
        }

        if (sourceManifest.metadata != null) {
            var metadata = info == null ? null : Metadata.newBuilder();
            writeMetadata(metadata, sourceManifest.metadata, imageReader);
            if (metadata != null) {
                info.setMetadata(metadata.build());
            }
        }
        if (info != null) {
            info.setSettings(Objects.requireNonNull(settings, "settings").build());
        }
        return info;
    }

    static void setProperties(Info.Builder info,
                              ModelProperties properties, String originVersion,
                              Hash256 modelHash) {
        info.setProperties(Properties.newBuilder()
                .setOriginVer(originVersion)
                .setFree(properties.free)
                .setModelId(ByteBuffer.wrap(modelHash.bytes()))
                .build());
    }

    static ModelSettings modelSettings(ModelProperties properties) {
        return ModelSettings.newBuilder()
                .setHeightScale(properties.heightScale)
                .setWidthScale(properties.widthScale)
                .setRenderLayersFirst(properties.renderLayersFirst)
                .setForceCulling(properties.forceCulling)
                .setGuiNoLighting(properties.guiNoLighting)
                .setMergeMultilineExpr(properties.mergeMultilineExpr)
                .build();
    }

    private static void writeMetadata(@Nullable Metadata.Builder dst,
                                      ModelMetadata src,
                                      ImageBlobReader imageReader)
            throws IOException {
        if (dst != null) {
            dst.setName(Objects.requireNonNull(src.name, ""))
                    .setTips(Objects.requireNonNull(src.tips, ""));
            dst.setLicense(License.newBuilder()
                    .setType((src.license != null && src.license.type != null) ? src.license.type : "All Rights Reserved")
                    .setDesc((src.license != null && src.license.desc != null) ? src.license.desc : "")
                    .build());
        }
        if (src.authors != null) {
            for (var author : src.authors) {
                if (author == null) {
                    continue;
                }
                var proto = dst == null ? null : Author.newBuilder()
                        .setName(Objects.requireNonNull(author.name, ""))
                        .setRole(Objects.requireNonNull(author.role, ""))
                        .setComment(Objects.requireNonNull(author.comment, ""));
                if (proto != null) {
                    addStringPairs(proto::addContacts, author.contact);
                }
                if (StringUtils.isNotBlank(author.avatar)) {
                   imageReader.read(author.avatar, "avatar",
                           RawImageCompressor.AUTHOR_AVATAR, proto == null ? null : proto::setAvatar);
                }
                if (proto != null) {
                    Objects.requireNonNull(dst, "dst").addAuthors(proto.build());
                }
            }
        }

        if (dst != null) {
            addStringPairs(dst::addLinks, src.link);
        }
    }

    private static com.elfmcys.ysm.proto.mixel.manifest.info.ExtraAnimationButton toProto(ExtraAnimationButton src) {
        var dst = com.elfmcys.ysm.proto.mixel.manifest.info.ExtraAnimationButton.newBuilder()
                .setId(Objects.requireNonNull(src.id, ""))
                .setName(Objects.requireNonNull(src.name, ""))
                .setSound(Objects.requireNonNull(src.sound, ""));
        if (src.configForms != null) {
            for (var form : src.configForms) {
                if (form != null) {
                    dst.addConfigForms(toProto(form));
                }
            }
        }
        return dst.build();
    }

    private static com.elfmcys.ysm.proto.mixel.manifest.info.ConfigForms toProto(ConfigForms src) {
        var source = Objects.requireNonNullElse(src.value, "");
        var actionOnlyRadio = source.isBlank() && "radio".equals(src.type)
                && src.labels != null && !src.labels.isEmpty();
        if (source.isBlank() && !actionOnlyRadio) {
            throw new IllegalArgumentException("Config expression is empty");
        }
        var dst = com.elfmcys.ysm.proto.mixel.manifest.info.ConfigForms.newBuilder()
                .setType(Objects.requireNonNullElse(src.type, ""))
                .setTitle(Objects.requireNonNullElse(src.title, ""))
                .setDescription(Objects.requireNonNullElse(src.description, ""))
                .setReadProgram(SourcePrograms.of(actionOnlyRadio ? "0" : source))
                .setWriteProgram(SourcePrograms.of(actionOnlyRadio ? "return;" : source + "=t.value"))
                .setStep((float) src.step)
                .setMin((float) src.min)
                .setMax((float) src.max);
        if (src.labels != null) {
            var labels = new ArrayList<>(src.labels.entrySet());
            labels.sort(Map.Entry.comparingByKey());
            for (var entry : labels) {
                dst.addLabels(ConfigLabel.newBuilder()
                        .setName(entry.getKey())
                        .setActionProgram(SourcePrograms.of(entry.getValue())).build());
            }
        }
        return dst.build();
    }

    private static com.elfmcys.ysm.proto.mixel.manifest.info.ExtraAnimationClassify toProto(ExtraAnimationClassify src) {
        var dst = com.elfmcys.ysm.proto.mixel.manifest.info.ExtraAnimationClassify.newBuilder()
                .setId(src.id);
        addStringPairs(dst::addExtraAnimation, src.extraAnimation);
        return dst.build();
    }

    private static void addStringPairs(
            Consumer<StringPair> adder,
            @Nullable Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (var entry : values.entrySet()) {
            adder.accept(StringPair.newBuilder()
                    .setKey(entry.getKey())
                    .setValue(entry.getValue())
                    .build());
        }
    }

    @FunctionalInterface
    interface ImageBlobReader {
        void read(String path, String hashType, RawImageCompressor.Policy policy,
                  @Nullable Consumer<Image> consumer) throws IOException;
    }
}

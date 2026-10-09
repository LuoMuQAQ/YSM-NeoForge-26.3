package com.elfmcys.ysm.natives.legacy;

import com.elfmcys.ysm.format.legacy.LegacyYsmHeader;
import com.elfmcys.ysm.format.parser.DefaultAnimationFilter;
import com.elfmcys.ysm.format.parser.RawCompileResult;
import com.elfmcys.ysm.model.catalog.RawModelImporter;
import com.elfmcys.ysm.model.catalog.source.CatalogModelLocation;
import com.elfmcys.ysm.model.catalog.source.CatalogRootIdentity;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.catalog.source.ModelSourceKey;
import com.elfmcys.ysm.model.catalog.source.ModelSourceKind;
import com.elfmcys.ysm.model.catalog.source.ModelSourceResolver;
import com.elfmcys.ysm.model.catalog.source.SourceObservation;
import com.elfmcys.ysm.model.catalog.source.SourceStamp;
import com.elfmcys.ysm.model.domain.ModelPath;
import com.elfmcys.ysm.model.storage.ConvertedObjectStore;
import com.elfmcys.ysm.model.storage.ConvertedSourceIndexStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LegacyModelImporterCorpusTest {
    private static final String NATIVE_MANIFEST_HEADER =
            "path_id_sha256\tsource_sha256\touter_version\tinner_version\t"
                    + "decoded_info_hash\tmodel_id\tstatus_code\tstatus_name\t"
                    + "descriptor_sha256\tpayload_set_sha256\timage_tokens\t"
                    + "reserved_zero\tsound_fields\temitted_sounds\t"
                    + "omitted_sounds\tsound_keyframes\tplayer_targets\t"
                    + "projectile_targets\tvehicle_targets\t"
                    + "textureless_projectiles\ttextureless_vehicles";

    @Test
    void stagesReopensAndPublishesConfiguredCorpus(@TempDir Path temporary)
            throws Exception {
        var configured = System.getenv("YSM_LEGACY_CORPUS");
        assumeTrue(configured != null && !configured.isBlank());
        var root = Path.of(configured);
        assumeTrue(Files.isDirectory(root));

        final List<Path> sources;
        try (var files = Files.walk(root)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".ysm"))
                    .sorted()
                    .toList();
        }
        var importer = new LegacyModelImporter();
        var objects = new ConvertedObjectStore(temporary.resolve("cache"));
        var routeCacheRoot = temporary.resolve("route-cache");
        var resolver = new ModelSourceResolver(
                new RawModelImporter(DefaultAnimationFilter.keepAll()),
                new ConvertedSourceIndexStore(routeCacheRoot, "test-version"),
                new ConvertedObjectStore(routeCacheRoot));
        var staging = temporary.resolve("staging");
        var nativeManifest = readNativeManifest();
        var finalManifest = System.getenv("YSM_LEGACY_FINAL_MANIFEST");
        if (finalManifest != null && !finalManifest.isBlank()) {
            assertNotNull(nativeManifest,
                    "YSM_LEGACY_NATIVE_MANIFEST is required for final evidence");
        }
        var evidence = finalManifest == null || finalManifest.isBlank()
                ? null
                : new ArrayList<String>();
        if (evidence != null) {
            evidence.add(NATIVE_MANIFEST_HEADER
                    + "\tjava_path\tjava_write\tproduction_reopen\tpublication"
                    + "\tcontainer_sha256\tcontainer_size\twarning_count");
        }
        var converted = 0;
        var routedRaw = 0;
        Path routingSample = null;
        for (var source : sources) {
            var nativeRow = nativeManifest == null
                    ? null
                    : nativeManifest.remove(pathId(root, source));
            if (nativeManifest != null) {
                assertNotNull(nativeRow, "native evidence row for " + source);
                assertEquals(nativeRow.sourceSha256(), sha256(source),
                        "native evidence source digest for " + source);
            }
            var version = LegacyYsmHeader.probe(source);
            if (version == LegacyYsmHeader.Version.V1_RAW
                    || version == LegacyYsmHeader.Version.V2_RAW) {
                routedRaw++;
                final ModelSourceResolver.Resolution routed;
                try {
                    routed = resolver.resolve(observation(
                            root, source, "raw-adapter-" + routedRaw));
                } catch (RuntimeException failure) {
                    throw new AssertionError("Failed raw corpus source: " + source, failure);
                }
                assertTrue(routed.convertedIndexEntry().isPresent());
                assertTrue(Files.isRegularFile(routed.entry().backingFile()));
                appendEvidence(evidence, nativeRow, "raw-adapter",
                        routed.entry().backingFile(), routed.diagnostics().size());
                continue;
            }
            assertEquals(LegacyYsmHeader.Version.V3_ENCRYPTED, version);
            if (routingSample == null) {
                routingSample = source;
            }
            var ordinal = ++converted;
            final RawCompileResult compiled;
            try {
                compiled = importer.stage(source, staging);
            } catch (RuntimeException failure) {
                throw new AssertionError("Failed legacy corpus source: " + source, failure);
            }
            var identity = objects.commit(compiled,
                    new CatalogModelLocation(CatalogRootKind.CUSTOM,
                            new ModelPath("corpus/" + ordinal)));
            assertEquals(compiled.modelHash(), identity.modelId());
            assertTrue(Files.isRegularFile(objects.objectPath(
                    identity.modelId(), identity.containerId())));
            appendEvidence(evidence, nativeRow, "legacy-v3",
                    objects.objectPath(identity.modelId(), identity.containerId()),
                    compiled.diagnostics().size());
            Files.deleteIfExists(compiled.stagedContainer());
        }

        var sample = routingSample;
        assertTrue(sample != null);
        var routed = resolver.resolve(observation(root, sample, "routing-sample"));
        assertTrue(routed.convertedIndexEntry().isPresent());
        assertTrue(routed.diagnostics().isEmpty());

        assertEquals(197, sources.size());
        assertEquals(12, routedRaw);
        assertEquals(185, converted);
        if (nativeManifest != null) {
            assertTrue(nativeManifest.isEmpty(), "unmatched native evidence rows");
        }
        if (evidence != null) {
            var output = Path.of(finalManifest).toAbsolutePath().normalize();
            Files.createDirectories(output.getParent());
            Files.write(output, evidence, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }
    }

    private static SourceObservation observation(Path root, Path source,
                                                 String name) throws Exception {
        var rootIdentity = new CatalogRootIdentity(
                CatalogRootKind.CUSTOM, root, "");
        var stamp = new SourceStamp.File(Files.size(source),
                Files.getLastModifiedTime(source).toMillis(), "");
        var sourceKind = switch (LegacyYsmHeader.probe(source)) {
            case V1_RAW, V2_RAW -> ModelSourceKind.CURRENT_RAW_ARCHIVE;
            case V3_ENCRYPTED -> ModelSourceKind.LEGACY_ARCHIVE;
            case UNSUPPORTED -> ModelSourceKind.UNSUPPORTED_YSM;
        };
        return new SourceObservation(new ModelSourceKey(rootIdentity,
                new ModelPath("corpus/" + name + ".ysm"),
                sourceKind), source, stamp, Files.size(source));
    }

    private static Map<String, NativeEvidenceRow> readNativeManifest()
            throws Exception {
        var configured = System.getenv("YSM_LEGACY_NATIVE_MANIFEST");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        var lines = Files.readAllLines(Path.of(configured), StandardCharsets.UTF_8);
        assertEquals(NATIVE_MANIFEST_HEADER, lines.get(0));
        var result = new LinkedHashMap<String, NativeEvidenceRow>();
        for (var line : lines.subList(1, lines.size())) {
            var columns = line.split("\t", -1);
            assertEquals(21, columns.length, "native evidence column count");
            assertNull(result.put(columns[0],
                    new NativeEvidenceRow(line, columns[1])),
                    "duplicate native evidence path id");
        }
        return result;
    }

    private static void appendEvidence(ArrayList<String> evidence,
                                       NativeEvidenceRow nativeRow,
                                       String javaPath, Path container,
                                       int warningCount) throws Exception {
        if (evidence == null) {
            return;
        }
        evidence.add(nativeRow.line() + '\t' + javaPath
                + "\ttrue\ttrue\ttrue\t" + sha256(container) + '\t'
                + Files.size(container) + '\t' + warningCount);
    }

    private static String pathId(Path root, Path source) throws Exception {
        var relative = root.relativize(source).toString().replace('\\', '/');
        return sha256(relative.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(Path file) throws Exception {
        try (var input = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) != -1; ) {
                digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record NativeEvidenceRow(String line, String sourceSha256) {
    }
}

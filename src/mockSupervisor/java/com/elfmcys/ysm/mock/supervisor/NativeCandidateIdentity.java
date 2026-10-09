package com.elfmcys.ysm.mock.supervisor;

import com.elfmcys.ysm.mock.evidence.EvidenceJson;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable native source/binary identity consumed by verification-only entry points. */
record NativeCandidateIdentity(String revision, String runtimeLibrarySha256,
                               String manifestPath, String manifestSha256,
                               String provenancePath, String provenanceSha256) {
    private static final String SCHEMA = "MMR-NATIVE-CANDIDATE-1";
    private static final String MANIFEST = "implement/native-candidate.json";

    static NativeCandidateIdentity load(Path taskRoot) throws IOException {
        var normalizedTaskRoot = taskRoot.toAbsolutePath().normalize();
        var manifest = normalizedTaskRoot.resolve(MANIFEST).normalize();
        require(manifest.startsWith(normalizedTaskRoot) && Files.isRegularFile(manifest),
                "Native candidate manifest is missing: " + manifest);
        var bytes = Files.readAllBytes(manifest);
        var source = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
        require(SCHEMA.equals(source.get("schema").getAsString()),
                "Native candidate manifest schema mismatch");
        var revision = source.get("nativeRevision").getAsString();
        var librarySha256 = source.get("runtimeLibrarySha256").getAsString();
        require(revision.matches("[0-9a-f]{40}"),
                "Native candidate revision must be a full commit");
        require(librarySha256.matches("[0-9a-f]{64}"),
                "Native runtime library hash must be SHA-256");

        var provenance = source.getAsJsonObject("provenance");
        var provenanceRelative = Path.of(provenance.get("artifact").getAsString());
        require(!provenanceRelative.isAbsolute(),
                "Native candidate provenance path must be task-relative");
        var provenancePath = normalizedTaskRoot.resolve(provenanceRelative).normalize();
        require(provenancePath.startsWith(normalizedTaskRoot)
                        && Files.isRegularFile(provenancePath),
                "Native candidate provenance artifact is missing or escaped");
        var expectedProvenanceHash = provenance.get("sha256").getAsString();
        var provenanceBytes = Files.readAllBytes(provenancePath);
        require(expectedProvenanceHash.equals(EvidenceJson.sha256(provenanceBytes)),
                "Native candidate provenance hash mismatch");
        var provenanceJson = JsonParser.parseString(
                new String(provenanceBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        require(revision.equals(provenanceJson.getAsJsonObject("authorityRevisions")
                        .get("native").getAsString()),
                "Native revision is not bound by the provenance artifact");
        require(librarySha256.equals(provenanceJson.getAsJsonObject("configuration")
                        .get("nativeLibrarySha256").getAsString()),
                "Native runtime hash is not bound by the provenance artifact");
        return new NativeCandidateIdentity(revision, librarySha256,
                normalize(manifest), EvidenceJson.sha256(bytes),
                normalize(provenancePath), expectedProvenanceHash);
    }

    void verifyRuntimeLibrary(Path runtimeLibrary) throws IOException {
        require(Files.isRegularFile(runtimeLibrary),
                "Native runtime library is missing: " + runtimeLibrary);
        require(runtimeLibrarySha256.equals(
                        EvidenceJson.sha256(Files.readAllBytes(runtimeLibrary))),
                "Native runtime library does not match the frozen candidate manifest");
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}

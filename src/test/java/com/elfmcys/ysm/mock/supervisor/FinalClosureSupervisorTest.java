package com.elfmcys.ysm.mock.supervisor;

import com.elfmcys.ysm.mock.evidence.EvidenceJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalClosureSupervisorTest {
    private static final String JAVA_REVISION = "a".repeat(40);
    private static final String NATIVE_REVISION = "b".repeat(40);

    @Test
    void docsVerdictCoversEveryContractBranch() {
        assertEquals("Pass", FinalClosureSupervisor.docsVerdict(
                true, true, List.of(), "NotRequired"));
        assertEquals("Pass", FinalClosureSupervisor.docsVerdict(
                false, true, List.of(), "UserAttributed"));
        assertEquals("Unreviewable", FinalClosureSupervisor.docsVerdict(
                false, true, List.of(), "Missing"));
        assertEquals("Fail", FinalClosureSupervisor.docsVerdict(
                true, false, List.of(), "NotRequired"));
        assertEquals("Fail", FinalClosureSupervisor.docsVerdict(
                false, true, List.of(), "TaskOwned"));

        for (var consumer : List.of("production", "test", "Contract", "acceptance")) {
            var match = new FinalClosureSupervisor.ForbiddenDependencyMatch(
                    consumer, consumer + "/consumer.txt");
            assertEquals("Fail", FinalClosureSupervisor.docsVerdict(
                    true, true, List.of(match), "NotRequired"), consumer);
        }
    }

    @Test
    void missingCandidateIsValidButIncompleteCandidateFails(@TempDir Path root)
            throws Exception {
        var absent = FinalClosureSupervisor.auditDocumentationCandidate(root);
        assertTrue(absent.complete());
        assertTrue(absent.inventory().isEmpty());

        var candidate = root.resolve("implement").resolve(candidateName());
        Files.createDirectories(candidate.getParent());
        Files.writeString(candidate, "# incomplete", StandardCharsets.UTF_8);
        var incomplete = FinalClosureSupervisor.auditDocumentationCandidate(root);
        assertFalse(incomplete.complete());
        assertEquals(1, incomplete.inventory().size());
    }

    @Test
    void dependencySearchUsesOnlyTheFourForbiddenConsumerScopes(@TempDir Path root)
            throws Exception {
        var project = root.resolve("project");
        var task = root.resolve("task");
        var reference = candidateName();
        write(project.resolve("src/main/java/Product.java"), reference);
        write(project.resolve("src/test/java/ProductTest.java"), reference);
        write(task.resolve("contract/obligations.md"), reference);
        write(task.resolve("contract/acceptance.md"), reference);
        write(project.resolve("src/mockSupervisor/java/Verifier.java"), reference);

        var matches = FinalClosureSupervisor.findForbiddenCandidateDependencies(
                task, project);
        assertEquals(Set.of("production", "test", "Contract", "acceptance"),
                matches.stream().map(
                        FinalClosureSupervisor.ForbiddenDependencyMatch::consumer)
                        .collect(Collectors.toSet()));
        assertEquals(4, matches.size());
    }

    @Test
    void userAttributionMustBindExactPathAndFinalHash(@TempDir Path taskRoot)
            throws Exception {
        var baselineHead = "1".repeat(40);
        var currentHead = "2".repeat(40);
        var beforeHash = "5".repeat(64);
        var afterHash = "6".repeat(64);
        var baselineTree = treeHash(List.of(
                new FinalClosureSupervisor.ContentEntry("guide.md", beforeHash)));
        var currentTree = treeHash(List.of(
                new FinalClosureSupervisor.ContentEntry("guide.md", afterHash)));
        var manifest = new FinalClosureSupervisor.ContentManifest(
                List.of(new FinalClosureSupervisor.ContentEntry("guide.md", afterHash)),
                currentTree);
        var changes = List.of(new FinalClosureSupervisor.DocsDelta(
                "guide.md", beforeHash, afterHash));
        var attribution = Map.of(
                "schema", "MMR-DOCS-ATTRIBUTION-1",
                "event", Map.of(
                        "id", "user-docs-commit-1",
                        "actor", "user",
                        "occurredAt", "2026-09-07T00:00:00Z"),
                "baseline", Map.of(
                        "head", baselineHead,
                        "regularFiles", 1,
                        "treeSha256", baselineTree),
                "final", Map.of(
                        "head", currentHead,
                        "regularFiles", 1,
                        "treeSha256", currentTree,
                        "status", List.of()),
                "changes", List.of(Map.of(
                        "path", "guide.md",
                        "beforeSha256", beforeHash,
                        "afterSha256", afterHash)));
        var attributionPath = taskRoot.resolve("implement/docs-new-attribution.json");
        write(attributionPath, EvidenceJson.canonicalBytes(attribution));

        var valid = FinalClosureSupervisor.auditDocsAttribution(
                taskRoot, baselineHead, 1, baselineTree, currentHead,
                manifest, List.of(), changes, false);
        assertEquals("UserAttributed", valid.status());

        var mutated = new LinkedHashMap<>(attribution);
        mutated.put("changes", List.of(Map.of(
                "path", "guide.md",
                "beforeSha256", beforeHash,
                "afterSha256", "7".repeat(64))));
        Files.write(attributionPath, EvidenceJson.canonicalBytes(mutated));
        var invalid = FinalClosureSupervisor.auditDocsAttribution(
                taskRoot, baselineHead, 1, baselineTree, currentHead,
                manifest, List.of(), changes, false);
        assertEquals("Invalid", invalid.status());
    }

    @Test
    void userAttributionMustBindBeforeHashToFrozenTree(@TempDir Path taskRoot)
            throws Exception {
        var baselineHead = "1".repeat(40);
        var currentHead = "2".repeat(40);
        var beforeHash = "3".repeat(64);
        var wrongBeforeHash = "4".repeat(64);
        var afterHash = "5".repeat(64);
        var baselineTree = EvidenceJson.sha256(
                (beforeHash + "  guide.md\n").getBytes(StandardCharsets.UTF_8));
        var currentTree = EvidenceJson.sha256(
                (afterHash + "  guide.md\n").getBytes(StandardCharsets.UTF_8));
        var manifest = new FinalClosureSupervisor.ContentManifest(
                List.of(new FinalClosureSupervisor.ContentEntry("guide.md", afterHash)),
                currentTree);
        var changes = List.of(new FinalClosureSupervisor.DocsDelta(
                "guide.md", beforeHash, afterHash));
        var attribution = Map.of(
                "schema", "MMR-DOCS-ATTRIBUTION-1",
                "event", Map.of(
                        "id", "user-docs-commit-1",
                        "actor", "user",
                        "occurredAt", "2026-09-07T00:00:00Z"),
                "baseline", Map.of(
                        "head", baselineHead,
                        "regularFiles", 1,
                        "treeSha256", baselineTree),
                "final", Map.of(
                        "head", currentHead,
                        "regularFiles", 1,
                        "treeSha256", currentTree,
                        "status", List.of()),
                "changes", List.of(Map.of(
                        "path", "guide.md",
                        "beforeSha256", wrongBeforeHash,
                        "afterSha256", afterHash)));
        write(taskRoot.resolve("implement/docs-new-attribution.json"),
                EvidenceJson.canonicalBytes(attribution));

        var audit = FinalClosureSupervisor.auditDocsAttribution(
                taskRoot, baselineHead, 1, baselineTree, currentHead,
                manifest, List.of(), changes, false);

        assertEquals("Invalid", audit.status());
    }

    @Test
    void userAttributionReconstructsAddedAndRemovedFrozenEntries(
            @TempDir Path taskRoot) throws Exception {
        var addedHash = "6".repeat(64);
        var added = auditAttribution(taskRoot.resolve("added"), List.of(),
                List.of(new FinalClosureSupervisor.ContentEntry("added.md", addedHash)),
                List.of(new FinalClosureSupervisor.DocsDelta(
                        "added.md", "Absent", addedHash)),
                List.of(change("added.md", "Absent", addedHash)));
        assertEquals("UserAttributed", added.status());

        var removedHash = "7".repeat(64);
        var removed = auditAttribution(taskRoot.resolve("removed"),
                List.of(new FinalClosureSupervisor.ContentEntry(
                        "removed.md", removedHash)),
                List.of(),
                List.of(),
                List.of(change("removed.md", removedHash, "Absent")));
        assertEquals("UserAttributed", removed.status());
    }

    @Test
    void userAttributionRejectsOmittedAndExtraPaths(@TempDir Path taskRoot)
            throws Exception {
        var beforeGuide = "6".repeat(64);
        var afterGuide = "7".repeat(64);
        var beforeOther = "8".repeat(64);
        var afterOther = "9".repeat(64);
        var baseline = List.of(
                new FinalClosureSupervisor.ContentEntry("guide.md", beforeGuide),
                new FinalClosureSupervisor.ContentEntry("other.md", beforeOther));
        var current = List.of(
                new FinalClosureSupervisor.ContentEntry("guide.md", afterGuide),
                new FinalClosureSupervisor.ContentEntry("other.md", afterOther));
        var detected = List.of(
                new FinalClosureSupervisor.DocsDelta(
                        "guide.md", beforeGuide, afterGuide),
                new FinalClosureSupervisor.DocsDelta(
                        "other.md", beforeOther, afterOther));

        var omitted = auditAttribution(taskRoot.resolve("omitted"), baseline, current,
                detected, List.of(change("guide.md", beforeGuide, afterGuide)));
        assertEquals("Invalid", omitted.status());

        var extra = auditAttribution(taskRoot.resolve("extra"), baseline, current,
                detected, List.of(
                        change("guide.md", beforeGuide, afterGuide),
                        change("other.md", beforeOther, afterOther),
                        change("unexpected.md", "Absent", "Absent")));
        assertEquals("Invalid", extra.status());
    }

    @Test
    void successfulGitStderrIsRetainedButExcludedFromStdoutPaths(
            @TempDir Path root) throws Exception {
        var output = FinalClosureSupervisor.runGit(root,
                "-c", "alias.emit=!f() { printf 'guide.md\\n'; "
                        + "printf 'warning: diagnostic\\n' >&2; }; f",
                "emit");

        assertEquals(List.of("guide.md"), output.stdoutLines());
        assertEquals(List.of("warning: diagnostic"), output.stderrLines());
    }

    @Test
    void nulDelimitedGitOutputPreservesSpecialPaths() throws Exception {
        var output = new FinalClosureSupervisor.GitCommandOutput(
                "line\r\nbreak.md\0ordinary.md\0".getBytes(StandardCharsets.UTF_8),
                new byte[0]);

        assertEquals(List.of("line\r\nbreak.md", "ordinary.md"),
                output.stdoutNulDelimited());
    }

    @Test
    void userAttributionMustAcceptQuotedNonAsciiPath(@TempDir Path root)
            throws Exception {
        var docsRoot = root.resolve("docs");
        var taskRoot = root.resolve("task");
        var projectRoot = root.resolve("project");
        Files.createDirectories(docsRoot);
        Files.createDirectories(projectRoot);
        FinalClosureSupervisor.runGit(docsRoot, "init", "--quiet");
        FinalClosureSupervisor.runGit(docsRoot, "config", "user.name", "Review Test");
        FinalClosureSupervisor.runGit(docsRoot, "config", "user.email",
                "review@example.invalid");
        FinalClosureSupervisor.runGit(docsRoot, "config", "core.quotePath", "true");
        FinalClosureSupervisor.runGit(docsRoot, "-c", "commit.gpgsign=false",
                "commit", "--quiet", "--allow-empty", "-m", "baseline");

        var baselineHead = FinalClosureSupervisor.runGit(
                docsRoot, "rev-parse", "HEAD").stdoutText().strip();
        var baselineTree = treeHash(List.of());
        var baseline = """
                # docs baseline

                - Git HEAD: `%s`
                - Regular content files: `0`
                - Content-tree SHA-256: `%s`

                ## Existing user-owned working-tree state

                ```text
                ```
                """.formatted(baselineHead, baselineTree);
        write(taskRoot.resolve("contract/docs-new-readonly-baseline.md"), baseline);

        var relative = "模型.md";
        var file = docsRoot.resolve(relative);
        write(file, "content");
        var contentHash = EvidenceJson.sha256(Files.readAllBytes(file));
        var currentEntries = List.of(
                new FinalClosureSupervisor.ContentEntry(relative, contentHash));
        var currentTree = treeHash(currentEntries);
        var displayStatus = FinalClosureSupervisor.runGit(docsRoot, "status",
                "--porcelain=v1", "--untracked-files=normal").stdoutLines();
        assertEquals(1, displayStatus.size());
        assertTrue(displayStatus.get(0).startsWith("?? \""));
        var currentStatus = FinalClosureSupervisor.gitStatus(docsRoot);
        assertEquals(List.of("?? " + relative), currentStatus);
        write(taskRoot.resolve("implement/docs-new-attribution.json"),
                EvidenceJson.canonicalBytes(Map.of(
                        "schema", "MMR-DOCS-ATTRIBUTION-1",
                        "event", Map.of(
                                "id", "user-non-ascii-path-1",
                                "actor", "user",
                                "occurredAt", "2026-09-08T00:00:00Z"),
                        "baseline", Map.of(
                                "head", baselineHead,
                                "regularFiles", 0,
                                "treeSha256", baselineTree),
                        "final", Map.of(
                                "head", baselineHead,
                                "regularFiles", 1,
                                "treeSha256", currentTree,
                                "status", currentStatus),
                        "changes", List.of(
                                change(relative, "Absent", contentHash)))));

        var auditDocs = FinalClosureSupervisor.class.getDeclaredMethod(
                "auditDocs", Path.class, Path.class, Path.class,
                FinalClosureSupervisor.ContentManifest.class);
        auditDocs.setAccessible(true);
        var audit = auditDocs.invoke(null, taskRoot, docsRoot, projectRoot,
                new FinalClosureSupervisor.ContentManifest(currentEntries, currentTree));
        var changedPaths = audit.getClass().getDeclaredMethod("changedPaths");
        changedPaths.setAccessible(true);
        @SuppressWarnings("unchecked")
        var deltas = (List<FinalClosureSupervisor.DocsDelta>) changedPaths.invoke(audit);
        var attributionStatus = audit.getClass().getDeclaredMethod("attributionStatus");
        attributionStatus.setAccessible(true);
        var verdict = audit.getClass().getDeclaredMethod("verdict");
        verdict.setAccessible(true);

        assertEquals(List.of(relative),
                deltas.stream().map(FinalClosureSupervisor.DocsDelta::path).toList());
        assertEquals("UserAttributed", attributionStatus.invoke(audit));
        assertEquals("Pass", verdict.invoke(audit));
    }

    @Test
    void userAttributionMustCoverBothSidesOfDetectedRename(@TempDir Path root)
            throws Exception {
        var docsRoot = root.resolve("docs");
        var taskRoot = root.resolve("task");
        var projectRoot = root.resolve("project");
        Files.createDirectories(docsRoot);
        Files.createDirectories(projectRoot);
        FinalClosureSupervisor.runGit(docsRoot, "init", "--quiet");
        FinalClosureSupervisor.runGit(docsRoot, "config", "user.name", "Review Test");
        FinalClosureSupervisor.runGit(docsRoot, "config", "user.email",
                "review@example.invalid");
        FinalClosureSupervisor.runGit(docsRoot, "config", "diff.renames", "true");
        FinalClosureSupervisor.runGit(docsRoot, "config", "status.renames", "true");

        var oldPath = docsRoot.resolve("old.md");
        var newPath = docsRoot.resolve("new.md");
        write(oldPath, "same bytes");
        var contentHash = EvidenceJson.sha256(Files.readAllBytes(oldPath));
        FinalClosureSupervisor.runGit(docsRoot, "add", "old.md");
        FinalClosureSupervisor.runGit(docsRoot, "-c", "commit.gpgsign=false",
                "commit", "--quiet", "-m", "baseline");
        var baselineHead = FinalClosureSupervisor.runGit(
                docsRoot, "rev-parse", "HEAD").stdoutText().strip();
        var baselineEntries = List.of(
                new FinalClosureSupervisor.ContentEntry("old.md", contentHash));
        var baselineTree = treeHash(baselineEntries);

        Files.move(oldPath, newPath);
        FinalClosureSupervisor.runGit(docsRoot, "add", "--all");
        var currentStatus = FinalClosureSupervisor.gitStatus(docsRoot);
        assertEquals(List.of("R  old.md -> new.md"), currentStatus);

        var currentEntries = List.of(
                new FinalClosureSupervisor.ContentEntry("new.md", contentHash));
        var currentTree = treeHash(currentEntries);
        var baseline = """
                # docs baseline

                - Git HEAD: `%s`
                - Regular content files: `1`
                - Content-tree SHA-256: `%s`

                ## Existing user-owned working-tree state

                ```text
                ```
                """.formatted(baselineHead, baselineTree);
        write(taskRoot.resolve("contract/docs-new-readonly-baseline.md"), baseline);
        write(taskRoot.resolve("implement/docs-new-attribution.json"),
                EvidenceJson.canonicalBytes(Map.of(
                        "schema", "MMR-DOCS-ATTRIBUTION-1",
                        "event", Map.of(
                                "id", "user-rename-1",
                                "actor", "user",
                                "occurredAt", "2026-09-08T00:00:00Z"),
                        "baseline", Map.of(
                                "head", baselineHead,
                                "regularFiles", 1,
                                "treeSha256", baselineTree),
                        "final", Map.of(
                                "head", baselineHead,
                                "regularFiles", 1,
                                "treeSha256", currentTree,
                                "status", currentStatus),
                        "changes", List.of(
                                change("old.md", contentHash, "Absent"),
                                change("new.md", "Absent", contentHash)))));

        var auditDocs = FinalClosureSupervisor.class.getDeclaredMethod(
                "auditDocs", Path.class, Path.class, Path.class,
                FinalClosureSupervisor.ContentManifest.class);
        auditDocs.setAccessible(true);
        var audit = auditDocs.invoke(null, taskRoot, docsRoot, projectRoot,
                new FinalClosureSupervisor.ContentManifest(currentEntries, currentTree));
        var attributionStatus = audit.getClass().getDeclaredMethod("attributionStatus");
        attributionStatus.setAccessible(true);
        var verdict = audit.getClass().getDeclaredMethod("verdict");
        verdict.setAccessible(true);

        assertEquals("UserAttributed", attributionStatus.invoke(audit));
        assertEquals("Pass", verdict.invoke(audit));
    }

    @Test
    void nativeManifestRejectsSwappedRuntimeLibrary(@TempDir Path taskRoot)
            throws Exception {
        var expectedLibrary = "native-candidate".getBytes(StandardCharsets.UTF_8);
        var swappedLibrary = "swapped-library".getBytes(StandardCharsets.UTF_8);
        var candidate = nativeCandidate(taskRoot, expectedLibrary);
        var runtime = taskRoot.resolve("runtime.dll");
        Files.write(runtime, expectedLibrary);
        assertDoesNotThrow(() -> candidate.verifyRuntimeLibrary(runtime));
        Files.write(runtime, swappedLibrary);
        assertThrows(IOException.class, () -> candidate.verifyRuntimeLibrary(runtime));
    }

    @Test
    void nativeSourceClosureRejectsDirtyOrWrongCandidate(@TempDir Path taskRoot)
            throws Exception {
        var candidate = nativeCandidate(taskRoot,
                "native-candidate".getBytes(StandardCharsets.UTF_8));
        assertTrue(FinalClosureSupervisor.nativeCandidateMatches(
                candidate, NATIVE_REVISION, List.of()));
        assertFalse(FinalClosureSupervisor.nativeCandidateMatches(
                candidate, "c".repeat(40), List.of()));
        assertFalse(FinalClosureSupervisor.nativeCandidateMatches(
                candidate, NATIVE_REVISION, List.of(" M native-test.cpp")));
    }

    @Test
    void authorityIdentityRejectsWrongNativeRevision() {
        var source = json(Map.of(
                "authorityRevisions", Map.of(
                        "java", JAVA_REVISION,
                        "native", "c".repeat(40)),
                "configuration", Map.of("nativeLibrarySha256", "d".repeat(64)),
                "revisions", Map.of(
                        "taskContext", 3,
                        "decision", 3,
                        "contract", 2,
                        "implementation", 18)));
        var expected = new FinalClosureSupervisor.ExpectedIdentity(
                JAVA_REVISION, 18, Map.of("native", NATIVE_REVISION),
                Map.of("nativeLibrarySha256", "d".repeat(64)));
        assertThrows(IOException.class, () -> FinalClosureSupervisor.verifyIdentity(
                source, expected, "mutated scenario"));
    }

    @Test
    void authorityIdentityOnlyRequiresAuthoritiesInTheScenarioRadius() {
        var source = json(Map.of(
                "authorityRevisions", Map.of("java", JAVA_REVISION),
                "configuration", Map.of("task", "domain"),
                "revisions", Map.of(
                        "taskContext", 3,
                        "decision", 3,
                        "contract", 2,
                        "implementation", 18)));
        var expected = new FinalClosureSupervisor.ExpectedIdentity(
                JAVA_REVISION, 18, Map.of(), Map.of());
        assertDoesNotThrow(() -> FinalClosureSupervisor.verifyIdentity(
                source, expected, "domain scenario"));
    }

    @Test
    void indexAndVerdictClaimsMustMatch() {
        var index = json(Map.of(
                "acceptanceEligible", true,
                "finalAcceptance", List.of("FA-005")));
        var verdictIdentity = json(Map.of(
                "acceptanceEligible", false,
                "finalAcceptance", List.of("FA-005"),
                "scenarioId", "scenario"));
        assertThrows(IOException.class, () -> FinalClosureSupervisor.verifyIndexClaim(
                index, verdictIdentity, "scenario"));
    }

    @Test
    void finalAcceptanceRejectsIneligibleAndMisscopedScenarios() {
        var ineligible = scenario(false, List.of("FA-005"));
        var misscoped = scenario(true, List.of("FA-006"));
        var eligible = scenario(true, List.of("FA-005"));
        assertFalse(FinalClosureSupervisor.scenarioSupportsFinalAcceptance(
                ineligible, "FA-005"));
        assertFalse(FinalClosureSupervisor.scenarioSupportsFinalAcceptance(
                misscoped, "FA-005"));
        assertTrue(FinalClosureSupervisor.scenarioSupportsFinalAcceptance(
                eligible, "FA-005"));
    }

    @Test
    void currentRevisionDispositionRejectsStaleLabels() {
        var current = List.<Map<String, Object>>of(Map.of(
                "finalAcceptance", "FA-001",
                "revisionDisposition", "Current I21 rerun; historical I-02 adopted"));
        var stale = List.<Map<String, Object>>of(Map.of(
                "finalAcceptance", "FA-001",
                "revisionDisposition", "Current I20 rerun; historical I-02 adopted"));

        assertDoesNotThrow(() -> FinalClosureSupervisor
                .verifyCurrentRevisionDispositions(current, 21));
        assertThrows(IOException.class, () -> FinalClosureSupervisor
                .verifyCurrentRevisionDispositions(stale, 21));
    }

    private static NativeCandidateIdentity nativeCandidate(
            Path taskRoot, byte[] expectedLibrary) throws Exception {
        var provenancePath = taskRoot.resolve("implement/evidence/native-run.json");
        var libraryHash = EvidenceJson.sha256(expectedLibrary);
        var provenanceBytes = EvidenceJson.canonicalBytes(Map.of(
                "authorityRevisions", Map.of("native", NATIVE_REVISION),
                "configuration", Map.of("nativeLibrarySha256", libraryHash)));
        write(provenancePath, provenanceBytes);
        var manifest = Map.of(
                "schema", "MMR-NATIVE-CANDIDATE-1",
                "nativeRevision", NATIVE_REVISION,
                "runtimeLibrarySha256", libraryHash,
                "provenance", Map.of(
                        "artifact", "implement/evidence/native-run.json",
                        "sha256", EvidenceJson.sha256(provenanceBytes)));
        write(taskRoot.resolve("implement/native-candidate.json"),
                EvidenceJson.canonicalBytes(manifest));
        return NativeCandidateIdentity.load(taskRoot);
    }

    private static FinalClosureSupervisor.ScenarioAudit scenario(
            boolean eligible, List<String> finalAcceptance) {
        return new FinalClosureSupervisor.ScenarioAudit(
                "scenario", true, "Pass", eligible, finalAcceptance,
                "verdict.json", "e".repeat(64), JAVA_REVISION, 18, "test radius");
    }

    private static FinalClosureSupervisor.DocsAttributionAudit auditAttribution(
            Path taskRoot,
            List<FinalClosureSupervisor.ContentEntry> baselineEntries,
            List<FinalClosureSupervisor.ContentEntry> currentEntries,
            List<FinalClosureSupervisor.DocsDelta> detectedChanges,
            List<Map<String, String>> attributedChanges) throws Exception {
        var baselineHead = "1".repeat(40);
        var currentHead = "2".repeat(40);
        var baselineTree = treeHash(baselineEntries);
        var currentTree = treeHash(currentEntries);
        var attribution = Map.of(
                "schema", "MMR-DOCS-ATTRIBUTION-1",
                "event", Map.of(
                        "id", "user-docs-commit-1",
                        "actor", "user",
                        "occurredAt", "2026-09-07T00:00:00Z"),
                "baseline", Map.of(
                        "head", baselineHead,
                        "regularFiles", baselineEntries.size(),
                        "treeSha256", baselineTree),
                "final", Map.of(
                        "head", currentHead,
                        "regularFiles", currentEntries.size(),
                        "treeSha256", currentTree,
                        "status", List.of()),
                "changes", attributedChanges);
        write(taskRoot.resolve("implement/docs-new-attribution.json"),
                EvidenceJson.canonicalBytes(attribution));
        var manifest = new FinalClosureSupervisor.ContentManifest(
                List.copyOf(currentEntries), currentTree);
        return FinalClosureSupervisor.auditDocsAttribution(
                taskRoot, baselineHead, baselineEntries.size(), baselineTree,
                currentHead, manifest, List.of(), detectedChanges, false);
    }

    private static Map<String, String> change(
            String path, String before, String after) {
        return Map.of(
                "path", path,
                "beforeSha256", before,
                "afterSha256", after);
    }

    private static String treeHash(
            List<FinalClosureSupervisor.ContentEntry> entries) {
        var lines = entries.stream()
                .sorted(Comparator.comparing(
                        FinalClosureSupervisor.ContentEntry::path))
                .map(entry -> entry.sha256() + "  " + entry.path())
                .toList();
        return EvidenceJson.sha256((String.join("\n", lines) + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject json(Object value) {
        return JsonParser.parseString(new String(
                EvidenceJson.canonicalBytes(value), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static String candidateName() {
        return "documentation-" + "candidate.md";
    }

    private static void write(Path path, String value) throws IOException {
        write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path path, byte[] value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, value);
    }
}

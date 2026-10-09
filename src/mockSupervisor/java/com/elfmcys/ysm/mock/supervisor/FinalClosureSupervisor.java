package com.elfmcys.ysm.mock.supervisor;

import com.elfmcys.ysm.mock.evidence.EvidenceJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

/** Aggregates existing independent evidence; it owns no model-management business state. */
public final class FinalClosureSupervisor {
    private static final int TASK_CONTEXT_REVISION = 3;
    private static final int DECISION_REVISION = 3;
    private static final int CONTRACT_REVISION = 2;
    private static final int HOST_DISPOSITION_IMPLEMENTATION_REVISION = 15;
    private static final String HOST_DISPOSITION_JAVA_REVISION =
            "5a1337dd36579e14fde81c632c575234a5622a85";
    private static final Set<String> USER_OWNED_JAVA_STATUS = Set.of(
            "?? .claude/", "?? AGENTS.md", "?? CLAUDE.md");
    private static final Pattern BASELINE_HEAD = Pattern.compile(
            "Git HEAD: `([0-9a-f]{40})`");
    private static final Pattern BASELINE_FILES = Pattern.compile(
            "Regular content files: `([0-9]+)`");
    private static final Pattern BASELINE_TREE = Pattern.compile(
            "Content-tree SHA-256: `([0-9a-f]{64})`");

    private FinalClosureSupervisor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 14) {
            throw new IllegalArgumentException("Expected 14 arguments");
        }
        var projectRoot = absolute(args[0]);
        var taskRoot = absolute(args[1]);
        var outputRoot = absolute(args[2]);
        var javaRevision = args[3];
        var implementationRevision = Integer.parseInt(args[4]);
        var baselineJavaRevision = args[5];
        var domainInput = absolute(args[6]);
        var smokeInput = absolute(args[7]);
        var systemInput = absolute(args[8]);
        var exactInput = absolute(args[9]);
        var fullTestResults = absolute(args[10]);
        var processorTestResults = absolute(args[11]);
        var supervisorTestResults = absolute(args[12]);
        var nativeRoot = absolute(args[13]);
        var workspaceRoot = taskRoot.getParent().getParent().getParent();
        var docsRoot = workspaceRoot.resolve("docs-new");

        require(git(projectRoot, "rev-parse", "HEAD").equals(javaRevision),
                "Configured Java revision is not the current HEAD");
        require(implementationRevision == 21,
                "Final closure must run as Implement revision 21");

        try {
            var docsRevision = docsBaselineHead(taskRoot);
            var nativeCandidate = NativeCandidateIdentity.load(taskRoot);
            copyTree(domainInput, outputRoot.resolve("domain"));
            copyTree(smokeInput, outputRoot.resolve("classpath-smoke"));
            copyTree(systemInput, outputRoot.resolve("classpath-system"));
            copyTree(exactInput, outputRoot.resolve("classpath-exact-100"));

            var domainIdentity = new ExpectedIdentity(javaRevision, implementationRevision,
                    Map.of(), Map.of());
            var smokeIdentity = new ExpectedIdentity(javaRevision, implementationRevision,
                    Map.of("docs", docsRevision), Map.of());
            var nativeIdentity = new ExpectedIdentity(javaRevision, implementationRevision,
                    Map.of("docs", docsRevision, "native", nativeCandidate.revision()),
                    Map.of("nativeLibrarySha256", nativeCandidate.runtimeLibrarySha256(),
                            "nativeCandidateManifestSha256",
                            nativeCandidate.manifestSha256()));
            var domain = inspectGroup(outputRoot.resolve("domain"), domainIdentity);
            var smoke = inspectGroup(outputRoot.resolve("classpath-smoke"), smokeIdentity);
            var system = inspectGroup(outputRoot.resolve("classpath-system"), nativeIdentity);
            var exact = inspectGroup(outputRoot.resolve("classpath-exact-100"), nativeIdentity);
            var hostPath = taskRoot.resolve("implement/evidence/i-11/pass");
            var host = inspectGroup(hostPath, new ExpectedIdentity(
                    HOST_DISPOSITION_JAVA_REVISION,
                    HOST_DISPOSITION_IMPLEMENTATION_REVISION,
                    Map.of("docs", docsRevision, "native", nativeCandidate.revision()),
                    Map.of()));

            var required = new LinkedHashMap<String, ScenarioAudit>();
            requireScenario(domain, required, "MMR-DOM-LOCAL-001");
            requireScenario(domain, required, "MMR-DOM-REMOTE-AUTH-001");
            requireScenario(domain, required, "MMR-DOM-REMOTE-PUB-001");
            requireScenario(domain, required, "MMR-DOM-DEMAND-001");
            requireScenario(domain, required, "MMR-DOM-PRESENTATION-001");
            requireScenario(domain, required, "MMR-WORKLOAD-100-001");
            requireScenario(smoke, required, "MMR-CLASSPATH-SMOKE-001");
            requireScenario(system, required, "MMR-CLASSPATH-SYSTEM-001");
            requireScenario(exact, required, "MMR-CLASSPATH-100-001");
            requireScenario(host, required, "MMR-FORGE-DISPOSITION-001");

            var historical = inspectHistorical(taskRoot);
            writeNew(outputRoot.resolve("historical-integrity.json"), historical);

            var docsManifest = contentManifest(docsRoot);
            writeNew(outputRoot.resolve("docs-new-manifest.json"), docsManifest);
            var docsAudit = auditDocs(taskRoot, docsRoot, projectRoot, docsManifest);
            writeNew(outputRoot.resolve("docs-new-audit.json"), docsAudit);

            var changeAudit = auditChanges(projectRoot, nativeRoot, baselineJavaRevision,
                    outputRoot.resolve("classpath-system"), nativeCandidate);
            writeNew(outputRoot.resolve("change-closure.json"), changeAudit);

            var tests = List.of(
                    testSummary("test", fullTestResults),
                    testSummary("processorTest", processorTestResults),
                    testSummary("modelManagementMockSupervisorTest",
                            supervisorTestResults));
            var testsPass = tests.stream().allMatch(TestSummary::pass);
            var verification = Map.of(
                    "groups", List.of(domain, smoke, system, exact, host),
                    "tests", tests,
                    "requiredScenarioCount", required.size(),
                    "allRequiredScenariosValid",
                    required.values().stream().allMatch(ScenarioAudit::valid),
                    "allRegressionSuitesValid", testsPass);
            writeNew(outputRoot.resolve("verification.json"), verification);

            var acceptance = acceptance(taskRoot, outputRoot, required,
                    historical, changeAudit, docsAudit, testsPass, javaRevision,
                    implementationRevision);
            writeNew(outputRoot.resolve("acceptance.json"), acceptance);

            var run = new LinkedHashMap<String, Object>();
            run.put("authorityRevisions", Map.of(
                    "java", javaRevision,
                    "native", nativeCandidate.revision(),
                    "docs", git(docsRoot, "rev-parse", "HEAD")));
            run.put("command", List.of("./gradlew modelManagementMockFinal",
                    "dependencies: test, processorTest, domain, classpath smoke/full/exact-100, packaging"));
            run.put("configuration", Map.of(
                    "task", "modelManagementMockFinal",
                    "productionCandidate", "single",
                    "forgeClosure", "I-11 historical per-claim adoption; no rerun"));
            run.put("outputDirectory", normalize(outputRoot));
            run.put("revisions", Map.of(
                    "contract", CONTRACT_REVISION,
                    "decision", DECISION_REVISION,
                    "implementation", implementationRevision,
                    "taskContext", TASK_CONTEXT_REVISION));
            run.put("startedAt", Instant.now().toString());
            run.put("aggregateVerdict", acceptance.get("aggregateVerdict"));
            run.put("reviewDisposition", "Pending independent Final Review");
            writeNew(outputRoot.resolve("run.json"), run);

            writeNew(outputRoot.resolve("artifact-index.json"), artifactIndex(outputRoot));
            require("Pass".equals(acceptance.get("aggregateVerdict")),
                    "One or more required final acceptance entries did not pass");
        } catch (Throwable failure) {
            var artifact = outputRoot.resolve("failure.json");
            if (!Files.exists(artifact)) {
                writeNew(artifact, Map.of(
                        "type", failure.getClass().getName(),
                        "message", String.valueOf(failure.getMessage()),
                        "retainedAt", Instant.now().toString()));
            }
            throw failure;
        }
    }

    private static GroupAudit inspectGroup(Path root, ExpectedIdentity expected) {
        var errors = new ArrayList<String>();
        var scenarios = new TreeMap<String, ScenarioAudit>();
        String treeSha256 = null;
        try {
            require(Files.isDirectory(root), "Evidence group is missing: " + root);
            treeSha256 = treeHash(root);
            var run = object(root.resolve("run.json"));
            verifyIdentity(run, expected, "run.json");
            for (var line : Files.readAllLines(root.resolve("index.jsonl"),
                    StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                var index = JsonParser.parseString(line).getAsJsonObject();
                var scenarioId = index.get("scenarioId").getAsString();
                var scenario = root.resolve(scenarioId).normalize();
                require(scenario.startsWith(root) && Files.isDirectory(scenario),
                        "Scenario path is missing or escaped: " + scenarioId);
                var inputHash = EvidenceJson.sha256(
                        Files.readAllBytes(scenario.resolve("input.json")));
                require(inputHash.equals(index.get("inputSha256").getAsString()),
                        "Input hash mismatch: " + scenarioId);
                var verdictPath = root.resolve(
                        index.get("verdictArtifact").getAsString()).normalize();
                var inventoryPath = root.resolve(
                        index.get("inventoryArtifact").getAsString()).normalize();
                require(verdictPath.startsWith(root) && inventoryPath.startsWith(root),
                        "Indexed artifact escaped group: " + scenarioId);
                var verdict = object(verdictPath);
                verifyIdentity(verdict.getAsJsonObject("identity"), expected,
                        scenarioId + " verdict");
                require(inputHash.equals(verdict.getAsJsonObject("identity")
                                .get("inputSha256").getAsString()),
                        "Verdict input mismatch: " + scenarioId);
                require(index.get("verdict").getAsString()
                                .equals(verdict.get("verdict").getAsString()),
                        "Index/verdict mismatch: " + scenarioId);
                var verdictIdentity = verdict.getAsJsonObject("identity");
                verifyIndexClaim(index, verdictIdentity, scenarioId);
                verifyInventory(scenario, inventoryPath);
                var valid = "Pass".equals(verdict.get("verdict").getAsString())
                        && verdict.getAsJsonArray("mismatches").isEmpty();
                scenarios.put(scenarioId, new ScenarioAudit(scenarioId, valid,
                        verdict.get("verdict").getAsString(),
                        index.get("acceptanceEligible").getAsBoolean(),
                        strings(index.getAsJsonArray("finalAcceptance")),
                        normalize(verdictPath), inputHash,
                        expected.javaRevision(), expected.implementationRevision(),
                        verdict.get("evidenceLimit").getAsString()));
            }
            require(!scenarios.isEmpty(), "Evidence index is empty: " + root);
        } catch (Throwable failure) {
            errors.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return new GroupAudit(root.getFileName().toString(), normalize(root),
                errors.isEmpty(), treeSha256, Map.copyOf(scenarios), List.copyOf(errors));
    }

    static void verifyIdentity(JsonObject source, ExpectedIdentity expected,
                               String label) throws IOException {
        var revisions = source.getAsJsonObject("revisions");
        require(revisions.get("taskContext").getAsInt() == TASK_CONTEXT_REVISION
                        && revisions.get("decision").getAsInt() == DECISION_REVISION
                        && revisions.get("contract").getAsInt() == CONTRACT_REVISION
                        && revisions.get("implementation").getAsInt()
                        == expected.implementationRevision(),
                label + " revision mismatch");
        var authority = source.getAsJsonObject("authorityRevisions");
        require(authority.get("java").getAsString()
                        .equals(expected.javaRevision()),
                label + " Java revision mismatch");
        for (var entry : expected.authorityRevisions().entrySet()) {
            require(authority.has(entry.getKey())
                            && entry.getValue().equals(
                            authority.get(entry.getKey()).getAsString()),
                    label + " " + entry.getKey() + " authority revision mismatch");
        }
        var configuration = source.getAsJsonObject("configuration");
        for (var entry : expected.configuration().entrySet()) {
            require(configuration.has(entry.getKey())
                            && entry.getValue().equals(
                            configuration.get(entry.getKey()).getAsString()),
                    label + " " + entry.getKey() + " configuration mismatch");
        }
    }

    static void verifyIndexClaim(JsonObject index, JsonObject verdictIdentity,
                                 String scenarioId) throws IOException {
        require(index.get("acceptanceEligible").getAsBoolean()
                        == verdictIdentity.get("acceptanceEligible").getAsBoolean(),
                "Index/verdict acceptance eligibility mismatch: " + scenarioId);
        require(strings(index.getAsJsonArray("finalAcceptance")).equals(
                        strings(verdictIdentity.getAsJsonArray("finalAcceptance"))),
                "Index/verdict final acceptance mismatch: " + scenarioId);
        require(scenarioId.equals(verdictIdentity.get("scenarioId").getAsString()),
                "Index/verdict scenario identity mismatch: " + scenarioId);
    }

    private static void verifyInventory(Path scenario, Path inventoryPath)
            throws IOException {
        var inventory = object(inventoryPath).getAsJsonArray("artifacts");
        for (var value : inventory) {
            var entry = value.getAsJsonObject();
            var path = scenario.resolve(entry.get("path").getAsString()).normalize();
            require(path.startsWith(scenario) && Files.isRegularFile(path),
                    "Inventory artifact is missing or escaped: " + path);
            require(EvidenceJson.sha256(Files.readAllBytes(path))
                            .equals(entry.get("sha256").getAsString()),
                    "Inventory hash mismatch: " + path);
        }
    }

    private static void requireScenario(GroupAudit group,
                                        Map<String, ScenarioAudit> required,
                                        String scenarioId) throws IOException {
        require(group.valid(), "Evidence group failed validation: " + group.path());
        var scenario = group.scenarios().get(scenarioId);
        require(scenario != null && scenario.valid(),
                "Required scenario is absent or failed: " + scenarioId);
        required.put(scenarioId, scenario);
    }

    private static HistoricalAudit inspectHistorical(Path taskRoot) throws IOException {
        var dispositionPath = taskRoot.resolve(
                "implement/evidence/i-08/pass/historical-disposition.json");
        var source = object(dispositionPath);
        var checks = new ArrayList<Map<String, Object>>();
        var valid = true;
        for (var value : source.getAsJsonArray("artifacts")) {
            var entry = value.getAsJsonObject();
            var disposition = entry.get("disposition").getAsString();
            var original = Path.of(entry.get("originalPath").getAsString());
            var expectedHash = entry.get("contentSha256").getAsString();
            if ("superseded".equals(disposition)) {
                checks.add(Map.of(
                        "path", normalize(original),
                        "disposition", disposition,
                        "valid", true,
                        "result", "Process-only plan is excluded and removed by final reduction"));
                continue;
            }
            var actualHash = Files.isDirectory(original)
                    ? treeHash(original) : EvidenceJson.sha256(Files.readAllBytes(original));
            var matches = expectedHash.equals(actualHash)
                    && entry.getAsJsonObject("integrity").get("valid").getAsBoolean();
            valid &= matches;
            checks.add(Map.of(
                    "path", normalize(original),
                    "disposition", disposition,
                    "expectedSha256", expectedHash,
                    "actualSha256", actualHash,
                    "valid", matches));
        }
        return new HistoricalAudit(valid, checks.size(), List.copyOf(checks),
                normalize(dispositionPath),
                EvidenceJson.sha256(Files.readAllBytes(dispositionPath)));
    }

    private static DocsAudit auditDocs(Path taskRoot, Path docsRoot, Path projectRoot,
                                       ContentManifest current) throws IOException {
        var baselinePath = taskRoot.resolve("contract/docs-new-readonly-baseline.md");
        var baseline = Files.readString(baselinePath, StandardCharsets.UTF_8);
        var baselineHead = match(BASELINE_HEAD, baseline, "docs baseline HEAD");
        var baselineFiles = Integer.parseInt(match(
                BASELINE_FILES, baseline, "docs baseline file count"));
        var baselineTree = match(BASELINE_TREE, baseline, "docs baseline tree hash");
        var expectedStatus = statusBlock(baseline);
        var currentHead = git(docsRoot, "rev-parse", "HEAD");
        var currentStatusEntries = gitStatusEntries(docsRoot);
        var currentStatus = currentStatusEntries.stream()
                .map(GitStatusEntry::display).toList();
        var statusDelta = docsStatusDelta(docsRoot,
                baselineStatusEntries(expectedStatus), currentStatusEntries);
        var exact = baselineHead.equals(currentHead)
                && baselineFiles == current.entries().size()
                && baselineTree.equals(current.treeSha256())
                && expectedStatus.equals(currentStatus);
        var deltaInventory = docsDeltaPaths(
                docsRoot, baselineHead, statusDelta, current);
        var changedPaths = deltaInventory.changedPaths();
        var candidate = auditDocumentationCandidate(taskRoot);
        var dependencyMatches = findForbiddenCandidateDependencies(taskRoot, projectRoot);
        var attribution = auditDocsAttribution(taskRoot, baselineHead, baselineFiles,
                baselineTree, currentHead, current, currentStatus, changedPaths, exact);
        var verdict = docsVerdict(exact, candidate.complete(), dependencyMatches,
                attribution.status());
        var conclusion = switch (verdict) {
            case "Pass" -> exact
                    ? "Task made no docs-new content-tree change"
                    : "All observed docs-new deltas are bound to an explicit user event and exact current hashes";
            case "Fail" -> !candidate.complete()
                    ? "Documentation candidate inventory is absent when declared, duplicated, or incomplete"
                    : !dependencyMatches.isEmpty()
                    ? "A forbidden production/test/Contract/acceptance consumer depends on the documentation candidate"
                    : "Attribution identifies a task-owned docs-new write";
            default -> "Unreviewable: no valid user event with exact path/hash attribution covers the changed content tree";
        };
        return new DocsAudit(verdict,
                Map.of("head", baselineHead, "regularFiles", baselineFiles,
                        "treeSha256", baselineTree, "status", expectedStatus),
                Map.of("head", currentHead, "regularFiles", current.entries().size(),
                        "treeSha256", current.treeSha256(), "status", currentStatus),
                changedPaths, deltaInventory.gitDiagnostics(), statusDelta,
                candidate.inventory(), candidate.complete(),
                dependencyMatches, attribution.path(), attribution.status(),
                attribution.eventId(), attribution.errors(), conclusion);
    }

    static String docsVerdict(boolean exact, boolean candidateComplete,
                              List<ForbiddenDependencyMatch> dependencyMatches,
                              String attributionStatus) {
        if (!candidateComplete || !dependencyMatches.isEmpty()
                || "TaskOwned".equals(attributionStatus)) {
            return "Fail";
        }
        if (exact || "UserAttributed".equals(attributionStatus)) {
            return "Pass";
        }
        return "Unreviewable";
    }

    static DocumentationCandidateAudit auditDocumentationCandidate(Path taskRoot)
            throws IOException {
        var implementRoot = taskRoot.resolve("implement");
        var inventory = new ArrayList<String>();
        if (Files.isDirectory(implementRoot)) {
            try (var files = Files.list(implementRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                                .startsWith("documentation-candidate"))
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(path -> inventory.add(normalize(path)));
            }
        }
        if (inventory.isEmpty()) {
            return new DocumentationCandidateAudit(List.of(), true, "None");
        }
        if (inventory.size() != 1) {
            return new DocumentationCandidateAudit(List.copyOf(inventory), false,
                    "Invalid: more than one documentation candidate");
        }
        var text = Files.readString(Path.of(inventory.get(0)), StandardCharsets.UTF_8);
        var complete = List.of("Primary target:", "Category:", "Provenance:",
                "Boundary:", "## Suggested wording", "## Open owner choice")
                .stream().allMatch(text::contains);
        return new DocumentationCandidateAudit(List.copyOf(inventory), complete,
                complete ? "Complete non-authority candidate" : "Incomplete candidate");
    }

    static List<ForbiddenDependencyMatch> findForbiddenCandidateDependencies(
            Path taskRoot, Path projectRoot) throws IOException {
        var matches = new ArrayList<ForbiddenDependencyMatch>();
        scanCandidateDependencies(matches, "production", projectRoot.resolve("src/main"),
                path -> true);
        scanCandidateDependencies(matches, "test", projectRoot.resolve("src/test"),
                path -> true);
        scanCandidateDependencies(matches, "Contract", taskRoot.resolve("contract"),
                path -> !path.getFileName().toString().equals("acceptance.md"));
        scanCandidateDependencies(matches, "acceptance",
                taskRoot.resolve("contract/acceptance.md"), path -> true);
        return matches.stream().sorted(Comparator
                .comparing(ForbiddenDependencyMatch::consumer)
                .thenComparing(ForbiddenDependencyMatch::path)).toList();
    }

    private static void scanCandidateDependencies(
            List<ForbiddenDependencyMatch> matches, String consumer, Path root,
            Predicate<Path> include) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        if (Files.isRegularFile(root)) {
            if (include.test(root) && isText(root) && containsCandidateReference(root)) {
                matches.add(new ForbiddenDependencyMatch(consumer, normalize(root)));
            }
            return;
        }
        try (var files = Files.walk(root)) {
            for (var path : files.filter(Files::isRegularFile).filter(include).toList()) {
                if (isText(path) && containsCandidateReference(path)) {
                    matches.add(new ForbiddenDependencyMatch(consumer, normalize(path)));
                }
            }
        }
    }

    private static boolean containsCandidateReference(Path path) throws IOException {
        var text = Files.readString(path, StandardCharsets.UTF_8);
        return text.contains("documentation-candidate.md")
                || text.contains("implement/documentation-candidate");
    }

    private static ChangeAudit auditChanges(Path projectRoot, Path nativeRoot,
                                             String baselineJavaRevision,
                                             Path systemEvidence,
                                             NativeCandidateIdentity nativeCandidate)
            throws IOException {
        var currentJava = git(projectRoot, "rev-parse", "HEAD");
        var diffLines = lines(git(projectRoot, "diff", "--name-status",
                baselineJavaRevision + ".." + currentJava));
        var categories = new TreeMap<String, List<String>>();
        for (var line : diffLines) {
            var fields = line.split("\\t");
            var path = fields[fields.length - 1].replace('\\', '/');
            categories.computeIfAbsent(category(path), ignored -> new ArrayList<>())
                    .add(line);
        }
        var javaStatus = lines(git(projectRoot, "status", "--porcelain=v1",
                "--untracked-files=normal"));
        var taskOwnedDirty = javaStatus.stream()
                .filter(line -> !USER_OWNED_JAVA_STATUS.contains(line)).toList();
        var nativeHead = git(nativeRoot, "rev-parse", "HEAD");
        var nativeStatus = lines(git(nativeRoot, "status", "--porcelain=v1",
                "--untracked-files=normal"));

        var productionDiff = git(projectRoot, "diff", "--unified=0",
                baselineJavaRevision + ".." + currentJava, "--", "src/main/java");
        var forbiddenProductionLines = addedLines(productionDiff).stream()
                .filter(FinalClosureSupervisor::forbiddenProductionMechanism).toList();
        var forbiddenCategories = List.of("API", "config", "schema", "ABI", "docs")
                .stream().filter(name -> categories.containsKey(name)).toList();
        var sideAudit = sideAudit(systemEvidence);
        var productionSeams = List.of(
                Map.of("symbol", "ConvertedObjectStore.snapshotDirect",
                        "kind", "production ownership fix",
                        "deletionEffect", "FA-001 retained lease follows mutable source and fails"),
                Map.of("symbol", "SessionCollectionPublication.fullFragments(authority, transferId, animations)",
                        "kind", "stateless composition seam",
                        "deletionEffect", "FA-005/FA-006 ordinary JVM must enter host-static AnimationManager or lose production publication encoding",
                        "ownerState", "none",
                        "productionCaller", "existing two-argument overload only"));
        var compatibility = List.of(
                "No protocol/schema/native ABI or public API path changed",
                "The two-argument fullFragments call remains the sole production caller; the overload is same-build composition, not a mixed-version reader",
                "LegacyClasspath is a Gradle physical configuration name, not a product compatibility track",
                "Socket timeouts and polling sleeps are harness deadlock guards, not production recovery");
        var pass = taskOwnedDirty.isEmpty()
                && nativeCandidateMatches(nativeCandidate, nativeHead, nativeStatus)
                && forbiddenProductionLines.isEmpty()
                && forbiddenCategories.isEmpty()
                && sideAudit.valid();
        return new ChangeAudit(pass, baselineJavaRevision, currentJava,
                List.copyOf(diffLines), immutableLists(categories),
                List.copyOf(javaStatus), List.copyOf(taskOwnedDirty),
                nativeCandidate.revision(), nativeHead, List.copyOf(nativeStatus),
                nativeCandidate.manifestPath(), nativeCandidate.manifestSha256(),
                nativeCandidate.runtimeLibrarySha256(),
                List.copyOf(forbiddenProductionLines),
                List.copyOf(forbiddenCategories), sideAudit,
                productionSeams, compatibility,
                "Verification source sets and final supervisor are excluded from release archives by verifyMockHostPackaging");
    }

    private static SideAudit sideAudit(Path root) throws IOException {
        var client = object(root.resolve("classpath.client.json"));
        var server = object(root.resolve("classpath.server.json"));
        var clientSide = client.getAsJsonObject("attributes")
                .get("net.neoforged.distribution").getAsString();
        var serverSide = server.getAsJsonObject("attributes")
                .get("net.neoforged.distribution").getAsString();
        var valid = clientSide.equals("client") && serverSide.equals("server")
                && client.get("ownEndpointOutputPresent").getAsBoolean()
                && client.get("oppositeEndpointOutputAbsent").getAsBoolean()
                && server.get("ownEndpointOutputPresent").getAsBoolean()
                && server.get("oppositeEndpointOutputAbsent").getAsBoolean()
                && !client.get("aggregateSha256").getAsString()
                .equals(server.get("aggregateSha256").getAsString());
        return new SideAudit(valid, clientSide, serverSide,
                client.get("aggregateSha256").getAsString(),
                server.get("aggregateSha256").getAsString(),
                client.get("launchCommand").getAsJsonArray().size(),
                server.get("launchCommand").getAsJsonArray().size(),
                "Independent client/server manifests; merged classpath is not an input");
    }

    private static Map<String, Object> acceptance(
            Path taskRoot, Path outputRoot, Map<String, ScenarioAudit> scenarios,
            HistoricalAudit historical, ChangeAudit changes, DocsAudit docs,
            boolean testsPass,
            String javaRevision, int implementationRevision) throws IOException {
        var entries = new ArrayList<Map<String, Object>>();
        var current = "Current I" + implementationRevision;
        entries.add(fa("FA-001", scenarios, List.of("MMR-DOM-LOCAL-001"),
                current + " rerun; historical I-02 remains adopted"));
        entries.add(fa("FA-002", scenarios, List.of("MMR-DOM-REMOTE-AUTH-001",
                "MMR-DOM-REMOTE-PUB-001", "MMR-CLASSPATH-SYSTEM-001"),
                current + " domain and two-sided classpath closure"));
        entries.add(fa("FA-003", scenarios, List.of("MMR-DOM-DEMAND-001",
                "MMR-DOM-PRESENTATION-001", "MMR-CLASSPATH-SYSTEM-001"),
                current + " logical and two-sided closure"));
        entries.add(fa("FA-004", scenarios, List.of("MMR-DOM-DEMAND-001",
                "MMR-DOM-PRESENTATION-001", "MMR-CLASSPATH-SYSTEM-001",
                "MMR-FORGE-DISPOSITION-001"),
                current + " logical/two-sided evidence plus I15 adopted actual-host slice"));
        entries.add(fa("FA-005", scenarios, List.of("MMR-CLASSPATH-SYSTEM-001"),
                current + " side-specific ordinary-JVM runtime"));
        entries.add(fa("FA-006", scenarios, List.of("MMR-CLASSPATH-100-001"),
                current + " exact-100 mode on the FA-005 runtime"));
        entries.add(fa("FA-007", scenarios, List.of("MMR-DOM-LOCAL-001",
                "MMR-DOM-REMOTE-AUTH-001", "MMR-DOM-REMOTE-PUB-001",
                "MMR-DOM-DEMAND-001", "MMR-DOM-PRESENTATION-001",
                "MMR-CLASSPATH-SYSTEM-001", "MMR-CLASSPATH-100-001",
                "MMR-FORGE-DISPOSITION-001"),
                current + " failure claims are limited to the boundary each scenario entered"));
        entries.add(auditFa("FA-008", "MMR-FINAL-COMPLEXITY-001",
                changes.pass() && testsPass ? "Pass" : "Fail",
                normalize(outputRoot.resolve("change-closure.json")),
                "Static structure, packaging and side-closure audit; runtime behavior remains owned by FA-001—FA-007/010",
                implementationRevision));
        entries.add(auditFa("FA-009", "MMR-FINAL-DOCS-001",
                docs.verdict(),
                normalize(outputRoot.resolve("docs-new-audit.json")),
                "Read-only attribution only; it does not validate user-owned documentation content",
                docs.attribution(), implementationRevision));
        entries.add(fa("FA-010", scenarios, List.of("MMR-FORGE-DISPOSITION-001"),
                "Historical I15 disposition adopts four exact I-06 host claims; original run remains C2/D2/C1/I14 partial"));
        verifyCurrentRevisionDispositions(entries, implementationRevision);
        var allPass = historical.valid()
                && entries.stream().allMatch(entry -> "Pass".equals(entry.get("verdict")));
        var anyFail = !historical.valid()
                || entries.stream().anyMatch(entry -> "Fail".equals(entry.get("verdict")));
        var result = new LinkedHashMap<String, Object>();
        result.put("schema", "MMR-FINAL-ACCEPTANCE-1");
        result.put("revisions", Map.of(
                "taskContext", TASK_CONTEXT_REVISION,
                "decision", DECISION_REVISION,
                "contract", CONTRACT_REVISION,
                "implementation", implementationRevision));
        result.put("authorityRevisions", Map.of(
                "java", javaRevision,
                "native", changes.nativeBaseline(),
                "docs", docs.current().get("head")));
        result.put("aggregateVerdict", allPass ? "Pass"
                : anyFail ? "Fail" : "Unreviewable");
        result.put("reviewDisposition", "Pending independent Final Review");
        result.put("acceptances", entries);
        result.put("historicalIntegrityArtifact",
                normalize(outputRoot.resolve("historical-integrity.json")));
        result.put("optionalObservations", Map.of(
                "players200", "Not run",
                "players300", "Not run",
                "soak", "Not run",
                "fuzz", "Not run",
                "lowMemory", "Not run",
                "physicalReclamation", "Not run"));
        result.put("uncoveredBoundaries", List.of(
                "Other operating systems, GPUs, Iris and shader-pack combinations",
                "Long-duration behavior and physical heap/RSS/GPU reclamation timing"));
        result.put("taskRoot", normalize(taskRoot));
        result.put("evidenceRoot", normalize(outputRoot));
        return result;
    }

    static Map<String, Object> fa(String id,
                                  Map<String, ScenarioAudit> scenarios,
                                  List<String> scenarioIds,
                                  String disposition) {
        var evidence = new ArrayList<Map<String, Object>>();
        var pass = true;
        for (var scenarioId : scenarioIds) {
            var scenario = scenarios.get(scenarioId);
            pass &= scenarioSupportsFinalAcceptance(scenario, id);
            if (scenario != null) {
                evidence.add(Map.of(
                        "scenarioId", scenarioId,
                        "verdictArtifact", scenario.verdictArtifact(),
                        "javaRevision", scenario.javaRevision(),
                        "implementationRevision", scenario.implementationRevision(),
                        "disposition", scenarioId.equals("MMR-FORGE-DISPOSITION-001")
                                ? "current disposition over partial historical run"
                                : "current"));
            }
        }
        return Map.of(
                "finalAcceptance", id,
                "scenarioIds", scenarioIds,
                "verdict", pass ? "Pass" : "Fail",
                "evidence", evidence,
                "revisionDisposition", disposition,
                "failureAttribution", pass ? "None" : "Required scenario absent or failed",
                "uncoveredBoundary", scenarioIds.stream()
                        .map(scenarios::get).filter(value -> value != null)
                        .map(ScenarioAudit::evidenceLimit).distinct().toList());
    }

    static boolean scenarioSupportsFinalAcceptance(ScenarioAudit scenario,
                                                   String finalAcceptance) {
        return scenario != null && scenario.valid() && scenario.acceptanceEligible()
                && scenario.finalAcceptance().contains(finalAcceptance);
    }

    private static Map<String, Object> auditFa(String id, String scenarioId,
                                               String verdict, String artifact,
                                               String evidenceLimit,
                                               int implementationRevision) {
        return auditFa(id, scenarioId, verdict, artifact, evidenceLimit,
                "Pass".equals(verdict) ? "None" : "Final audit mismatch",
                implementationRevision);
    }

    private static Map<String, Object> auditFa(String id, String scenarioId,
                                               String verdict, String artifact,
                                               String evidenceLimit,
                                               String failureAttribution,
                                               int implementationRevision) {
        return Map.of(
                "finalAcceptance", id,
                "scenarioIds", List.of(scenarioId),
                "verdict", verdict,
                "evidence", List.of(Map.of(
                        "scenarioId", scenarioId,
                        "verdictArtifact", artifact,
                        "disposition", "current")),
                "revisionDisposition", "Current I" + implementationRevision
                        + " final audit",
                "failureAttribution", "Pass".equals(verdict)
                        ? "None" : failureAttribution,
                "uncoveredBoundary", List.of(evidenceLimit));
    }

    static void verifyCurrentRevisionDispositions(
            List<Map<String, Object>> entries, int implementationRevision) throws IOException {
        var pattern = Pattern.compile("\\bCurrent I([0-9]+)\\b");
        for (var entry : entries) {
            var disposition = String.valueOf(entry.get("revisionDisposition"));
            var matcher = pattern.matcher(disposition);
            while (matcher.find()) {
                require(Integer.parseInt(matcher.group(1)) == implementationRevision,
                        "Stale current Implement revision in "
                                + entry.get("finalAcceptance") + ": " + disposition);
            }
        }
    }

    private static TestSummary testSummary(String task, Path root) {
        var tests = 0L;
        var failures = 0L;
        var errors = 0L;
        var skipped = 0L;
        var suites = 0L;
        var auditErrors = new ArrayList<String>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            try (var files = Files.list(root)) {
                for (var path : files.filter(file -> file.getFileName().toString()
                                .startsWith("TEST-") && file.toString().endsWith(".xml"))
                        .toList()) {
                    var element = factory.newDocumentBuilder().parse(path.toFile())
                            .getDocumentElement();
                    suites++;
                    tests += Long.parseLong(element.getAttribute("tests"));
                    failures += Long.parseLong(element.getAttribute("failures"));
                    errors += Long.parseLong(element.getAttribute("errors"));
                    skipped += Long.parseLong(element.getAttribute("skipped"));
                }
            }
            require(suites > 0, "No JUnit suites found");
        } catch (Throwable failure) {
            auditErrors.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return new TestSummary(task, suites, tests, failures, errors, skipped,
                auditErrors.isEmpty() && failures == 0 && errors == 0,
                List.copyOf(auditErrors));
    }

    private static ContentManifest contentManifest(Path root) throws IOException {
        var entries = new ArrayList<ContentEntry>();
        try (var files = Files.walk(root)) {
            for (var path : files.filter(Files::isRegularFile)
                    .filter(path -> !normalize(root.relativize(path)).startsWith(".git/"))
                    .sorted(Comparator.comparing(path -> normalize(root.relativize(path))))
                    .toList()) {
                entries.add(new ContentEntry(normalize(root.relativize(path)),
                        EvidenceJson.sha256(Files.readAllBytes(path))));
            }
        }
        return new ContentManifest(List.copyOf(entries), contentTreeSha256(entries));
    }

    private static ArtifactIndex artifactIndex(Path root) throws IOException {
        var entries = new ArrayList<ArtifactEntry>();
        for (var name : List.of("acceptance.json", "change-closure.json",
                "docs-new-audit.json", "docs-new-manifest.json",
                "historical-integrity.json", "verification.json", "run.json")) {
            var path = root.resolve(name);
            entries.add(new ArtifactEntry(name,
                    EvidenceJson.sha256(Files.readAllBytes(path))));
        }
        for (var name : List.of("domain", "classpath-smoke", "classpath-system",
                "classpath-exact-100")) {
            entries.add(new ArtifactEntry(name + "/", treeHash(root.resolve(name))));
        }
        return new ArtifactIndex(List.copyOf(entries));
    }

    private static void copyTree(Path source, Path target) throws IOException {
        require(Files.isDirectory(source), "Evidence input is missing: " + source);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory,
                                                     BasicFileAttributes attributes)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)),
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String treeHash(Path root) throws IOException {
        var lines = new ArrayList<String>();
        try (var files = Files.walk(root)) {
            for (var path : files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> normalize(root.relativize(path))))
                    .toList()) {
                lines.add(EvidenceJson.sha256(Files.readAllBytes(path)) + "  "
                        + normalize(root.relativize(path)));
            }
        }
        return EvidenceJson.sha256((String.join("\n", lines) + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String contentTreeSha256(List<ContentEntry> entries) {
        var manifestLines = entries.stream()
                .sorted(Comparator.comparing(ContentEntry::path))
                .map(entry -> entry.sha256() + "  " + entry.path())
                .toList();
        return EvidenceJson.sha256((String.join("\n", manifestLines) + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String git(Path root, String... args) throws IOException {
        return runGit(root, args).stdoutText().stripTrailing();
    }

    private static byte[] gitBytes(Path root, String... args) throws IOException {
        return runGit(root, args).stdout();
    }

    static GitCommandOutput runGit(Path root, String... args) throws IOException {
        var command = new ArrayList<String>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(List.of(args));
        var stderrPath = Files.createTempFile("ysm-git-stderr-", ".log");
        try {
            var process = new ProcessBuilder(command)
                    .redirectError(stderrPath.toFile()).start();
            var stdout = process.getInputStream().readAllBytes();
            var exit = process.waitFor();
            var stderr = Files.readAllBytes(stderrPath);
            require(exit == 0, "git command failed (" + exit + ")\nstdout:\n"
                    + normalizeText(stdout) + "\nstderr:\n" + normalizeText(stderr));
            return new GitCommandOutput(stdout, stderr);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running git", failure);
        } finally {
            Files.deleteIfExists(stderrPath);
        }
    }

    private static String normalizeText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static List<String> gitStatus(Path root) throws IOException {
        return gitStatusEntries(root).stream().map(GitStatusEntry::display).toList();
    }

    private static List<GitStatusEntry> gitStatusEntries(Path root) throws IOException {
        return parsePorcelainV1Z(runGit(root, "status", "--porcelain=v1", "-z",
                "--untracked-files=normal"));
    }

    static List<GitStatusEntry> parsePorcelainV1Z(GitCommandOutput output)
            throws IOException {
        var fields = output.stdoutNulDelimited();
        var result = new ArrayList<GitStatusEntry>();
        for (var index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            require(field.length() >= 4 && field.charAt(2) == ' ',
                    "Malformed porcelain v1 -z status entry");
            var code = field.substring(0, 2);
            var path = field.substring(3);
            String originalPath = null;
            if (code.indexOf('R') >= 0 || code.indexOf('C') >= 0) {
                require(index + 1 < fields.size(),
                        "Rename/copy porcelain status is missing its original path");
                originalPath = fields.get(++index);
            }
            result.add(new GitStatusEntry(code, path, originalPath));
        }
        return List.copyOf(result);
    }

    private static GitStatusEntry baselineStatusEntry(String status) throws IOException {
        require(status.length() >= 4 && status.charAt(2) == ' ',
                "Malformed frozen baseline status entry");
        var path = status.substring(3);
        String originalPath = null;
        var rename = path.indexOf(" -> ");
        if (rename >= 0) {
            originalPath = path.substring(0, rename);
            path = path.substring(rename + 4);
        }
        return new GitStatusEntry(status.substring(0, 2), path, originalPath);
    }

    private static List<GitStatusEntry> baselineStatusEntries(List<String> statuses)
            throws IOException {
        var result = new ArrayList<GitStatusEntry>();
        for (var status : statuses) {
            result.add(baselineStatusEntry(status));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> docsStatusDelta(
            Path docsRoot, List<GitStatusEntry> baseline, List<GitStatusEntry> current)
            throws IOException {
        var baselineSet = new LinkedHashSet<>(baseline);
        var currentSet = new LinkedHashSet<>(current);
        var result = new ArrayList<Map<String, Object>>();
        for (var status : current) {
            if (!baselineSet.contains(status)) {
                result.add(docsStatusDeltaEntry(docsRoot, status, "added-current-status"));
            }
        }
        for (var status : baseline) {
            if (!currentSet.contains(status)) {
                result.add(docsStatusDeltaEntry(docsRoot, status, "removed-current-status"));
            }
        }
        return List.copyOf(result);
    }

    private static DocsDeltaInventory docsDeltaPaths(
            Path docsRoot, String baselineHead,
            List<Map<String, Object>> statusDelta, ContentManifest current)
            throws IOException {
        var paths = new TreeSet<String>();
        var diagnostics = new ArrayList<GitDiagnostic>();
        var diff = runGit(docsRoot, "diff", "--no-renames", "--name-only", "-z",
                baselineHead);
        paths.addAll(diff.stdoutNulDelimited());
        retainGitDiagnostic(diagnostics,
                List.of("diff", "--no-renames", "--name-only", "-z", baselineHead),
                diff);
        var untracked = runGit(docsRoot, "ls-files", "--others",
                "--exclude-standard", "-z");
        paths.addAll(untracked.stdoutNulDelimited());
        retainGitDiagnostic(diagnostics,
                List.of("ls-files", "--others", "--exclude-standard", "-z"),
                untracked);
        for (var delta : statusDelta) {
            var path = String.valueOf(delta.get("path"));
            if (path.endsWith("/")) {
                current.entries().stream().map(ContentEntry::path)
                        .filter(entry -> entry.startsWith(path)).forEach(paths::add);
            } else {
                paths.add(path);
            }
        }
        var currentHashes = new TreeMap<String, String>();
        current.entries().forEach(entry -> currentHashes.put(entry.path(), entry.sha256()));
        var result = new ArrayList<DocsDelta>();
        for (var path : paths) {
            var baselineHash = "Absent";
            try {
                baselineHash = EvidenceJson.sha256(
                        gitBytes(docsRoot, "show", baselineHead + ":" + path));
            } catch (IOException missingAtBaselineHead) {
                // The explicit attribution event owns the unavailable frozen-worktree hash.
            }
            result.add(new DocsDelta(path, baselineHash,
                    currentHashes.getOrDefault(path, "Absent")));
        }
        return new DocsDeltaInventory(List.copyOf(result), List.copyOf(diagnostics));
    }

    private static void retainGitDiagnostic(
            List<GitDiagnostic> diagnostics, List<String> arguments,
            GitCommandOutput output) {
        var stderr = output.stderrLines();
        if (!stderr.isEmpty()) {
            diagnostics.add(new GitDiagnostic(List.copyOf(arguments), stderr));
        }
    }

    static DocsAttributionAudit auditDocsAttribution(
            Path taskRoot, String baselineHead, int baselineFiles, String baselineTree,
            String currentHead, ContentManifest current, List<String> currentStatus,
            List<DocsDelta> changedPaths, boolean exact) {
        var path = taskRoot.resolve("implement/docs-new-attribution.json");
        if (exact) {
            return new DocsAttributionAudit(normalize(path), "NotRequired", "None",
                    List.of());
        }
        if (!Files.isRegularFile(path)) {
            return new DocsAttributionAudit(normalize(path), "Missing", "None",
                    List.of("No explicit docs-new actor event was supplied"));
        }
        try {
            var source = object(path);
            require("MMR-DOCS-ATTRIBUTION-1".equals(source.get("schema").getAsString()),
                    "Docs attribution schema mismatch");
            var event = source.getAsJsonObject("event");
            var eventId = event.get("id").getAsString();
            var actor = event.get("actor").getAsString();
            require(!eventId.isBlank() && !event.get("occurredAt").getAsString().isBlank(),
                    "Docs attribution event identity is incomplete");
            if ("task".equalsIgnoreCase(actor)) {
                return new DocsAttributionAudit(normalize(path), "TaskOwned", eventId,
                        List.of("The attribution event identifies the task as actor"));
            }
            require("user".equalsIgnoreCase(actor),
                    "Docs attribution actor must be user or task");

            var baseline = source.getAsJsonObject("baseline");
            require(baselineHead.equals(baseline.get("head").getAsString())
                            && baselineFiles == baseline.get("regularFiles").getAsInt()
                            && baselineTree.equals(baseline.get("treeSha256").getAsString()),
                    "Docs attribution baseline identity mismatch");
            var finalState = source.getAsJsonObject("final");
            require(currentHead.equals(finalState.get("head").getAsString())
                            && current.entries().size()
                            == finalState.get("regularFiles").getAsInt()
                            && current.treeSha256().equals(
                            finalState.get("treeSha256").getAsString())
                            && currentStatus.equals(strings(finalState.getAsJsonArray("status"))),
                    "Docs attribution final identity mismatch");

            var expected = new TreeMap<String, DocsDelta>();
            changedPaths.forEach(change -> expected.put(change.path(), change));
            var attributed = new TreeMap<String, JsonObject>();
            for (var value : source.getAsJsonArray("changes")) {
                var change = value.getAsJsonObject();
                var relative = change.get("path").getAsString().replace('\\', '/');
                require(!relative.isBlank() && !Path.of(relative).isAbsolute()
                                && !relative.startsWith("../") && !relative.contains("/../"),
                        "Docs attribution path is invalid: " + relative);
                require(attributed.put(relative, change) == null,
                        "Docs attribution path is duplicated: " + relative);
            }
            var currentHashes = new TreeMap<String, String>();
            current.entries().forEach(entry ->
                    currentHashes.put(entry.path(), entry.sha256()));
            for (var entry : attributed.entrySet()) {
                var change = entry.getValue();
                var before = change.get("beforeSha256").getAsString();
                var after = change.get("afterSha256").getAsString();
                require(validContentHash(before),
                        "Docs attribution before hash is invalid: " + entry.getKey());
                require(currentHashes.getOrDefault(entry.getKey(), "Absent").equals(after),
                        "Docs attribution final hash mismatch: " + entry.getKey());
                require(!before.equals(after),
                        "Docs attribution contains a no-op path: " + entry.getKey());
                if (!expected.containsKey(entry.getKey())) {
                    require("Absent".equals(after),
                            "Docs attribution path is absent from the machine inventory: "
                                    + entry.getKey());
                }
            }
            var reconstructedBaseline = new TreeMap<String, String>();
            current.entries().forEach(entry ->
                    reconstructedBaseline.put(entry.path(), entry.sha256()));
            for (var entry : attributed.entrySet()) {
                var before = entry.getValue().get("beforeSha256").getAsString();
                if ("Absent".equals(before)) {
                    reconstructedBaseline.remove(entry.getKey());
                } else {
                    reconstructedBaseline.put(entry.getKey(), before);
                }
            }
            var reconstructedEntries = reconstructedBaseline.entrySet().stream()
                    .map(entry -> new ContentEntry(entry.getKey(), entry.getValue()))
                    .toList();
            require(reconstructedEntries.size() == baselineFiles
                            && baselineTree.equals(contentTreeSha256(reconstructedEntries)),
                    "Docs attribution before hashes do not reconstruct the frozen baseline");
            return new DocsAttributionAudit(normalize(path), "UserAttributed", eventId,
                    List.of());
        } catch (Throwable failure) {
            return new DocsAttributionAudit(normalize(path), "Invalid", "None",
                    List.of(failure.getClass().getSimpleName() + ": "
                            + failure.getMessage()));
        }
    }

    private static boolean validContentHash(String value) {
        return "Absent".equals(value) || value.matches("[0-9a-f]{64}");
    }

    private static Map<String, Object> docsStatusDeltaEntry(
            Path docsRoot, GitStatusEntry status, String kind) throws IOException {
        var pathText = status.path();
        var path = docsRoot.resolve(pathText).normalize();
        var result = new LinkedHashMap<String, Object>();
        result.put("kind", kind);
        result.put("status", status.display());
        result.put("path", pathText.replace('\\', '/'));
        if (status.originalPath() != null) {
            result.put("originalPath", status.originalPath().replace('\\', '/'));
        }
        result.put("baselineWorktreeSha256", "Unavailable: frozen baseline retained only aggregate tree hash");
        if (Files.isRegularFile(path)) {
            result.put("currentSha256", EvidenceJson.sha256(Files.readAllBytes(path)));
        }
        try {
            result.put("headSha256", EvidenceJson.sha256(
                    gitBytes(docsRoot, "show", "HEAD:" + pathText.replace('\\', '/'))));
        } catch (IOException missingAtHead) {
            result.put("headSha256", "Not present at HEAD");
        }
        return Map.copyOf(result);
    }

    private static List<String> statusBlock(String baseline) throws IOException {
        var marker = "## Existing user-owned working-tree state";
        var start = baseline.indexOf(marker);
        require(start >= 0, "Docs baseline status section is missing");
        var open = baseline.indexOf("```text", start);
        var close = baseline.indexOf("```", open + 7);
        require(open >= 0 && close >= 0, "Docs baseline status block is missing");
        var block = baseline.substring(open + 7, close).stripTrailing();
        if (block.startsWith("\r\n")) {
            block = block.substring(2);
        } else if (block.startsWith("\n")) {
            block = block.substring(1);
        }
        return lines(block);
    }

    private static String match(Pattern pattern, String input, String label)
            throws IOException {
        var matcher = pattern.matcher(input);
        require(matcher.find(), "Missing " + label);
        return matcher.group(1);
    }

    private static String docsBaselineHead(Path taskRoot) throws IOException {
        return match(BASELINE_HEAD, Files.readString(
                        taskRoot.resolve("contract/docs-new-readonly-baseline.md"),
                        StandardCharsets.UTF_8),
                "docs baseline HEAD");
    }

    static boolean nativeCandidateMatches(NativeCandidateIdentity candidate,
                                          String actualRevision,
                                          List<String> workingTreeStatus) {
        return candidate.revision().equals(actualRevision)
                && workingTreeStatus.isEmpty();
    }

    private static List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.replace("\r\n", "\n").lines().filter(line -> !line.isBlank())
                .toList();
    }

    private static List<String> addedLines(String diff) {
        return diff.lines().filter(line -> line.startsWith("+")
                && !line.startsWith("+++")).map(line -> line.substring(1)).toList();
    }

    private static boolean forbiddenProductionMechanism(String line) {
        var lower = line.toLowerCase();
        return lower.contains("new thread") || lower.contains("executors.")
                || lower.contains("scheduledexecutor") || lower.contains("system.gc")
                || lower.contains("resync") || lower.contains("dualread")
                || lower.contains("dualwrite") || lower.contains("testmode")
                || lower.contains("@deprecated");
    }

    private static String category(String path) {
        if (path.startsWith("src/mock")) return "automation";
        if (path.startsWith("src/test/")) return "test";
        if (path.equals("build.gradle") || path.startsWith("buildSrc/")
                || path.startsWith("gradle/")) return "build";
        if (path.startsWith("src/main/java/com/elfmcys/ysm/api/")) return "API";
        if (path.startsWith("src/main/proto/") || path.contains("/schema/")) return "schema";
        if (path.startsWith("src/main/resources/")) return "config";
        if (path.endsWith(".h") || path.endsWith(".hpp") || path.endsWith(".c")
                || path.endsWith(".cc") || path.endsWith(".cpp")) return "ABI";
        if (path.startsWith("docs/")) return "docs";
        if (path.startsWith("src/main/java/")) return "production";
        return "task-artifact";
    }

    private static boolean isText(Path path) {
        var name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".gradle")
                || name.endsWith(".md") || name.endsWith(".json")
                || name.endsWith(".toml") || name.endsWith(".properties")
                || name.endsWith(".xml") || name.endsWith(".proto")
                || name.endsWith(".txt") || name.endsWith(".groovy");
    }

    private static List<String> strings(JsonArray array) {
        var result = new ArrayList<String>();
        array.forEach(value -> result.add(value.getAsString()));
        return List.copyOf(result);
    }

    private static Map<String, List<String>> immutableLists(
            Map<String, List<String>> source) {
        var result = new TreeMap<String, List<String>>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private static JsonObject object(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Path absolute(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void writeNew(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, EvidenceJson.canonicalBytes(value),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    record ExpectedIdentity(String javaRevision, int implementationRevision,
                            Map<String, String> authorityRevisions,
                            Map<String, String> configuration) {
    }

    private record GroupAudit(String name, String path, boolean valid,
                              String treeSha256, Map<String, ScenarioAudit> scenarios,
                              List<String> errors) {
    }

    record ScenarioAudit(String scenarioId, boolean valid, String verdict,
                         boolean acceptanceEligible,
                         List<String> finalAcceptance,
                         String verdictArtifact, String inputSha256,
                         String javaRevision, int implementationRevision,
                         String evidenceLimit) {
    }

    private record HistoricalAudit(boolean valid, int reviewedArtifacts,
                                   List<Map<String, Object>> artifacts,
                                   String dispositionArtifact,
                                   String dispositionSha256) {
    }

    record ContentEntry(String path, String sha256) {
    }

    record ContentManifest(List<ContentEntry> entries, String treeSha256) {
    }

    private record DocsAudit(String verdict, Map<String, Object> baseline,
                              Map<String, Object> current,
                              List<DocsDelta> changedPaths,
                              List<GitDiagnostic> gitDiagnostics,
                              List<Map<String, Object>> statusTransitions,
                             List<String> documentationCandidates,
                             boolean candidateComplete,
                             List<ForbiddenDependencyMatch> forbiddenDependencyMatches,
                             String attributionArtifact, String attributionStatus,
                             String attributionEvent, List<String> attributionErrors,
                             String attribution) {
    }

    record DocumentationCandidateAudit(List<String> inventory, boolean complete,
                                       String disposition) {
    }

    record ForbiddenDependencyMatch(String consumer, String path) {
    }

    record DocsAttributionAudit(String path, String status, String eventId,
                                List<String> errors) {
    }

    record DocsDelta(String path, String baselineCommitSha256,
                     String currentSha256) {
    }

    private record DocsDeltaInventory(List<DocsDelta> changedPaths,
                                      List<GitDiagnostic> gitDiagnostics) {
    }

    record GitDiagnostic(List<String> arguments, List<String> stderr) {
    }

    record GitStatusEntry(String code, String path, String originalPath) {
        String display() {
            return code + " " + (originalPath == null
                    ? path : originalPath + " -> " + path);
        }
    }

    record GitCommandOutput(byte[] stdout, byte[] stderr) {
        String stdoutText() {
            return normalizeText(stdout);
        }

        List<String> stdoutLines() {
            return lines(stdoutText());
        }

        List<String> stdoutNulDelimited() throws IOException {
            if (stdout.length == 0) {
                return List.of();
            }
            require(stdout[stdout.length - 1] == 0,
                    "Git NUL-delimited output is unterminated");
            var values = new String(stdout, StandardCharsets.UTF_8)
                    .split("\u0000", -1);
            return List.copyOf(Arrays.asList(values)
                    .subList(0, values.length - 1));
        }

        List<String> stderrLines() {
            return lines(normalizeText(stderr));
        }
    }

    private record ChangeAudit(boolean pass, String javaBaseline,
                               String javaCandidate, List<String> diff,
                               Map<String, List<String>> categories,
                               List<String> javaWorkingTree,
                               List<String> taskOwnedDirty,
                               String nativeBaseline, String nativeCandidate,
                               List<String> nativeWorkingTree,
                               String nativeCandidateManifest,
                               String nativeCandidateManifestSha256,
                               String nativeRuntimeLibrarySha256,
                               List<String> forbiddenProductionMechanisms,
                               List<String> forbiddenChangeCategories,
                               SideAudit sideClosure,
                               List<Map<String, String>> productionSeams,
                               List<String> compatibilityDisposition,
                               String releasePackaging) {
    }

    private record SideAudit(boolean valid, String clientDistribution,
                             String serverDistribution, String clientClasspathSha256,
                             String serverClasspathSha256, int clientCommandElements,
                             int serverCommandElements, String conclusion) {
    }

    private record TestSummary(String task, long suites, long tests, long failures,
                               long errors, long skipped, boolean pass,
                               List<String> auditErrors) {
    }

    private record ArtifactEntry(String path, String sha256) {
    }

    private record ArtifactIndex(List<ArtifactEntry> artifacts) {
    }
}

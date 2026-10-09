package com.elfmcys.ysm.mock.supervisor;

import com.elfmcys.ysm.mock.evidence.EvidenceJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Machine-checkable CO-018 disposition without rewriting historical artifacts. */
final class HistoricalEvidenceDisposition {
    private HistoricalEvidenceDisposition() {
    }

    static Result inspect(Path projectRoot, Path taskRoot) {
        var evidence = taskRoot.resolve("implement/evidence");
        var records = new ArrayList<Record>();
        records.add(inspectGroup(evidence.resolve("i-01/pass"), 1, "adopted",
                List.of("evidence serialization", "canonical hashing",
                        "append-only sink", "index linkage"),
                List.of("product acceptance verdict"), false, projectRoot));
        records.add(inspectGroup(evidence.resolve("i-02/pass"), 2, "adopted",
                List.of("FA-001 candidate", "local-boundary FA-007 candidate"),
                List.of("classpath system", "Forge host", "current revision identity"),
                true, projectRoot));
        records.add(inspectGroup(evidence.resolve("i-03/pass"), 3, "partial",
                List.of("FA-002 component", "remote/session FA-007 component"),
                List.of("single two-sided run", "physical side closure", "FA-005"),
                false, projectRoot));
        records.add(inspectGroup(evidence.resolve("i-04/pass"), 4, "partial",
                List.of("FA-003 component", "FA-004 logical-owner component"),
                List.of("complete two-sided round trip", "actual owner thread"),
                false, projectRoot));
        records.add(inspectGroup(evidence.resolve("i-05/pass"), 5, "partial",
                List.of("FA-006 server-half action and terminal ledger"),
                List.of("client receive", "client activation", "two-sided close",
                        "revised FA-006"), false, projectRoot));
        records.add(inspectGroup(evidence.resolve("i-06/run-9b9b63852"), 14,
                "partial", List.of("FA-010 host claim candidate",
                        "FA-004/FA-007 host slices"),
                List.of("FA-005", "FA-006", "side-specific ordinary JVM closure",
                        "FA-009 docs identity"), false, projectRoot));

        var plan = taskRoot.resolve("implement/task-07-final-closure.md")
                .toAbsolutePath().normalize();
        var planChecks = new ArrayList<String>();
        String planHash = null;
        if (Files.isRegularFile(plan)) {
            try {
                planHash = EvidenceJson.sha256(Files.readAllBytes(plan));
                planChecks.add("plan path and bytes are readable");
            } catch (IOException error) {
                planChecks.add("plan hash failed: " + error.getMessage());
            }
        } else {
            planChecks.add("plan artifact is missing");
        }
        records.add(new Record(normalize(plan), planHash,
                Map.of("contract", 1, "decision", 2, "implementation", 14,
                        "taskContext", 2), "9b9b638522eafbb2d16c76e4f12d4c36bc9bee01",
                "superseded", List.of(),
                List.of("nine-FA aggregate", "Forge-owned FA-005"),
                new Integrity(Files.isRegularFile(plan), 0, 0, true,
                        List.copyOf(planChecks), null)));

        var i02 = records.get(1);
        return new Result(List.copyOf(records), records.size(),
                i02.disposition().equals("adopted") && i02.integrity().valid(),
                records.stream().filter(record -> record.integrity().valid()).count());
    }

    private static Record inspectGroup(Path root, int expectedImplementation,
                                       String maximumDisposition,
                                       List<String> admissibleClaims,
                                       List<String> excludedClaims,
                                       boolean reviewLocalOracle, Path projectRoot) {
        var absolute = root.toAbsolutePath().normalize();
        var checks = new ArrayList<String>();
        var revisions = new LinkedHashMap<String, Integer>();
        String javaRevision = null;
        String contentHash = null;
        var indexedScenarios = 0;
        var verifiedLinks = 0;
        var oracleIndependent = !reviewLocalOracle;
        String oracleSourceHash = null;
        var valid = true;
        try {
            require(Files.isDirectory(absolute), "artifact directory is missing");
            contentHash = treeHash(absolute);
            checks.add("content tree SHA-256 recorded");

            var run = parse(absolute.resolve("run.json"));
            var sourceRevisions = run.getAsJsonObject("revisions");
            revisions.put("contract", sourceRevisions.get("contract").getAsInt());
            revisions.put("decision", sourceRevisions.get("decision").getAsInt());
            revisions.put("implementation",
                    sourceRevisions.get("implementation").getAsInt());
            revisions.put("taskContext", sourceRevisions.get("taskContext").getAsInt());
            javaRevision = run.getAsJsonObject("authorityRevisions")
                    .get("java").getAsString();
            require(revisions.equals(Map.of("contract", 1, "decision", 2,
                            "implementation", expectedImplementation, "taskContext", 2)),
                    "original revision identity changed");
            require(javaRevision.matches("[0-9a-f]{40}"),
                    "historical Java revision is not a full commit");
            checks.add("original C2/D2/C1/I" + expectedImplementation
                    + " identity retained");

            var index = absolute.resolve("index.jsonl");
            require(Files.isRegularFile(index), "index.jsonl is missing");
            for (var line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                indexedScenarios++;
                var entry = JsonParser.parseString(line).getAsJsonObject();
                verifyIndexEntry(absolute, entry);
                verifiedLinks++;
            }
            require(indexedScenarios > 0, "index.jsonl contains no scenarios");
            checks.add("all index/input/verdict/inventory hashes and links verified");

            if (reviewLocalOracle) {
                var source = projectRoot.resolve(
                        "src/test/java/com/elfmcys/ysm/model/catalog/"
                                + "ModelManagementLocalCatalogScenarioTest.java");
                var text = Files.readString(source, StandardCharsets.UTF_8);
                oracleSourceHash = EvidenceJson.sha256(Files.readAllBytes(source));
                var inputBeforeAction = text.indexOf(
                        "var inputBytes = EvidenceJson.canonicalBytes(corpus.input())")
                        < text.indexOf("execute(corpus, evidence)");
                var publicProjectionCompared = text.contains(
                        "requireInventory(catalog.current(), corpus.expected");
                var noReflection = !text.contains("java.lang.reflect")
                        && !text.contains("getDeclaredField(");
                oracleIndependent = inputBeforeAction && publicProjectionCompared
                        && noReflection;
                require(oracleIndependent,
                        "I-02 oracle source review did not establish input-before-action "
                                + "and public-projection comparison");
                checks.add("I-02 oracle source fixes corpus input before actions and compares "
                        + "manifest expectations with public catalog snapshots");
            }
        } catch (Throwable error) {
            valid = false;
            checks.add(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        var disposition = valid ? maximumDisposition : "rerun-required";
        return new Record(normalize(absolute), contentHash, Map.copyOf(revisions),
                javaRevision, disposition, admissibleClaims, excludedClaims,
                new Integrity(valid, indexedScenarios, verifiedLinks,
                        oracleIndependent, List.copyOf(checks), oracleSourceHash));
    }

    private static void verifyIndexEntry(Path root, JsonObject entry) throws IOException {
        var scenarioId = entry.get("scenarioId").getAsString();
        var inputHash = entry.get("inputSha256").getAsString();
        var scenario = root.resolve(scenarioId).normalize();
        require(scenario.startsWith(root), "scenario path escaped evidence root");
        require(EvidenceJson.sha256(Files.readAllBytes(scenario.resolve("input.json")))
                        .equals(inputHash),
                "scenario input hash does not match index: " + scenarioId);

        var verdictPath = root.resolve(entry.get("verdictArtifact").getAsString())
                .normalize();
        var inventoryPath = root.resolve(entry.get("inventoryArtifact").getAsString())
                .normalize();
        require(verdictPath.startsWith(root) && inventoryPath.startsWith(root),
                "indexed artifact escaped evidence root");
        var verdict = parse(verdictPath);
        require(verdict.getAsJsonObject("identity").get("inputSha256").getAsString()
                        .equals(inputHash),
                "verdict input hash does not match index: " + scenarioId);
        require(verdict.get("verdict").getAsString()
                        .equals(entry.get("verdict").getAsString()),
                "verdict outcome does not match index: " + scenarioId);

        var inventory = parse(inventoryPath).getAsJsonArray("artifacts");
        for (var element : inventory) {
            var artifact = element.getAsJsonObject();
            var path = scenario.resolve(artifact.get("path").getAsString()).normalize();
            require(path.startsWith(scenario) && Files.isRegularFile(path),
                    "inventory path is absent or escaped: " + path);
            require(EvidenceJson.sha256(Files.readAllBytes(path))
                            .equals(artifact.get("sha256").getAsString()),
                    "inventory hash mismatch: " + path);
        }
    }

    private static JsonObject parse(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static String treeHash(Path root) throws IOException {
        var lines = new ArrayList<String>();
        try (var stream = Files.walk(root)) {
            for (var path : stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> normalize(root.relativize(path))))
                    .toList()) {
                lines.add(EvidenceJson.sha256(Files.readAllBytes(path)) + "  "
                        + normalize(root.relativize(path)));
            }
        }
        return EvidenceJson.sha256((String.join("\n", lines) + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    record Result(List<Record> artifacts, int reviewedArtifacts,
                  boolean i02Adopted, long validArtifacts) {
    }

    record Record(String originalPath, String contentSha256,
                  Map<String, Integer> originalRevisions, String javaRevision,
                  String disposition, List<String> admissibleClaims,
                  List<String> excludedClaims, Integrity integrity) {
    }

    record Integrity(boolean valid, int indexedScenarios, int verifiedLinks,
                     boolean oracleIndependent, List<String> checks,
                     String oracleSourceSha256) {
    }
}

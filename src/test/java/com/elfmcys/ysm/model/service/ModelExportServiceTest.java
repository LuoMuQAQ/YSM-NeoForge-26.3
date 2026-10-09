package com.elfmcys.ysm.model.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelExportServiceTest {
    @Test
    void directArtifactSuffixIsStable() {
        assertEquals("pack/model.mxc",
                ModelExportService.withDirectSuffix("pack/model.mxc"));
        assertEquals("pack/model.MXC",
                ModelExportService.withDirectSuffix("pack/model.MXC"));
        assertEquals("pack/model.ysm.mxc",
                ModelExportService.withDirectSuffix("pack/model.ysm"));
        assertEquals("pack/model.mxc",
                ModelExportService.withDirectSuffix("pack/model"));
        assertEquals("pack/model.ysm",
                ModelExportService.directInputPath("pack/model.ysm"));
        assertEquals("pack/model.mxc",
                ModelExportService.directInputPath("pack/model"));
    }

    @Test
    void clientOwnerReceivesTheWholeExportRequestIncludingAbsentExtra() {
        var observedExtra = new AtomicReference<String>("not-called");
        var expected = Path.of("exported.mxc");
        try (var registration = ModelExportService.registerExporter((path, extra) -> {
            assertEquals("pack/model", path);
            observedExtra.set(extra);
            return CompletableFuture.completedFuture(expected);
        })) {
            assertEquals(expected, ModelExportService.export("pack/model", null).join());
            assertEquals(null, observedExtra.get());
        }
    }
}

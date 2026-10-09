package com.elfmcys.ysm.model.catalog.source;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public record SourceChangeSet(Set<Path> paths, boolean overflow) {
    public SourceChangeSet {
        paths = paths.stream().map(path -> path.toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
    }
}

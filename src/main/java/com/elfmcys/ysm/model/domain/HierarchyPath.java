package com.elfmcys.ysm.model.domain;

import java.util.Objects;

/** Canonical, case-sensitive catalog path. */
public record HierarchyPath(String value) implements Comparable<HierarchyPath> {
    public HierarchyPath {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0
                || value.startsWith("/") || value.endsWith("/")) {
            throw new IllegalArgumentException("Invalid hierarchy path: " + value);
        }
        for (var part : value.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid hierarchy path: " + value);
            }
        }
    }

    @Override
    public int compareTo(HierarchyPath other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

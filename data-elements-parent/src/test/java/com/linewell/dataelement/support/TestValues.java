package com.linewell.dataelement.support;

import java.util.List;

/** Typed assertions at heterogeneous service-response boundaries. */
public final class TestValues {
    private TestValues() {}

    public static <T> List<T> list(Object value, Class<T> elementType) {
        if (!(value instanceof List<?> rows)) {
            throw new AssertionError("Expected a list response");
        }
        return rows.stream().map(elementType::cast).toList();
    }
}

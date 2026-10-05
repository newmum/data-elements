package com.linewell.dataelement.platform.persistence.id;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumericIdTest {

    @Test
    void generatesDistinctNumericSnowflakeIds() {
        String first = NumericId.nextId();
        String second = NumericId.nextId();

        assertTrue(NumericId.isValid(first));
        assertTrue(NumericId.isValid(second));
        assertNotEquals(first, second);
    }
}

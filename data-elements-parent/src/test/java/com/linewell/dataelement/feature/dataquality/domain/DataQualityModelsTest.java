package com.linewell.dataelement.feature.dataquality.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DataQualityModelsTest {
    @Test
    void splitsBillionScaleNumericRangeWithoutIntegerOverflow() {
        var range = new DataQualityModels.NumericRange(
                BigDecimal.ONE,
                new BigDecimal("10000000000")
        );
        var shards = range.split(64);
        assertThat(shards).hasSize(64);
        assertThat(shards.getFirst().lower()).isEqualByComparingTo("1");
        assertThat(shards.getLast().upper()).isEqualByComparingTo("10000000000");
        assertThat(shards.getLast().upperInclusive()).isTrue();
    }
}

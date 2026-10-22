package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.impl;

import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class DataAccessTaskCanvasReferencesTest {
    private final DataAccessAggTaskTServiceImpl service = new DataAccessAggTaskTServiceImpl(null, null);

    @Test
    void inlineConnectionAndPhysicalTableNeverBecomeRegisteredResourceIds() {
        var task = bind(Map.of("jdbcUrl", "jdbc:mysql://fixture.invalid/test", "table", "orders_copy"));
        assertThat(task.getTargetDbId()).isNull();
        assertThat(task.getTargetTableId()).isNull();
    }

    @Test
    void explicitRegistrationAndSelectedDatabaseReferencesArePreserved() {
        var registered = bind(Map.of("registeredDatasourceId", "registered-db", "targetTableId", "registered-table",
                "jdbcUrl", "jdbc:mysql://fixture.invalid/test", "table", "orders_copy"));
        assertThat(registered.getTargetDbId()).isEqualTo("registered-db");
        assertThat(registered.getTargetTableId()).isEqualTo("registered-table");
        var selected = bind(Map.of("selectedDatabaseId", "selected-db", "table", "orders_copy"));
        assertThat(selected.getTargetDbId()).isEqualTo("selected-db");
        assertThat(selected.getTargetTableId()).isNull();
    }

    private DataAccessAggTaskT bind(Map<String, Object> config) {
        var task = new DataAccessAggTaskT();
        ReflectionTestUtils.invokeMethod(service, "fillFromDsl", task,
                Map.of("nodes", List.of(Map.of("category", "sink", "config", config))));
        return task;
    }
}

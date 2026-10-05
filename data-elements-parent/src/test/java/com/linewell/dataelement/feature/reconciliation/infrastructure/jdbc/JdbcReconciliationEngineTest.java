package com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.feature.reconciliation.config.ReconciliationProperties;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Bucket;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Difference;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Endpoint;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.FieldRule;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Listener;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Plan;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Stats;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JdbcReconciliationEngineTest {

    private static final String URL =
            "jdbc:h2:mem:reconciliation;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @BeforeAll
    static void prepare() throws Exception {
        Class.forName("org.h2.Driver");
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("drop table if exists \"source_table\"");
            statement.execute("drop table if exists \"target_table\"");
            statement.execute("""
                    create table "source_table"(
                        "id" bigint primary key,
                        "name" varchar(100),
                        "amount" decimal(20,2)
                    )
                    """);
            statement.execute("""
                    create table "target_table"(
                        "target_id" bigint primary key,
                        "target_name" varchar(100),
                        "target_amount" decimal(20,2)
                    )
                    """);
            statement.execute("""
                    insert into "source_table" values
                    (1, 'Alpha', 10.00), (2, 'Beta', 20.00), (3, 'Gamma', 30.00)
                    """);
            statement.execute("""
                    insert into "target_table" values
                    (1, 'Alpha', 10.00), (2, 'Changed', 20.00), (4, 'Delta', 40.00)
                    """);
        }
    }

    @Test
    void findsMissingExtraAndFieldDifferencesWithBoundedPaging() throws Exception {
        ReconciliationProperties properties = new ReconciliationProperties();
        RegisteredDataSourceResolver resolver = new RegisteredDataSourceResolver(
                null,
                null,
                properties
        );
        JdbcReconciliationEngine engine = new JdbcReconciliationEngine(resolver);
        Endpoint source = new Endpoint(
                "source-db", "source-table", URL, "sa", "",
                "org.h2.Driver", "source_table"
        );
        Endpoint target = new Endpoint(
                "target-db", "target-table", URL, "sa", "",
                "org.h2.Driver", "target_table"
        );
        FieldRule nameRule = new FieldRule(
                "name", "target_name", "VARCHAR", "VARCHAR",
                true, "DEFAULT", null, null, false, "NONE"
        );
        FieldRule amountRule = new FieldRule(
                "amount", "target_amount", "DECIMAL", "DECIMAL",
                true, "DEFAULT", new BigDecimal("0.01"), null, false, "NONE"
        );
        Plan plan = new Plan(
                "tenant-a", "run-a", "policy-a", "CONTENT",
                List.of("id"), List.of("target_id"),
                null, null, null, null, 2, 100,
                BigDecimal.ZERO, false, true, false,
                source, target, List.of(nameRule, amountRule)
        );
        Bucket bucket = new Bucket(
                "bucket-a", 0, null, null, true,
                List.of(), List.of(), new Stats()
        );
        CapturingListener listener = new CapturingListener();

        Map<String, Object> precheck = engine.precheck(plan);
        Stats stats = engine.compare(plan, bucket, listener);

        assertEquals(true, precheck.get("success"));
        assertEquals(3, stats.sourceCount());
        assertEquals(3, stats.targetCount());
        assertEquals(1, stats.matchedCount());
        assertEquals(1, stats.missingTargetCount());
        assertEquals(1, stats.extraTargetCount());
        assertEquals(1, stats.valueMismatchCount());
        assertEquals(3, stats.diffCount());
        assertTrue(listener.differences.stream()
                .anyMatch(value -> "MISSING_TARGET".equals(value.type())));
        assertTrue(listener.differences.stream()
                .anyMatch(value -> "EXTRA_TARGET".equals(value.type())));
        assertTrue(listener.differences.stream()
                .anyMatch(value -> "VALUE_MISMATCH".equals(value.type())));
    }

    private static final class CapturingListener implements Listener {

        private final List<Difference> differences = new ArrayList<>();

        @Override
        public void difference(Difference difference) {
            differences.add(difference);
        }

        @Override
        public void checkpoint(
                List<Object> sourceKey,
                List<Object> targetKey,
                Stats stats
        ) {
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    }
}

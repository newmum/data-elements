package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler.NodeConfigException;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DslCompilerSyncPolicyTest {
    private final NifiClient nifi = mock(NifiClient.class);
    private final List<Processor> processors = new ArrayList<>();
    private final DslCompiler compiler;
    private record Processor(String group, String type, String name, Map<String, String> props, String period, String strategy, String id) {}

    DslCompilerSyncPolicyTest() throws Exception {
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ObjectMapper mapper = new ObjectMapper();
        for (String[] item : List.of(new String[]{"source.mysql", "/manifests/sources/mysql.json"},
                new String[]{"source.oracle", "/manifests/sources/oracle.json"},
                new String[]{"source.oceanbase", "/manifests/sources/oceanbase.json"},
                new String[]{"sink.jdbc", "/manifests/sinks/jdbc-sink.json"})) {
            try (var input = getClass().getResourceAsStream(item[1])) {
                when(registry.get(item[0])).thenReturn(mapper.readValue(input, ComponentManifest.class));
            }
        }
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createSerialBatchGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("batch"));
        when(nifi.createInputPort(anyString(), anyString())).thenReturn(entity("input"));
        AtomicInteger sequence = new AtomicInteger();
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(i -> entity("cs-" + sequence.incrementAndGet()));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class)))
                .thenAnswer(i -> {
                    String id = "processor-" + sequence.incrementAndGet();
                    processors.add(new Processor(i.getArgument(0), i.getArgument(1), i.getArgument(2),
                            new LinkedHashMap<>(i.getArgument(5)), i.getArgument(6), i.getArgument(7), id));
                    return entity(id);
                });
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(i -> entity("connection-" + sequence.incrementAndGet()));
        compiler = new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class));
    }

    @Test
    void databaseSourcesConnectFetchDirectlyToExecuteAndUseCanvasRecordWindows() {
        for (String sourceKey : List.of("source.mysql", "source.oracle", "source.oceanbase")) {
            for (Map<String, Object> options : List.of(Map.<String, Object>of(),
                    Map.<String, Object>of("batchSize", 1234, "maxRowsPerRun", 87654),
                    Map.<String, Object>of("batchSize", "5000", "maxRowsPerRun", "0"))) {
                processors.clear();
                clearInvocations(nifi);
                Pipeline base = flow(options, target());
                Pipeline.Node original = base.dsl().nodes().getFirst();
                Map<String, Object> config = new LinkedHashMap<>(original.config());
                config.put("compatibleMode", "ORACLE");
                config.put("sid", "test");
                var source = new Pipeline.Node(original.id(), sourceKey, original.label(), original.category(), 0, 0, config);
                var pipeline = new Pipeline("p", "record windows", null, null, null,
                        new Pipeline.Dsl(1, List.of(source, base.dsl().nodes().getLast()), base.dsl().edges()),
                        null, null, null, null, null, null, null);
                Map<String, Object> before = new LinkedHashMap<>(config);
                compiler.compile(pipeline);
                Processor fetch = processor("来源/fetch");
                Processor execute = processor("来源/execute");
                assertThat(processors).noneMatch(p -> p.type().endsWith(".ReplaceText"));
                verify(nifi).createConnection("pg", fetch.id(), "PROCESSOR", execute.id(), "PROCESSOR", List.of("success"));
                assertThat(execute.props()).containsEntry("Max Rows Per FlowFile", options.getOrDefault("batchSize", 5000).toString())
                        .containsEntry("Fetch Size", options.getOrDefault("maxRowsPerRun", 20000).toString())
                        .containsEntry("Output Batch Size", "1").containsEntry("Output Empty FlowFile", "false");
                assertThat(fetch.props()).containsEntry(DateTableFetchPolicy.PARTITION_SIZE, "10000");
                assertThat(config).isEqualTo(before);
            }
        }
    }

    @Test
    void invalidRowCountsFailBeforeNativeMutationAndDoNotTruncateDecimalsOrOverflow() {
        for (Map<String, Object> options : List.of(Map.<String, Object>of("batchSize", 0),
                Map.<String, Object>of("batchSize", 1.5), Map.<String, Object>of("maxRowsPerRun", -1),
                Map.<String, Object>of("maxRowsPerRun", "4294967295"))) {
            assertThatThrownBy(() -> compiler.compile(flow(options, target())))
                    .isInstanceOf(NodeConfigException.class).hasMessageContaining("整数");
            verifyNoInteractions(nifi);
        }
    }

    @Test
    void dateFetchUsesRegisteredTimestampForBothPropertiesAndOverridesOldPartitionDefaults() {
        var options = Map.<String, Object>of("syncMode", "FULL_THEN_INCR", "incrementalColumn", "UPDATE_TIME", "batchSize", 5000);
        compiler.compile(flow(options, target()));
        Processor fetch = processor("来源/fetch");
        assertThat(fetch.type()).isEqualTo(DateTableFetchPolicy.TYPE);
        assertThat(fetch.props()).containsEntry("Maximum-value Columns", "UPDATE_TIME")
                .containsEntry(DateTableFetchPolicy.PARTITION_COLUMN, "UPDATE_TIME")
                .containsEntry(DateTableFetchPolicy.PARTITION_SIZE, "10000")
                .containsEntry("fetch-partition-type", "2").containsEntry("fetch-partition-type-count", "1")
                .containsEntry("Max Wait Time", "0 seconds").containsEntry("min-date", null).containsEntry("max-date", null)
                .doesNotContainKeys("Partition Size", "Database Type", "Initial Load Strategy");
        verify(nifi).executeOnPrimaryNode(fetch.id());
        assertThat(options).containsEntry("batchSize", 5000);
    }

    @Test
    void twentyAndOneHundredSourcesCheckExtensionsOnceWithoutReadingPerTable() {

        for (int count : List.of(20, 100)) {
            clearInvocations(nifi);
            var template = flow(Map.of("syncMode", "FULL_THEN_INCR"), target()).dsl().nodes().getFirst();
            List<Pipeline.Node> nodes = new ArrayList<>();
            for (int index = 0; index < count; index++) nodes.add(new Pipeline.Node("s"+index,
                    template.manifestKey(), "source"+index, "source", 0, 0, template.config()));
            var pipeline = new Pipeline("p", "batch", null, null, null, new Pipeline.Dsl(1, nodes, List.of()), null, null, null, null, null, null, null);
            compiler.preflightSources(pipeline);
            verify(nifi, times(1)).requireDateFetchExtension();
            verifyNoMoreInteractions(nifi);
        }
    }

    @Test
    void missingExtensionAndSecondaryWatermarkFailBeforeNativeMutation() {
        doThrow(new IllegalStateException("GenerateDateTableFetch unavailable")).when(nifi).requireDateFetchExtension();
        assertThatThrownBy(() -> compiler.preflightSources(flow(Map.of("syncMode", "FULL_THEN_INCR"), target())))
                .hasMessageContaining("GenerateDateTableFetch");
        verify(nifi).requireDateFetchExtension();
        verifyNoMoreInteractions(nifi);
        clearInvocations(nifi);
        assertThatThrownBy(() -> compiler.preflightSources(flow(Map.of("tieBreakerColumn", "id"), target())))
                .hasMessageContaining("次级");
        verifyNoInteractions(nifi);
    }

    @Test
    void fullAndPeriodicIgnoreOldIncrementalWatermarkAndUseStablePaging() {
        for (String mode : List.of("FULL", "PERIODIC_FULL")) {
            processors.clear();
            clearInvocations(nifi);
            compiler.compile(flow(Map.of("syncMode", mode, "incrementalColumn", "id", "batchSize", 200), target()));
            assertThat(processor("来源/fetch").props()).doesNotContainKey("Maximum-value Columns")
                    .containsEntry("gen-table-custom-orderby-column", "id").containsEntry(DateTableFetchPolicy.PARTITION_SIZE, "10000")
                    .containsEntry(DateTableFetchPolicy.PARTITION_COLUMN, "id");
            if ("PERIODIC_FULL".equals(mode)) {
                verify(nifi, never()).executeOnPrimaryNode(processor("来源/fetch").id());
                verify(nifi).executeOnPrimaryNode(processor("定时全量触发").id());
            } else {
                verify(nifi).executeOnPrimaryNode(processor("来源/fetch").id());
            }
        }
    }

    @Test
    void dateFetchWithoutTimestampFailsBeforeCreatingAnyNativeGroup() {
        assertThatThrownBy(() -> compiler.compile(flow(Map.of("syncMode", "FULL", "incrementalColumn", ""), target())))
                .isInstanceOfSatisfying(NodeConfigException.class, error -> {
                    assertThat(error.nodeId()).isEqualTo("source");
                    assertThat(error.fieldKey()).isEqualTo("incrementalColumn");
                })
                .hasMessageContaining("时间戳");
        verifyNoInteractions(nifi);
    }

    @Test
    void periodicTruncateRunsOnceBeforeFetchingAndAllRecordsStayInTheSerialChildGroup() {
        var result = compiler.compile(flow(Map.of("syncMode", "PERIODIC_FULL", "fullSyncStrategy", "TRUNCATE_RELOAD",
                "incrementalColumn", "id", "schedulingStrategy", "CRON_DRIVEN", "schedulingPeriod", "0 0 2 * * ?"), target()));
        assertThat(result.processGroupId()).isEqualTo("pg");
        Processor trigger = processor("定时全量触发");
        assertThat(trigger.group()).isEqualTo("pg");
        assertThat(trigger.strategy()).isEqualTo("CRON_DRIVEN");
        assertThat(trigger.props()).containsEntry("Batch Size", "1");
        verify(nifi).executeOnPrimaryNode(trigger.id());
        verify(nifi).createSerialBatchGroup(eq("pg"), anyString(), anyDouble(), anyDouble());
        verify(nifi).connectBatchTrigger("pg", trigger.id(), "batch", "input");
        Processor clear = processor("每轮清空/目标");
        assertThat(clear.props()).containsEntry("SQL Statement", "TRUNCATE TABLE `target_table`")
                .containsEntry("Support Fragmented Transactions", "false");
        verify(nifi).createConnection("batch", "input", "INPUT_PORT", clear.id(), "PROCESSOR", List.of());
        verify(nifi).createConnection("batch", clear.id(), "PROCESSOR", processor("来源/fetch").id(), "PROCESSOR", List.of("success"));
        assertThat(processors.stream().filter(p -> !p.id().equals(trigger.id()))).allMatch(p -> "batch".equals(p.group()) && !"CRON_DRIVEN".equals(p.strategy()));
        assertThat(processors.stream().filter(p -> p.type().endsWith(".PutSQL"))).hasSize(1);
        assertThat(processor("目标/put").props()).containsEntry("Statement Type", "INSERT");
    }

    @Test
    void periodicUpsertKeepsTargetsAndValidatesBusinessKey() {
        compiler.compile(flow(Map.of("syncMode", "PERIODIC_FULL"), target()));
        assertThat(processors).noneMatch(p -> p.type().endsWith(".PutSQL"));
        assertThat(processor("目标/put").props()).containsEntry("Statement Type", "UPSERT").containsEntry("Update Keys", "id");
        var noKey = target();
        noKey.remove("targetColumns");
        assertThatThrownBy(() -> compiler.compile(flow(Map.of("syncMode", "PERIODIC_FULL"), noKey))).hasMessageContaining("primary or unique key");
    }

    @Test
    void invalidPeriodicTargetFailsBeforeCreatingAnyNativeGroup() {
        var unsupported = target();
        unsupported.put("dbType", "CLICKHOUSE");
        assertThatThrownBy(() -> compiler.compile(flow(Map.of("syncMode", "PERIODIC_FULL"), unsupported))).hasMessageContaining("不能回退");
        verifyNoInteractions(nifi);
        unsupported.put("dbType", "PostgreSQL");
        assertThatThrownBy(() -> compiler.compile(flow(Map.of("syncMode", "PERIODIC_FULL", "fullSyncStrategy", "TRUNCATE_RELOAD"), unsupported))).hasMessageContaining("MySQL 和达梦");
        verifyNoInteractions(nifi);
    }

    @Test
    void failedChildCreationStillRecordsRootForRecovery() {
        when(nifi.createSerialBatchGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenThrow(new IllegalStateException("child failure"));
        List<String> roots = new ArrayList<>();
        assertThatThrownBy(() -> compiler.compile(flow(Map.of("syncMode", "PERIODIC_FULL"), target()), roots::add)).hasMessage("child failure");
        assertThat(roots).containsExactly("pg");
    }

    @Test
    void incrementalCurrentMaxPreservesNativeInitializationWithoutInventingAPropertyOnDateFetch29() {
        compiler.compile(flow(Map.of("syncMode", "INCREMENTAL", "incrementalColumn", "id", "initialStrategy", "START_AT_CURRENT_MAX",
                "customWherePredicate", "enabled = 1"), target()));
        Processor fetch = processor("来源/fetch");
        assertThat(fetch.type()).endsWith(".QueryDatabaseTableRecord");
        assertThat(fetch.props()).containsEntry("Initial Load Strategy", "Start at Current Maximum Values")
                .containsEntry("Maximum-value Columns", "id").containsEntry("Max Rows Per FlowFile", "5000")
                .containsEntry("Fetch Size", "20000").containsEntry("Output Batch Size", "1")
                .containsEntry("Additional WHERE Clause", "enabled = 1")
                .doesNotContainKey(DateTableFetchPolicy.PARTITION_COLUMN);
        verify(nifi).executeOnPrimaryNode(fetch.id());
        assertThat(processors).noneMatch(p -> p.type().equals(DateTableFetchPolicy.TYPE));
    }

    @Test
    void firstFullThenIncrementalAlwaysBeginsAtTheStartAndSpecifiedValueWorksForIncremental() {
        compiler.compile(flow(Map.of("syncMode", "FULL_THEN_INCR", "incrementalColumn", "id", "initialStrategy", "START_AT_VALUE", "initialValue", "100"), target()));
        assertThat(processor("来源/fetch").props()).containsEntry("Maximum-value Columns", "id").doesNotContainKey("initial.maxvalue.id");
        processors.clear();
        compiler.compile(flow(Map.of("syncMode", "INCREMENTAL", "incrementalColumn", "id", "initialStrategy", "START_AT_VALUE", "initialValue", "100"), target()));
        assertThat(processor("来源/fetch").props()).containsEntry("initial.maxvalue.id", "100");
    }

    @Test
    void legacyModesAndStringFalseMatchTheFrontendContract() {
        assertThat(SyncPolicy.mode(Map.of())).isEqualTo("FULL");
        assertThat(SyncPolicy.mode(Map.of("incrementalColumn", " id "))).isEqualTo("INCREMENTAL");
        assertThat(SyncPolicy.mode(Map.of("syncMode", "INCR"))).isEqualTo("INCREMENTAL");
        assertThat(SyncPolicy.needsCleanupConfirmation(flow(Map.of("syncMode", "FULL", "deleteTargetData", "false"), target()))).isFalse();
        assertThatThrownBy(() -> SyncPolicy.mode(Map.of("syncMode", "typo"))).hasMessageContaining("不支持");
    }

    private Processor processor(String name) { return processors.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow(); }
    private Map<String, Object> target() {
        return new LinkedHashMap<>(Map.of("jdbcUrl", "jdbc:mysql://test/ods", "table", "target_table", "dbType", "MySQL",
                "writerType", "NIFI_PUT_DATABASE_RECORD", "targetColumns", List.of(Map.of("columnName", "id", "dataType", "BIGINT", "primaryKey", true))));
    }
    private Pipeline flow(Map<String, Object> options, Map<String, Object> target) {
        var source = new LinkedHashMap<String, Object>(Map.of("table", "source_table", "host", "test", "database", "source", "incrementalColumn", "UPDATED_AT"));
        source.putAll(options);
        return new Pipeline("test", "同步验证", null, null, null, new Pipeline.Dsl(1, List.of(
                new Pipeline.Node("source", "source.mysql", "来源", "source", 0, 0, source),
                new Pipeline.Node("target", "sink.jdbc", "目标", "sink", 500, 0, target)),
                List.of(new Pipeline.Edge("edge", "source", "target", null))), null, null, null, null, null, null, null);
    }
    private NifiEntity entity(String id) { return new NifiEntity(null, Map.of("id", id), null, null); }
}

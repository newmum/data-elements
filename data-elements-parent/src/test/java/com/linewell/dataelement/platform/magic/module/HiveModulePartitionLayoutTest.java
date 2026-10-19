package com.linewell.dataelement.platform.magic.module;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HiveModulePartitionLayoutTest {
    @ParameterizedTest
    @ValueSource(strings = {"\u0001", "1", "\\001", "\\u0001", "\\u0001             ",
            "\u0001             ", "  1  ", "01", "+1"})
    void ctrlARepresentationsAndFormattedPaddingDoNotRejectValidTables(String delimiter) throws Exception {
        var fixture = fixture("field.delim", delimiter, true, 20);
        var layout = fixture.hive.partitionLayout("warehouse", "events");
        assertThat(layout.get("location")).isEqualTo("hdfs://cluster/custom/events");
        assertThat((List<?>) layout.get("partitions")).hasSize(20);
        verify(fixture.statement, times(3)).executeQuery(anyString());
        verify(fixture.connection).close();
    }

    @Test
    void readsTwoColumnMetadataAndUsesSerializationFormatOnlyWhenFieldDelimiterIsAbsent() throws Exception {
        fixture("field.delim", "\\u0001   ", false, 0).hive.partitionLayout("warehouse", "events");
        fixture("serialization.format", "1   ", true, 0).hive.partitionLayout("warehouse", "events");
        fixture(null, null, true, 0).hive.partitionLayout("warehouse", "events");
        fixture("field.delim", "1", true, 0, "9").hive.partitionLayout("warehouse", "events");
        assertThatThrownBy(() -> fixture("field.delim", "9", true, 0, "1").hive.partitionLayout("warehouse", "events"))
                .hasMessageContaining("field.delim");
        var conflict = fixture("serialization.format", "9", true, 0);
        assertThatThrownBy(() -> conflict.hive.partitionLayout("warehouse", "events"))
                .hasMessageContaining("warehouse").hasMessageContaining("events")
                .hasMessageContaining("serialization.format").hasMessageContaining("9")
                .hasMessageContaining("Ctrl-A");
    }

    @ParameterizedTest
    @ValueSource(strings = {",", "\\t", "\t", "9", " ", "^A", "\\u0002", "1x"})
    void genuinelyDifferentDelimitersRemainBlockedWithTargetAndActualValue(String delimiter) throws Exception {
        var fixture = fixture("field.delim", delimiter, true, 20);
        assertThatThrownBy(() -> fixture.hive.partitionLayout("warehouse", "events"))
                .hasMessageContaining("warehouse").hasMessageContaining("events")
                .hasMessageContaining("field.delim").hasMessageContaining("实际")
                .hasMessageContaining("Ctrl-A").hasMessageContaining("U+0001");
        verify(fixture.statement, never()).executeQuery(startsWith("SHOW PARTITIONS"));
        verify(fixture.connection).close();
    }

    @Test
    void metadataQueryCountIsFixedForTwentyAndOneHundredPartitions() throws Exception {
        for (int count : List.of(20, 100)) {
            var fixture = fixture("field.delim", "\\u0001   ", true, count);
            assertThat((List<?>) fixture.hive.partitionLayout("warehouse", "events").get("partitions")).hasSize(count);
            verify(fixture.statement, times(3)).executeQuery(anyString());
            verify(fixture.statement).setMaxRows(2001);
        }
    }

    private record Fixture(HiveModule hive, Connection connection, Statement statement) {}

    private Fixture fixture(String property, String delimiter, boolean threeColumns, int count, String... fallbackValues) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        ResultSet createRows = rows(List.<String[]>of(new String[]{
                "CREATE TABLE events (id BIGINT) PARTITIONED BY (ods_partition STRING)"}));
        when(statement.executeQuery(startsWith("SHOW CREATE TABLE"))).thenReturn(createRows);
        List<String[]> description = new ArrayList<>();
        description.add(new String[]{"Location:", "hdfs://cluster/custom/events", ""});
        description.add(new String[]{"SerDe Library:", "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe", ""});
        description.add(new String[]{"InputFormat:", "org.apache.hadoop.mapred.TextInputFormat", ""});
        description.add(new String[]{"Storage Desc Params:", "", ""});
        if (property != null) description.add(threeColumns ? new String[]{"", property + "    ", delimiter}
                : new String[]{property + "    ", delimiter, ""});
        for (String fallback : fallbackValues) description.add(new String[]{"", "serialization.format", fallback});
        ResultSet descriptionRows = rows(description);
        when(statement.executeQuery(startsWith("DESCRIBE FORMATTED"))).thenReturn(descriptionRows);
        List<String[]> partitions = new ArrayList<>();
        for (int i = 0; i < count; i++) partitions.add(new String[]{"ods_partition=p_" + i});
        ResultSet partitionRows = rows(partitions);
        when(statement.executeQuery(startsWith("SHOW PARTITIONS"))).thenReturn(partitionRows);
        HiveModule hive = spy(new HiveModule());
        doReturn(connection).when(hive).getConnection();
        return new Fixture(hive, connection, statement);
    }

    private ResultSet rows(List<String[]> rows) throws Exception {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger index = new AtomicInteger(-1);
        when(result.next()).thenAnswer(call -> index.incrementAndGet() < rows.size());
        when(result.getString(anyInt())).thenAnswer(call -> rows.get(index.get())[call.<Integer>getArgument(0) - 1]);
        return result;
    }
}

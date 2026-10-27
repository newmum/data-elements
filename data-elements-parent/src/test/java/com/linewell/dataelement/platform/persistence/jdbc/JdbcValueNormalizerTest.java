package com.linewell.dataelement.platform.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.serial.SerialClob;
import org.junit.jupiter.api.Test;

class JdbcValueNormalizerTest {

    @Test void normalizesJdbcArrayAndPostgresCompatibleJsonValues() throws Exception {
        var array=org.mockito.Mockito.mock(java.sql.Array.class);
        org.mockito.Mockito.when(array.getArray()).thenReturn(new String[]{"第一项","第二项"});
        assertThat(JdbcValueNormalizer.normalize(array)).isEqualTo(List.of("第一项","第二项"));
        org.mockito.Mockito.verify(array).free();
        var json=new org.postgresql.util.PGobject();json.setType("jsonb");json.setValue("{\"业务字段\":1}");
        assertThat(JdbcValueNormalizer.normalize(json)).isEqualTo("{\"业务字段\":1}");
    }

    @Test
    void readsClobText() throws Exception {
        Clob clob = new SerialClob("jdbc:dm://10.231.176.52:5236/test".toCharArray());

        assertThat(JdbcValueNormalizer.text(clob))
                .isEqualTo("jdbc:dm://10.231.176.52:5236/test");
    }

    @Test
    void normalizesNestedJdbcValuesForJsonSerialization() throws Exception {
        Clob clob = new SerialClob("{\"min\":1}".toCharArray());

        Object normalized = JdbcValueNormalizer.normalize(
                Map.of("rules", List.of(Map.of("parameters", clob)))
        );

        assertThat(normalized).isEqualTo(
                Map.of("rules", List.of(Map.of("parameters", "{\"min\":1}")))
        );
    }

    @Test
    void normalizesTypedJdbcResultRows() throws Exception {
        Clob clob = new SerialClob("snapshot".toCharArray());

        List<Map<String, Object>> normalized = JdbcValueNormalizer.maps(
                List.of(Map.of("task_snapshot", clob))
        );

        assertThat(normalized).containsExactly(Map.of("task_snapshot", "snapshot"));
    }

    @Test
    void readsDriverStreamsBeforeJsonSerialization() {
        Map<String, Object> row = Map.of(
                "binary_stream", new ByteArrayInputStream("binary-value".getBytes(StandardCharsets.UTF_8)),
                "character_stream", new StringReader("character-value")
        );

        assertThat(JdbcValueNormalizer.map(row)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "binary_stream", "binary-value",
                "character_stream", "character-value"
        ));
    }
}

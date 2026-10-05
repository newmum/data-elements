package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DslCompilerLookupQueryTest {

    @Test
    void quotesReservedAliasesForDmAndOracleDictionaryQueries() throws Exception {
        String sql = enrichmentQuery(Map.of("dbType", "DM"));

        assertThat(sql)
                .contains("NULLIF(?, '') AS \"TIME\"")
                .contains("NULLIF(?, '') AS ROWKEY")
                .contains("lookup_result.FOREIGN_ABROAD_FLAG_cn AS FOREIGN_ABROAD_FLAG_cn");
    }

    @Test
    void usesMySqlIdentifierQuotingForReservedAliases() throws Exception {
        String sql = enrichmentQuery(Map.of("dbType", "MYSQL"));

        assertThat(sql).contains("NULLIF(?, '') AS `TIME`");
    }

    @Test
    void readsLookupArgumentsFromTheSplitRecordObjectRoot() throws Exception {
        DslCompiler compiler = new DslCompiler(mock(NifiClient.class), mock(ManifestRegistry.class),
                mock(FieldMappingService.class), mock(HiveModule.class));
        Method method = DslCompiler.class.getDeclaredMethod("jsonPath", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(compiler, "id")).isEqualTo("$.id");
        assertThat(method.invoke(compiler, "person-name")).isEqualTo("$['person-name']");
    }

    private String enrichmentQuery(Map<String, Object> dataSource) throws Exception {
        DslCompiler compiler = new DslCompiler(mock(NifiClient.class), mock(ManifestRegistry.class),
                mock(FieldMappingService.class), mock(HiveModule.class));
        FieldMappingService.LookupPlan lookup = new FieldMappingService.LookupPlan(
                "FOREIGN_ABROAD_FLAG_cn", "FOREIGN_ABROAD_FLAG_cn",
                "SELECT DETAIL AS FOREIGN_ABROAD_FLAG_cn FROM T_SH_ZWHJPT_ZDB WHERE CODE = ?",
                List.of("FOREIGN_ABROAD_FLAG"), List.of("__lookup_0_0"), "NULL", dataSource);
        Method method = DslCompiler.class.getDeclaredMethod("buildLookupEnrichmentQuery",
                FieldMappingService.LookupPlan.class, List.class);
        method.setAccessible(true);
        return (String) method.invoke(compiler, lookup, List.of("ROWKEY", "TIME"));
    }
}

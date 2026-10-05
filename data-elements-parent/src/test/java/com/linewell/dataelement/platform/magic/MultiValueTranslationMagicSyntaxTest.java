package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiValueTranslationMagicSyntaxTest {
    private static final Path DIRECTORY = Path.of(
            "db/migrations/resources/multivalue-translation-20261003/magic");

    @Test
    void materializationAndTaskGenerationCompile() {
        for (String file : new String[] {
                "e3320ec899a24426adf5f77e2dab36f9.ms",
                "ods_data_agg_task_ensure_01.ms" }) {
            assertDoesNotThrow(() -> MagicScript.create(
                    Files.readString(DIRECTORY.resolve(file), StandardCharsets.UTF_8), null).compile(), file);
        }
    }

    @Test
    void mysqlLookupHelperProducesExecutableOrderedQuery() throws Exception {
        String query = helperQuery("(SELECT 'U' AS dict_code, '未知' AS dict_name)");
        assertTrue(query.contains("JSON_TABLE(CONCAT('[', REPLACE(JSON_QUOTE(?), ','"));
        assertTrue(query.contains("ORDER BY tokens.ord SEPARATOR ','"));
    }

    @Test
    void mysqlLookupAndTargetWritePreserveOrderDuplicatesAndUnknownCodes() throws Exception {
        String url = System.getenv("MULTIVALUE_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "requires an explicit test JDBC URL");
        String query = helperQuery("(SELECT code AS dict_code, MAX(label) AS dict_name "
                + "FROM codex_multivalue_dict GROUP BY code)");
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of(
                "from", "/source_codes", "to", "/translated", "lookup", Map.of(
                        "sql", query.replace("gender_code_cn", "translated"),
                        "resultColumn", "translated", "multiValue", true,
                        "dataSource", Map.of("dbType", "MYSQL")))));
        var mappingService = new FieldMappingService();
        var plan = mappingService.compilePlan(spec);
        var compiler = new DslCompiler(mock(NifiClient.class), mock(ManifestRegistry.class),
                mappingService, mock(HiveModule.class));
        Method buildQuery = DslCompiler.class.getDeclaredMethod("buildLookupEnrichmentQuery", List.class, List.class);
        buildQuery.setAccessible(true);
        String nifiQuery = (String) buildQuery.invoke(compiler, plan.lookups(), List.of("source_codes"));
        assertTrue(nifiQuery.contains("JSON_TABLE"));
        try (var connection = DriverManager.getConnection(url,
                System.getenv("MULTIVALUE_TEST_JDBC_USER"),
                System.getenv("MULTIVALUE_TEST_JDBC_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE codex_multivalue_dict "
                    + "(code VARCHAR(20), label VARCHAR(100))");
            statement.execute("CREATE TEMPORARY TABLE codex_multivalue_target "
                    + "(source_codes VARCHAR(100), translated VARCHAR(255))");
            statement.execute("INSERT INTO codex_multivalue_dict VALUES ('U','未知'),('F','女')");
            for (String raw : new String[] { "U,F", " U ,X,F,U ", "X,Y" }) {
                try (var lookup = connection.prepareStatement(nifiQuery)) {
                    lookup.setString(1, raw);
                    lookup.setString(2, raw);
                    try (var rows = lookup.executeQuery()) {
                        assertTrue(rows.next());
                        try (var insert = connection.prepareStatement(
                                "INSERT INTO codex_multivalue_target VALUES (?,?)")) {
                            insert.setString(1, rows.getString("source_codes"));
                            insert.setString(2, rows.getString("translated"));
                            insert.executeUpdate();
                        }
                    }
                }
            }
            try (var rows = statement.executeQuery(
                    "SELECT translated FROM codex_multivalue_target ORDER BY source_codes")) {
                assertTrue(rows.next());
                assertTrue("未知,X,女,未知".equals(rows.getString(1)));
                assertTrue(rows.next());
                assertTrue("未知,女".equals(rows.getString(1)));
                assertTrue(rows.next());
                assertTrue(rows.getString(1) == null);
            }
        }
    }

    private String helperQuery(String dictionaryRows) throws Exception {
        String source = Files.readString(DIRECTORY.resolve("ods_data_agg_task_ensure_01.ms"),
                StandardCharsets.UTF_8);
        String helper = source.substring(source.indexOf("// NiFi's ExecuteSQLRecord"),
                source.indexOf("var mappingDsl ="));
        String script = "var text = (value) => value == null ? '' : '' + value\n"
                + "var blank = (value) => text(value).trim() == ''\n"
                + helper + "\nreturn mysqlMultiValueLookupSql('gender_code_cn', 'MYSQL', dictionaryRows, ',')";
        return String.valueOf(MagicScript.create(script, null).execute(
                new MagicScriptContext(Map.of("dictionaryRows", dictionaryRows))));
    }
}

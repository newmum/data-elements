package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.model.Parameter;
import org.ssssssss.magicapi.function.model.FunctionInfo;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

public class DataStandardPageMagicTest {
    private static final String ELEMENTS = "5b83c2702e9b4c9687392a291ca9162a";
    private static final String CODES = "cc413ac10904400d8dcff45b7df52d98";
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private JdbcTemplate jdbc;
    private Map<String, Object> modules;
    private final List<String> reads = new ArrayList<>();

    @BeforeEach
    void fixture() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:standard_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE ALIAS JSON_VALID FOR '" + getClass().getName() + ".validJson'");
        jdbc.execute("CREATE ALIAS JSON_LENGTH FOR '" + getClass().getName() + ".jsonLength'");
        jdbc.execute("""
                CREATE TABLE da_metadata_t (tid VARCHAR(64), tenant_id VARCHAR(64), is_del INT, meta_name VARCHAR(500),
                    meta_code VARCHAR(100), format_pattern VARCHAR(500), publish_status INT, value_domain_id VARCHAR(64),
                    data_category_path VARCHAR(500), field_length INT, created_time TIMESTAMP, updated_time TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE da_metadata_code_t (tid VARCHAR(64), tenant_id VARCHAR(64), is_del INT,
                    dict_name VARCHAR(500), dict_code VARCHAR(64), code_set VARCHAR(64), code_value VARCHAR(64),
                    dict_item_value VARCHAR(20000), data_category_path VARCHAR(500), created_time TIMESTAMP, updated_time TIMESTAMP)
                """);
        var modules = new LinkedHashMap<String, Object>();
        modules.put("tenantRuntime", new TenantMagicModule(null));
        modules.put("tableRead", TaskRuntimeMagicFixture.structuredReads(jdbc, reads::add));
        modules.put("tableSort", new TableSortMagicModule());
        Path root = Path.of("..", "magic");
        if (!Files.isDirectory(root)) root = Path.of("magic");
        String[] content = Files.readString(root.resolve("function/01.通用函数/84.数据标准代码集匹配.ms"))
                .split("================================\\R", 2);
        var metadata = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(content[0], FunctionInfo.class);
        assertThat(metadata.getPath()).isEqualTo("/standardCodeResolution");
        assertThat(metadata.getParameters()).extracting(Parameter::getName).containsExactly("tenantId");
        var function = MagicScript.create(content[1].replaceFirst("(?m)^import tableRead\\R", ""), null);
        modules.put("resolveCodes", (MagicScriptLambdaFunction) (ignored, args) -> function.execute(
                new MagicScriptContext(Map.of(metadata.getParameters().getFirst().getName(), args[0],
                        "tableRead", modules.get("tableRead")))));
        this.modules = modules;
    }

    @Test
    void bothWireContractsUseTwoQueriesForTwentyAndHundredRows() throws Exception {
        for (int index = 0; index < 105; index++) {
            code("code-" + index, "a", 0, "dict-" + index, "set", "__HEADER__", "[1,null]");
            element("element-" + index, "a", 0, "code-" + index, "name", "root", null);
        }
        code("foreign-code", "other", 0, "foreign", "set", "__HEADER__", "[1,2,3]");
        element("foreign-element", "other", 0, "code-0", "foreign", "root", "[0-9]");
        for (String endpoint : List.of(ELEMENTS, CODES)) {
            for (int size : List.of(20, 100)) {
                var result = page(endpoint, Map.of("pageSize", size, "tenantId", "other",
                        "sortField", endpoint.equals(ELEMENTS) ? "dictionaryCount" : "referenceCount"));
                assertThat((Number) result.get("total")).hasToString("105");
                assertThat(rows(result)).hasSize(size).allSatisfy(row -> assertThat(row.get("tenantId")).isEqualTo("a"));
                assertThat(reads).hasSize(2);
                assertThat(reads.getFirst()).doesNotContain("JOIN", "JSON_LENGTH");
                assertThat(reads.getLast()).contains("ROW_NUMBER()", "ORDER BY").containsIgnoringCase("limit");
                System.out.printf("standard %s page size=%d, SQL=%d%n", endpoint.equals(ELEMENTS) ? "elements" : "codes", size, reads.size());
            }
        }
        assertThat(rows(page(ELEMENTS, Map.of("pageNum", 100)))).isEmpty();
        assertThat(reads).hasSize(1);
    }

    @Test
    void codeResolutionKeepsLegacyPrecedenceAndExcludesForeignAndDeletedCandidates() throws Exception {
        code("id", "a", 0, "id-holder", "fallback", "item", "[1,2,3]");
        code("dict-id", "a", 0, "id", "fallback", "__HEADER__", "[1]");
        code("dict-a", "a", 0, "dictionary", "other-set", "__HEADER__", "[1,null]");
        code("dict-z", "a", 0, "dictionary", "other-set", "__HEADER__", "bad-json");
        code("a-set", "a", 0, "different", "dictionary", "__HEADER__", "{}" );
        code("foreign-matching-id", "other", 0, "foreign", "fallback", "__HEADER__", "[1]");
        code("deleted-matching-id", "a", 1, "deleted", "fallback", "__HEADER__", "[1]");
        element("direct", "a", 0, "id", "name", "root", null);
        element("by-dictionary", "a", 0, "dictionary", "name", "root", null);
        element("by-set", "a", 0, "fallback", "name", "root", null);
        element("foreign", "a", 0, "foreign-matching-id", "name", "root", null);
        element("deleted", "a", 0, "deleted-matching-id", "name", "root", null);
        element("retired", "a", 1, "dictionary", "retired", "root", null);
        var list = rows(page(ELEMENTS, Map.of("sortField", "dictionaryCount")));
        assertThat(list).extracting(row -> row.get("tid")).containsExactly("direct", "by-dictionary", "by-set", "deleted", "foreign");
        assertThat(list).extracting(row -> row.get("CodeId")).containsExactly("id", "dict-a", "dict-id", null, null);
        assertThat(list).allSatisfy(row -> assertThat(row.get("CodeRank")).isEqualTo(1));
        var headers = rows(page(CODES, Map.of("sortField", "referenceCount")));
        assertThat(headers).extracting(row -> row.get("tid")).containsExactly("dict-a", "dict-id", "a-set", "dict-z");
        assertThat(headers).extracting(row -> row.get("itemCount").toString()).containsExactly("2", "1", "0", "0");
        assertThat(headers).extracting(row -> row.get("referenceCount").toString()).containsExactly("1", "1", "0", "0");
    }

    @Test
    void filtersLiteralPrefixAndEncodingBeforeCountAndRejectsUnsafeSort() throws Exception {
        element("matching", "a", 0, null, "name%_", "root%_child", "[0-9]");
        element("other-prefix", "a", 0, null, "name%_", "subroot%_child", "[0-9]");
        element("literal-mismatch", "a", 0, null, "nameX", "root%_child", "[0-9]");
        element("empty-pattern", "a", 0, null, "name%_", "root%_child", "  ");
        var page = page(ELEMENTS, Map.of("metaName", "%_", "categoryPath", "root%_", "encodingOnly", true, "publishStatus", "1"));
        assertThat(rows(page)).extracting(row -> row.get("tid")).containsExactly("matching");
        assertThat((Number) page.get("total")).hasToString("1");
        for (String endpoint : List.of(ELEMENTS, CODES)) {
            assertThatThrownBy(() -> page(endpoint, Map.of("sortField", "name;delete")))
                    .hasStackTraceContaining("不支持的排序字段");
            assertThat(reads).isEmpty();
        }
    }

    private void code(String id, String tenant, int deleted, String dictionary, String set, String type, String json) {
        jdbc.update("INSERT INTO da_metadata_code_t(tid,tenant_id,is_del,dict_name,dict_code,code_set,code_value,dict_item_value) VALUES(?,?,?,?,?,?,?,?)",
                id, tenant, deleted, dictionary, dictionary, set, type, json);
    }

    private void element(String id, String tenant, int deleted, String domain, String name, String category, String pattern) {
        jdbc.update("INSERT INTO da_metadata_t(tid,tenant_id,is_del,value_domain_id,meta_name,meta_code,data_category_path,format_pattern,publish_status) VALUES(?,?,?,?,?,?,?,?,1)",
                id, tenant, deleted, domain, name, id, category, pattern);
    }

    private Map<String, Object> page(String id, Map<String, ?> request) throws Exception {
        String source = CanonicalMagicSources.byId(id);
        MagicScript.create(source, null).compile();
        String script = source.replaceAll("(?m)^import (tenantRuntime|tableRead|tableSort|'@/common/standardCodeResolution' as resolveCodes)\\R", "");
        var values = new LinkedHashMap<>(modules);
        values.put("body", request);
        reads.clear();
        return TenantContext.call("a", () -> StructuredValues.objectMap(MagicScript.create(script, null)
                .execute(new MagicScriptContext(values))));
    }

    private static List<Map<String, Object>> rows(Map<String, Object> response) {
        return StructuredValues.objectRows(response.get("list"));
    }

    public static int validJson(String json) {
        return parseJson(json) == null ? 0 : 1;
    }

    public static int jsonLength(String json) {
        return parseJson(json).size();
    }

    private static JsonNode parseJson(String json) {
        try {
            return json == null ? null : JSON.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            return null;
        }
    }
}

package com.linewell.dataelement.platform.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StructuredContainsPredicateAdapterTest {
    private static final Map<String, String> COLUMN_TYPES = Map.of("payload", "LOB");
    private static final String NAMESPACE = "contains";
    private static final int CHUNK_SIZE = 8_000;
    private final StructuredContainsPredicateAdapter adapter = new StructuredContainsPredicateAdapter();

    @Test
    void executesLiteralCaseInsensitiveContainsOnH2Clobs() throws SQLException {
        try (var fixture = new Fixture("MySQL")) {
            String regexSymbols = ".^$*+?()[]{}|\\";
            String sqlShapedLiteral = "x' OR 1 = 1 --";
            fixture.insert("case", "before-AbCdEf-after");
            fixture.insert("chinese", "头部中文匹配尾部");
            fixture.insert("wildcards", "before-%_!-after");
            fixture.insert("wildcard-decoy", "before-abcdef-after");
            fixture.insert("regex", "before-" + regexSymbols + "-after");
            fixture.insert("regex-decoy", "before-anything-after");
            fixture.insert("sql-literal", "before-" + sqlShapedLiteral + "-after");
            fixture.insert("null-value", null);

            assertThat(matches(fixture, "aBcDeF")).containsExactly("case", "wildcard-decoy");
            assertThat(matches(fixture, "中文匹配")).containsExactly("chinese");
            for (String literal : List.of("%", "_", "!", "%_!")) {
                assertThat(matches(fixture, literal)).as("literal %s", literal).containsExactly("wildcards");
            }
            assertThat(matches(fixture, regexSymbols)).containsExactly("regex");
            assertThat(matches(fixture, sqlShapedLiteral)).containsExactly("sql-literal");
            assertThat(matches(fixture, "不存在的关键词")).isEmpty();
            assertThat(matches(fixture, "NULL")).isEmpty();
        }
    }

    @Test
    void searchesAcrossEightThousandCharacterBoundariesAndBeyondThirtyTwoK() throws SQLException {
        try (var fixture = new Fixture("MySQL")) {
            String keyword = "边界AbC%_!.*?尾";
            fixture.insert("a-crossing", "x".repeat(7_999) + keyword + "tail");
            fixture.insert("b-at-boundary", "x".repeat(8_000) + keyword + "tail");
            fixture.insert("c-after-boundary", "x".repeat(8_001) + keyword + "tail");
            fixture.insert("d-after-32k", "x".repeat(32_768 + 97) + keyword + "tail");
            fixture.insert("no-match", "x".repeat(48_000));
            fixture.insert("null-value", null);

            assertThat(matches(fixture, keyword.toLowerCase(Locale.ROOT)))
                    .containsExactly("a-crossing", "b-at-boundary", "c-after-boundary", "d-after-32k");

            String longKeyword = "长".repeat(CHUNK_SIZE - 1) + "尾";
            fixture.insert("e-long-keyword", "x".repeat(32_768) + longKeyword + "tail");
            assertThat(matches(fixture, longKeyword)).containsExactly("e-long-keyword");
            assertThat(matches(fixture, "不匹配的后部")).isEmpty();
        }
    }

    @Test
    void mysqlMetadataReusesInstrAndExecutesWithUnchangedBindingsOnH2() throws SQLException {
        assertPortableDialect("MySQL", "jdbc:mysql://validation/contains", "INSTR");
    }

    @Test
    void kingbaseMetadataReusesStrposWithUnchangedBindingsWithoutClaimingNativeExecution() throws SQLException {
        try (var fixture = new Fixture("PostgreSQL")) {
            var source = new MetadataUrlDataSource(fixture.source, "jdbc:kingbase8://validation/contains");
            String keyword = "中文AbC%_!.*?[x]'尾";
            var fragment = adapter.compile(source, predicate(keyword), COLUMN_TYPES, NAMESPACE);
            assertReusesTableReadRenderer(source, keyword, fragment);
            assertThat(fragment.sql()).startsWith("STRPOS(LOWER(").contains("LOWER(:contains0)")
                    .doesNotContain(keyword, "LIKE", "REGEXP", "?");
            assertThat(fragment.parameters().values()).containsExactly(keyword);
            // H2 2.2.224 的 PostgreSQL 模式也不提供 STRPOS；只验证生成，不伪造实库证据。
            assertThat(source.statements).isZero();
        }
    }

    @Test
    void damengClobSqlBindsOverlappingEightThousandCharacterWindows() throws SQLException {
        try (var fixture = new Fixture("MySQL")) {
            var source = new MetadataUrlDataSource(fixture.source, "jdbc:dm://validation/contains");
            // H2 不支持达梦 DBMS_LOB/CONNECT BY：仅生成 SQL 并核对窗口覆盖，不执行达梦 SQL。
            // 这些断言不是达梦实库集成测试；代理也会阻止任何语句被提交给 H2。
            for (String keyword : List.of("边", "边界AbC%_!.*?[x]'尾", "长".repeat(CHUNK_SIZE - 1) + "尾")) {
                var fragment = adapter.compile(source, predicate(keyword), COLUMN_TYPES, NAMESPACE);
                int expectedStride = CHUNK_SIZE - (keyword.length() - 1);

                assertReusesTableReadRenderer(source, keyword, fragment);
                assertThat(fragment.sql()).contains("EXISTS (SELECT 1", "SELECT \"payload\" AS search_text",
                                "DBMS_LOB.SUBSTR(joined_text.search_text, 8000, (LEVEL - 1) * :contains0 + 1)",
                                "INSTR(LOWER(", "LOWER(:contains1)",
                                "CONNECT BY LEVEL <= CEIL(LENGTH(joined_text.search_text) / (:contains2 * 1.0))")
                        .doesNotContain(keyword, "LIKE", "REGEXP", "?");
                assertThat(fragment.parameters().keySet()).containsExactly("contains0", "contains1", "contains2");
                assertThat(fragment.parameters().values()).containsExactly(expectedStride, keyword, expectedStride);

                // 按 SQL 中实际绑定的步长选择窗口，验证跨 8000 边界和 32k 后部的完整关键词仍被覆盖。
                int stride = ((Number) fragment.parameters().get("contains0")).intValue();
                for (int offset : new int[]{7_999, 8_000, 8_001, 32_768 + 97}) {
                    String text = "x".repeat(offset) + keyword.toLowerCase(Locale.ROOT) + "tail";
                    int windowStart = (offset / stride) * stride;
                    String window = text.substring(windowStart, Math.min(windowStart + CHUNK_SIZE, text.length()));
                    assertThat(window).as("keyword length %s at offset %s", keyword.length(), offset)
                            .containsIgnoringCase(keyword);
                }
                if (keyword.length() > 1) {
                    String crossing = "x".repeat(7_999) + keyword + "tail";
                    assertThat(crossing.substring(0, CHUNK_SIZE)).doesNotContain(keyword);
                    assertThat(crossing.substring(CHUNK_SIZE)).doesNotContain(keyword);
                }
            }
            assertThat(source.statements).isZero();
        }
    }

    @Test
    void damengKeywordsLongerThanAChunkRetainTheSharedInstrFallback() throws SQLException {
        try (var fixture = new Fixture("MySQL")) {
            var source = new MetadataUrlDataSource(fixture.source, "jdbc:dm://validation/contains");
            String keyword = "长".repeat(CHUNK_SIZE) + "尾";
            var fragment = adapter.compile(source, predicate(keyword), COLUMN_TYPES, NAMESPACE);

            // 同样只核对生成结果，不把 H2 当成达梦执行环境。
            assertReusesTableReadRenderer(source, keyword, fragment);
            assertThat(fragment.sql()).contains("INSTR(LOWER(", "LOWER(:contains0)")
                    .doesNotContain(keyword, "DBMS_LOB.SUBSTR", "CONNECT BY", "?");
            assertThat(fragment.parameters()).containsOnlyKeys("contains0");
            assertThat(fragment.parameters().get("contains0")).isEqualTo(keyword);
            assertThat(source.statements).isZero();
        }
    }

    @Test
    void rejectsUnauthorizedColumnsOperatorsAndExtraSqlOrScopeKeysBeforeConnecting() throws SQLException {
        try (var fixture = new Fixture("MySQL")) {
            var source = new MetadataUrlDataSource(fixture.source, "jdbc:mysql://validation/contains");
            // id 是真实存在但没有授权的列；SQL 片段也不能作为列或运算符传入。
            for (String column : List.of("id", "private_payload", "payload OR 1 = 1", "payload; DROP TABLE contains_values")) {
                var invalid = new LinkedHashMap<>(predicate("literal"));
                invalid.put("column", column);
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.compile(source, invalid, COLUMN_TYPES, NAMESPACE), column);
            }
            for (String operator : List.of("like", "regex", "equals", "CONTAINS", "contains OR 1 = 1")) {
                var invalid = new LinkedHashMap<>(predicate("literal"));
                invalid.put("operator", operator);
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.compile(source, invalid, COLUMN_TYPES, NAMESPACE), operator);
            }
            for (String key : List.of("sql", "extraSql", "scope")) {
                var invalid = new LinkedHashMap<>(predicate("literal"));
                invalid.put(key, "scope".equals(key)
                        ? Map.of("tenantId", "validation-other", "range", "ALL") : "OR 1 = 1");
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.compile(source, invalid, COLUMN_TYPES, NAMESPACE), key);
            }
            assertThat(source.connections).isZero();
            assertThat(source.statements).isZero();
        }
    }

    @Test
    void rejectsNonStringValuesAndUnsafeBindingNamespacesBeforeConnecting() throws SQLException {
        try (var fixture = new Fixture("MySQL")) {
            var source = new MetadataUrlDataSource(fixture.source, "jdbc:mysql://validation/contains");
            for (Object value : new Object[]{null, 42, List.of("literal"), Map.of("sql", "OR 1 = 1")}) {
                var invalid = new LinkedHashMap<>(predicate("literal"));
                invalid.put("value", value);
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.compile(source, invalid, COLUMN_TYPES, NAMESPACE));
            }
            for (String namespace : new String[]{null, "", "1invalid", "_invalid", "name) OR 1 = 1", "a".repeat(42)}) {
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.compile(source, predicate("literal"), COLUMN_TYPES, namespace));
            }
            assertThat(source.connections).isZero();
            assertThat(source.statements).isZero();
        }
    }

    private List<String> matches(Fixture fixture, String keyword) {
        var fragment = adapter.compile(fixture.source, predicate(keyword), COLUMN_TYPES, NAMESPACE);
        assertThat(fragment.parameters().values()).containsExactly(keyword);
        return fixture.matches(fixture.source, fragment);
    }

    private void assertPortableDialect(String h2Mode, String metadataUrl, String function) throws SQLException {
        try (var fixture = new Fixture(h2Mode)) {
            var source = new MetadataUrlDataSource(fixture.source, metadataUrl);
            String keyword = "中AbC%_!.*?[x]' OR 1 = 1 --尾";
            fixture.insert("matching", "x".repeat(32_768 + 97) + keyword.toLowerCase(Locale.ROOT) + "tail");
            fixture.insert("decoy", "x".repeat(48_000) + "plain text");
            fixture.insert("null-value", null);
            var fragment = adapter.compile(source, predicate(keyword), COLUMN_TYPES, NAMESPACE);

            assertReusesTableReadRenderer(source, keyword, fragment);
            assertThat(fragment.sql()).startsWith(function + "(LOWER(").contains("LOWER(:contains0)")
                    .doesNotContain(keyword, "LIKE", "REGEXP", "DBMS_LOB", "?");
            assertThat(fragment.parameters().keySet()).containsExactly("contains0");
            assertThat(fragment.parameters().values()).containsExactly(keyword);
            assertThat(source.statements).isZero();
            assertThat(fixture.matches(source, fragment)).containsExactly("matching");
            assertThat(source.statements).isEqualTo(1);
        }
    }

    private static void assertReusesTableReadRenderer(DataSource source, String keyword,
            StructuredContainsPredicateAdapter.Fragment fragment) throws SQLException {
        try (Connection connection = source.getConnection()) {
            var tableRead = new StructuredTableReadMagicModule(new JdbcTemplate(source));
            var compiled = tableRead.compileContains(connection, "payload", "LOB", keyword);
            assertThat(fragment.sql().replaceAll(":" + NAMESPACE + "\\d+", "?"))
                    .isEqualTo(compiled.sql());
            assertThat(fragment.parameters().values()).containsExactlyElementsOf(compiled.parameters());
        }
    }

    private static Map<String, Object> predicate(String keyword) {
        return Map.of("operator", "contains", "column", "payload", "value", keyword);
    }

    private static final class Fixture implements AutoCloseable {
        private final DriverManagerDataSource source;
        private final Connection keepAlive;
        private final JdbcTemplate jdbc;

        private Fixture(String mode) throws SQLException {
            // 只有随机命名的内存库，不读取运行配置或任何业务凭据。
            source = new DriverManagerDataSource("jdbc:h2:mem:contains_validation_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE");
            keepAlive = source.getConnection();
            jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE contains_values (id VARCHAR(64) PRIMARY KEY, payload CLOB)");
        }

        private void insert(String id, String payload) {
            jdbc.update("INSERT INTO contains_values (id, payload) VALUES (?, ?)", statement -> {
                statement.setString(1, id);
                if (payload == null) {
                    statement.setNull(2, Types.CLOB);
                } else {
                    statement.setCharacterStream(2, new StringReader(payload), payload.length());
                }
            });
        }

        private List<String> matches(DataSource executionSource, StructuredContainsPredicateAdapter.Fragment fragment) {
            return new NamedParameterJdbcTemplate(executionSource).queryForList(
                    "SELECT id FROM contains_values WHERE " + fragment.sql() + " ORDER BY id",
                    fragment.parameters(), String.class);
        }

        @Override
        public void close() throws SQLException {
            keepAlive.close();
        }
    }

    private static final class MetadataUrlDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final String metadataUrl;
        private int connections;
        private int statements;

        private MetadataUrlDataSource(DataSource delegate, String metadataUrl) {
            this.delegate = delegate;
            this.metadataUrl = metadataUrl;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrap(delegate.getConnection(username, password));
        }

        private Connection wrap(Connection actual) {
            connections++;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            if ("getMetaData".equals(method.getName())) {
                                DatabaseMetaData metadata = actual.getMetaData();
                                // 只覆盖方言判断所需的 URL，其余元数据仍来自真实 H2。
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class<?>[]{DatabaseMetaData.class}, (ignored, metaMethod, metaArguments) -> {
                                            try {
                                                return "getURL".equals(metaMethod.getName()) ? metadataUrl
                                                        : metaMethod.invoke(metadata, metaArguments);
                                            } catch (InvocationTargetException error) {
                                                throw error.getCause();
                                            }
                                        });
                            }
                            if ("prepareStatement".equals(method.getName()) || "createStatement".equals(method.getName())
                                    || "prepareCall".equals(method.getName())) {
                                statements++;
                                if (metadataUrl.startsWith("jdbc:dm:")) {
                                    throw new AssertionError("达梦分支只生成 SQL，禁止在 H2 执行达梦语句");
                                }
                            }
                            return method.invoke(actual, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }
}
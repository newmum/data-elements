package com.linewell.dataelement.metautil.explorer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Executes discovery predicates, pagination and counts against isolated catalog fixtures. */
class KingBaseMetadataExplorerTest {
    private Connection database;
    private KingBaseMetadataExplorer explorer;
    private DataSourceConfig config;
    private final List<String> queries = new ArrayList<>();
    private final Map<String, Long> schemaIds = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        database = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (var statement = database.createStatement()) {
            statement.execute("CREATE TABLE pg_namespace (oid bigint, nspname varchar(100))");
            statement.execute("CREATE TABLE pg_class (oid bigint, relnamespace bigint, relname varchar(100), relkind varchar(1), reltuples bigint)");
            statement.execute("CREATE TABLE pg_description (objoid bigint, objsubid int, description varchar(100))");
            statement.execute("CREATE TABLE visible_tables (table_schema varchar(100), table_name varchar(100), table_type varchar(20))");
            statement.execute("CREATE TABLE visible_columns (table_schema varchar(100), table_name varchar(100))");
            statement.execute("CREATE TABLE visible_schemata (schema_name varchar(100))");
        }
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String original = invocation.getArgument(0);
            queries.add(original);
            // H2 stands in only for catalog storage and size functions; execute
            // the original discovery WHERE, joins, bound parameters and paging.
            String sql = original.replace("information_schema.tables", "visible_tables")
                    .replace("information_schema.columns", "visible_columns")
                    .replace("information_schema.schemata", "visible_schemata")
                    .replace("pg_total_relation_size(c.oid)", "0")
                    .replace("pg_relation_size(c.oid)", "0")
                    .replace("pg_indexes_size(c.oid)", "0");
            return database.prepareStatement(sql);
        });
        explorer = new KingBaseMetadataExplorer() {
            @Override
            protected Connection getConnection(DataSourceConfig ignored) { return connection; }
        };
        config = new DataSourceConfig();
        config.setSchema("public");
    }

    @AfterEach
    void tearDown() throws Exception { database.close(); }

    @Test
    void allVisibleOwnersExcludeSystemTablesAndViewsButRetainBusinessNamesAndGrants() throws Exception {
        addSystemObjects();
        addTable(101, "public", "sys_user", "r", true);
        addTable(102, "public", "sys_views", "v", true);
        addTable(103, "business", "orders", "r", true);
        addTable(104, "business", "orders_view", "v", true);
        addTable(105, "business", "secret_not_granted", "r", false);
        config.setConnectorProperties(Map.of("metadataCollectAllVisibleOwners", true));

        var tables = explorer.getTables(config);

        assertThat(tables).extracting(table -> table.getSchemaName() + "." + table.getTableName())
                .containsExactly("business.orders", "business.orders_view", "public.sys_user", "public.sys_views");
        assertThat(tables).filteredOn(table -> "VIEW".equals(table.getTableType())).hasSize(2);
        assertThat(queries).hasSize(1);
    }

    @Test
    void bothSchemaListsApplyTheSameCaseInsensitiveSystemFilter() throws Exception {
        addSystemObjects();
        addTable(101, "public", "sys_user", "r", true);
        addTable(102, "business", "orders_view", "v", true);
        addTable(103, "sysbusiness", "orders", "r", true);

        assertThat(explorer.getSchemas(config)).containsExactly("business", "public", "sysbusiness");
        assertThat(explorer.getSchemasWithTables(config)).containsExactly("business", "public", "sysbusiness");
        assertThat(queries).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sys_catalog", "sysmac", "sysaudit", "sys", "SYS_CATALOG",
            "SysMac", "information_schema", "pg_catalog", "pg_toast", "pg_temp_1", "sys_toast"})
    void explicitSystemSchemaIsExcludedFromFullAndPagedDiscovery(String schema) throws Exception {
        addTable(1, schema, "system_table", "r", true);
        addTable(2, schema, "system_view", "v", true);
        config.setSchema(schema);

        assertThat(explorer.getTables(config)).isEmpty();
        var page = explorer.getTablesPage(config, 1, 20, null);
        assertThat(page.getRows()).isEmpty();
        assertThat(page.getTotal()).isZero();
        assertThat(queries).hasSize(3);
    }

    @Test
    void businessObjectsWithSystemLikeTableNamesRemainInFullAndPagedResults() throws Exception {
        addTable(1, "public", "sys_user", "r", true);
        addTable(2, "public", "pg_business", "r", true);
        addTable(3, "public", "sys_report", "v", true);

        assertThat(explorer.getTables(config)).extracting(TableInfo::getTableName)
                .containsExactly("pg_business", "sys_report", "sys_user");
        var page = explorer.getTablesPage(config, 1, 20, "sys_");
        assertThat(page.getRows()).extracting(TableInfo::getTableName).containsExactly("sys_report", "sys_user");
        assertThat(page.getTotal()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void queryCountDoesNotGrowWithTableCount(int size) throws Exception {
        addSystemObjects();
        for (int index = 0; index < size; index++) {
            addTable(100 + index, "public", String.format("business_%03d", index), "r", true);
        }
        config.setConnectorProperties(Map.of("metadataCollectAllVisibleOwners", true));
        assertThat(explorer.getTables(config)).hasSize(size);
        assertThat(queries).hasSize(1);
        queries.clear();

        var page = explorer.getTablesPage(config, 1, size, null);
        assertThat(page.getRows()).hasSize(size);
        assertThat(page.getTotal()).isEqualTo(size);
        assertThat(queries).hasSize(2);
    }

    private void addSystemObjects() throws Exception {
        List<String> schemas = List.of("sys_catalog", "sysmac", "sysaudit", "sys", "SYS_CATALOG",
                "SysMac", "information_schema", "pg_catalog", "pg_toast", "pg_temp_1", "sys_toast");
        for (int index = 0; index < schemas.size(); index++) {
            addTable(index * 2 + 1, schemas.get(index), "system_table", "r", true);
            addTable(index * 2 + 2, schemas.get(index), "system_view", "v", true);
        }
    }

    private void addTable(long id, String schema, String name, String kind, boolean visible) throws Exception {
        if (!schemaIds.containsKey(schema)) {
            long schemaId = schemaIds.size() + 1;
            try (var statement = database.prepareStatement("INSERT INTO pg_namespace VALUES (?,?)")) {
                statement.setLong(1, schemaId);
                statement.setString(2, schema);
                statement.executeUpdate();
            }
            schemaIds.put(schema, schemaId);
        }
        try (var statement = database.prepareStatement("INSERT INTO pg_class VALUES (?,?,?,?,0)")) {
            statement.setLong(1, id);
            statement.setLong(2, schemaIds.get(schema));
            statement.setString(3, name);
            statement.setString(4, kind);
            statement.executeUpdate();
        }
        if (!visible) return;
        try (var statement = database.prepareStatement("INSERT INTO visible_tables VALUES (?,?,?)")) {
            statement.setString(1, schema);
            statement.setString(2, name);
            statement.setString(3, "v".equals(kind) ? "VIEW" : "BASE TABLE");
            statement.executeUpdate();
        }
        try (var statement = database.prepareStatement("INSERT INTO visible_schemata SELECT ? WHERE NOT EXISTS (SELECT 1 FROM visible_schemata WHERE schema_name=?)")) {
            statement.setString(1, schema);
            statement.setString(2, schema);
            statement.executeUpdate();
        }
    }
}

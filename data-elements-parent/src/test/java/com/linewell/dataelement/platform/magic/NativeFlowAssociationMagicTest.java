package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class NativeFlowAssociationMagicTest {
    @Test void pageCountsOnlyTheCurrentTenantAndSourceRoleScopeAtTwentyAndAHundred() throws Exception {
        String source = CanonicalMagicSources.byPath("api/03.数据接入/07.接入登记/08.关联原生流程.ms",
                "ea87bf02b8a843b5a0491ebdc6aa8071");
        MagicScript.create(source, null).compile();
        var script = MagicScript.create(source.replaceAll(
                "(?m)^import (?:dataScope|tableRead|tenantRuntime|.* as (?:nifi|runtime))\\R", ""), null);
        var reads = new AtomicInteger();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:native_flow_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "") {
            @Override public Connection getConnection() throws SQLException {
                var connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("getMetaData")) {
                                var metadata = connection.getMetaData();
                                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                        (ignored, operation, values) -> operation.getName().equals("getURL")
                                                ? "jdbc:mysql://localhost/native_flow_validation" : operation.invoke(metadata, values));
                            }
                            if (method.getName().equals("prepareStatement") && arguments[0] instanceof String sql
                                    && sql.startsWith("SELECT")) {
                                reads.incrementAndGet();
                            }
                            try {
                                return method.invoke(connection, arguments);
                            } catch (InvocationTargetException error) {
                                throw error.getCause();
                            }
                        });
            }
        });
        jdbc.execute("""
                CREATE TABLE db_datasource_t(tid varchar(32), tenant_id varchar(32), db_name varchar(80),
                  is_del int, asset_status int, created_by varchar(32), org_id varchar(32))
                """);
        jdbc.execute("""
                CREATE TABLE db_table_t(tid varchar(32), tenant_id varchar(32), datasource_id varchar(32),
                  table_name varchar(80), table_name_cn varchar(80), business_type varchar(32), is_del int, asset_status int)
                """);
        jdbc.execute("""
                CREATE TABLE data_access_native_flow_t(tid varchar(32), tenant_id varchar(32), source_table_id varchar(32),
                  node_id varchar(32), node_name varchar(80), process_group_id varchar(36), group_name varchar(80),
                  created_by varchar(32), created_at bigint)
                """);
        for (String[] scope : new String[][]{{"tenant-a","mine","user","org"}, {"tenant-a","other","other","other"},
                {"tenant-b","foreign","user","org"}}) {
            jdbc.update("INSERT INTO db_datasource_t VALUES (?,?,?,0,1,?,?)", scope[1], scope[0], scope[1], scope[2], scope[3]);
            jdbc.update("INSERT INTO db_table_t VALUES (?,?,?,?,?,'业务表',0,1)", scope[1],scope[0],scope[1],"test",scope[1]);
        }
        for (int i = 1; i <= 110; i++) {
            jdbc.update("INSERT INTO data_access_native_flow_t VALUES (?,'tenant-a','mine','node','NiFi',?,'验证','user',?)",
                    Integer.toString(i), UUID.randomUUID().toString(), i);
        }
        jdbc.update("INSERT INTO data_access_native_flow_t VALUES ('111','tenant-a','other','node','NiFi','group-1','外部','other',111)");
        jdbc.update("INSERT INTO data_access_native_flow_t VALUES ('112','tenant-b','foreign','node','NiFi','group-2','外部','user',112)");
        var dataScope = mock(DataScopeMagicModule.class);
        var tableRead = new StructuredTableReadMagicModule(jdbc);
        try (var ignored = TenantContext.use("tenant-a")) {
            for (String role : List.of("OWNER", "ORG")) {
                when(dataScope.dataSource()).thenReturn(Map.of("scope", role, "userId", "user", "organizationIds", List.of("org")));
                for (int size : List.of(20, 100)) {
                    reads.set(0);
                    var result = StructuredValues.objectMap(script.execute(new MagicScriptContext(Map.of(
                            "body", Map.of("action", "list", "size", size), "dataScope", dataScope,
                            "tenantRuntime", new TenantMagicModule(null), "tableRead", tableRead))));
                    var rows = StructuredValues.objectRows(result.get("list"));
                    assertThat(((Number) result.get("total")).intValue()).isEqualTo(110);
                    assertThat(rows).hasSize(size).allSatisfy(row -> assertThat(row.get("sourceTableId")).isEqualTo("mine"));
                    assertThat(reads.get()).isEqualTo(2);
                }
            }
        }
    }
}

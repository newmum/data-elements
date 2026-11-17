package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.service.MetadataExplorerService;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.Variables;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class DatasourceConnectivityMagicTest {
    private final MetadataExplorerService metadata = mock(MetadataExplorerService.class);
    private final StructuredSourceProbeService structured = mock(StructuredSourceProbeService.class);

    @Test
    void explicitlyOpenHiveKeepsJdbcEvenWithAnOlderMrsProfile() throws Exception {
        when(metadata.testConnection(any())).thenReturn(true);
        var result = run(Map.of("dbType", "hive", "hiveConnectionMode", "open-source", "hiveProfile", "default",
                "host", "validation.invalid", "database", "business"), this::unexpectedProbe);
        assertThat(result).containsEntry("connected", true);
        verify(metadata).testConnection(any());
    }

    @Test
    void elasticsearchUsesTheRegisteredEndpointAndPort() throws Exception {
        var result = run(Map.of("dbType", "es", "host", "127.0.0.1", "port", 9200,
                "jdbcURL", "http://127.0.0.1:19200"), (context, arguments) -> {
            assertThat(arguments[0]).isEqualTo("source.elasticsearch");
            assertThat(((Map<?, ?>) arguments[1]).get("nodes")).isEqualTo("http://127.0.0.1:19200");
            return ResponseEntity.ok(Map.of("success", true, "version", "validation"));
        });
        assertThat(result).containsEntry("connected", true).containsEntry("version", "validation");
    }

    @Test
    void buildsMrsHiveZooKeeperServiceDiscoveryWithoutAFixedHost() throws Exception {
        when(metadata.testConnection(any())).thenReturn(true);
        var source = new DriverManagerDataSource("jdbc:h2:mem:connectivity;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE db_datasource_t(tid varchar(70), tenant_id varchar(70), is_del int,
                    connection_status varchar(20), updated_time timestamp)
                """);
        jdbc.update("INSERT INTO db_datasource_t(tid,tenant_id,is_del) VALUES ('hive','tenant-a',0),('foreign','tenant-b',0)");
        MagicScriptLambdaFunction saved = (context, args) -> Map.of(
                "dbType", "mrs_hive", "database", "default",
                "zookeeperQuorum", "validation-a:24002,validation-b:24002",
                "zookeeperNamespace", "hiveserver2", "authMode", "KERBEROS",
                "principal", "hive/cluster.example.com@EXAMPLE.COM");
        var bindings = Map.of("db", DatasourceCompletionMagicTest.magicDb(source), "datasourceConnectionConfig", saved);
        var result = run(Map.of("tid", "hive"), this::unexpectedProbe, bindings);
        assertThat(result).containsEntry("connected", true);
        var config = ArgumentCaptor.forClass(DataSourceConfig.class);
        verify(metadata).testConnection(config.capture());
        assertThat(config.getValue().getHost()).isEmpty();
        assertThat(config.getValue().buildJdbcUrl()).isEqualTo(
                "jdbc:hive2://validation-a:24002,validation-b:24002/default"
                    + ";serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2"
                    + ";auth=KERBEROS;principal=hive/cluster.example.com@EXAMPLE.COM");
        assertThat(jdbc.queryForObject("SELECT connection_status FROM db_datasource_t WHERE tid='hive'", String.class))
                .isEqualTo("success");
        assertThat(run(Map.of("tid", "foreign", "tenantId", "tenant-b"), this::unexpectedProbe, bindings))
                .containsEntry("connected", false)
                .containsEntry("error", "数据源不存在或已删除，请返回第一步重新选择数据源");
        assertThat(jdbc.queryForObject("SELECT connection_status FROM db_datasource_t WHERE tid='foreign'", String.class))
                .isNull();
    }

    @Test
    void preservesMissingHostDiagnosticInTheRegistrationResponse() throws Exception {
        var result = run(Map.of("dbType", "mysql", "database", "business"), this::unexpectedProbe);
        assertThat(result).containsEntry("connected", false).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("主机地址不能为空");
        assertThat(result).containsKeys("errorCode", "rawError", "traceId", "errorDetail");
        assertThat(((Map<?, ?>) result.get("errorDetail")).get("path")).isEqualTo("/dst/db/srctest");
    }

    private Object unexpectedProbe(Variables context, Object[] arguments) {
        throw new AssertionError("An explicit JDBC connection must use the metadata explorer");
    }

    private Map<String, Object> run(Map<String, Object> body, MagicScriptLambdaFunction probe) throws IOException {
        return run(body, probe, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> run(Map<String, Object> body, MagicScriptLambdaFunction probe,
                                    Map<String, ?> bindings) throws IOException {
        String script = CanonicalMagicSources.byId("5c1e80431c9b440881fcce698f0b8634");
        MagicScript.create(script, null).compile();
        script = script.replaceAll("(?m)^import ('@/common/|.* as (metadataExplorer|structuredSourceProbe)$|tenantRuntime)"
                + "[^\\r\\n]*\\R", "");
        var inputs = new LinkedHashMap<String, Object>(bindings);
        inputs.putAll(Map.of("body", body, "probeHealth", probe,
                "metadataExplorer", metadata, "structuredSourceProbe", structured,
                "tenantRuntime", new TenantMagicModule(null)));
        var variables = new MagicScriptContext(inputs);
        var compiled = MagicScript.create(script, null);
        return TenantContext.call("tenant-a", () -> (Map<String, Object>) compiled.execute(variables));
    }
}

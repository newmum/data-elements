package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;

class DslCompilerJdbcDriverLocationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/opt/nifi/drivers", "/opt/nifi/drivers/", "CLICKHOUSE=/opt/nifi/clickhouse/"})
    void clickHouseManagedDirectoryLoadsTheMainArtifactAndItsDependencyBundle(String nodeLocation) throws Exception {
        String directory = nodeLocation.contains("=") ? "/opt/nifi/clickhouse" : "/opt/nifi/drivers";
        assertClickHouseDriverLocation(nodeLocation, Map.of(), directory + "/clickhouse-jdbc-0.6.5.jar,"
                + directory + "/clickhouse-jdbc-0.6.5-all.jar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/custom/clickhouse-custom.jar", "/custom/main.jar,/custom/dependencies.jar"})
    void explicitClickHouseNodePathsRemainAuthoritative(String paths) throws Exception {
        assertClickHouseDriverLocation("CLICKHOUSE=" + paths, Map.of(), paths);
    }

    @ParameterizedTest
    @ValueSource(strings = {"driverLocations", "nifiDriverLocations"})
    void explicitClickHouseTaskPathsOverrideTheManagedDirectory(String key) throws Exception {
        String paths = "/custom/task-main.jar,/custom/task-dependencies.jar";
        assertClickHouseDriverLocation("/opt/nifi/drivers/", Map.of(key, paths), paths);
    }

    private void assertClickHouseDriverLocation(String nodeLocation, Map<String, Object> overrides, String expected)
            throws Exception {
        NifiClient nifi = configuredNifi(nodeLocation);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sources/clickhouse.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("source.clickhouse")).thenReturn(manifest);
        var config = new java.util.LinkedHashMap<String, Object>(Map.of("dbType", "CLICKHOUSE",
                "host", "validation.invalid", "database", "validation", "table", "source_table"));
        config.putAll(overrides);
        new DslCompiler(nifi, mock(NifiNodeRuntimeResolver.class), registry,
                mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(credentialPipeline("source.clickhouse", config));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("来源/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Class Name", "com.clickhouse.jdbc.ClickHouseDriver")
                .containsEntry("Database Driver Locations", expected).doesNotContainKey("Password");
        assertThat(manifest.compile().controllerServices().getFirst().properties().get("Database Driver Locations"))
                .isEqualTo("${jdbc.driverLocations}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"test-secret", " padded secret ", "null", "********", " "})
    void optionalCredentialsKeepNativeSemanticsForSourcesAndJdbcTargets(String password) throws Exception {
        for (String component : List.of("source.clickhouse", "source.hive", "source.mysql", "sink.jdbc")) {
            NifiClient nifi = configuredNifi("/opt/nifi/drivers/");
            ManifestRegistry registry = mock(ManifestRegistry.class);
            String path = component.equals("sink.jdbc") ? "/manifests/sinks/jdbc-sink.json"
                    : "/manifests/sources/" + component.substring("source.".length()) + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                when(registry.get(component)).thenReturn(new ObjectMapper().readValue(stream, ComponentManifest.class));
            }
            var config = new java.util.LinkedHashMap<String, Object>(Map.of(
                    "dbType", component.equals("source.hive") ? "HIVE" : component.equals("source.clickhouse") ? "CLICKHOUSE" : "MYSQL",
                    "host", "validation.invalid", "database", "business", "username", "validation-user",
                    "table", "source_table", "incrementalColumn", "updated_at",
                    "hiveConnectionMode", "open-source", "writerType", "NIFI_PUT_DATABASE_RECORD"));
            config.put("password", password);
            new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class))
                    .compile(credentialPipeline(component, config));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
            verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                    eq("来源/dbcp"), properties.capture());
            assertThat(properties.getValue()).containsEntry("Database User", "validation-user");
            if (component.equals("source.hive")) {
                assertThat(properties.getValue()).containsEntry("Database Driver Class Name",
                        "com.linewell.dataelement.metautil.jdbc.NifiHiveJdbcDriver");
                assertThat(properties.getValue().get("Database Connection URL")).startsWith("jdbc:haitong:hive2:");
            }
            if (password == null || password.isEmpty()) assertThat(properties.getValue()).doesNotContainKey("Password");
            else assertThat(properties.getValue()).containsEntry("Password", password);
        }
    }

    @Test
    void absentNullAndEmptyPasswordsReuseOneScopedPoolWithoutCopyingNativeSensitiveMasks() throws Exception {
        NifiClient nifi = configuredNifi("/opt/nifi/drivers/");
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ObjectMapper mapper = new ObjectMapper();
        try (var stream = getClass().getResourceAsStream("/manifests/sources/clickhouse.json")) {
            when(registry.get("source.clickhouse")).thenReturn(mapper.readValue(stream, ComponentManifest.class));
        }
        var applicationServices = new java.util.HashMap<String, List<com.fasterxml.jackson.databind.JsonNode>>();
        for (String scope : List.of("application", "other-application")) {
            applicationServices.put(scope, new java.util.ArrayList<>());
            when(nifi.listControllerServices(scope)).thenAnswer(call -> List.copyOf(applicationServices.get(scope)));
        }
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble(), anyString())).thenReturn(entity("pg"));
        var createdProperties = new java.util.ArrayList<Map<String, String>>();
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
            String scope = call.getArgument(0);
            String id = "service-" + (createdProperties.size() + 1);
            if (applicationServices.containsKey(scope)) {
                Map<String, String> properties = call.getArgument(3);
                createdProperties.add(new java.util.LinkedHashMap<>(properties));
                var masked = new java.util.LinkedHashMap<>(properties);
                if (masked.containsKey("Password")) masked.put("Password", "********");
                applicationServices.get(scope).add(mapper.valueToTree(Map.of("component", Map.of(
                        "id", id, "type", call.getArgument(1), "name", call.getArgument(2), "properties", masked))));
            }
            return entity(id);
        });
        var config = new java.util.LinkedHashMap<String, Object>(Map.of("dbType", "CLICKHOUSE", "host", "validation.invalid",
                "database", "business", "username", "validation-user", "table", "source_table", "incrementalColumn", "updated_at"));
        DslCompiler compiler = new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class));
        var layout = new PipelineGroupOrganizer.Layout("root", "application", "application", "任务", "owned",
                new PipelineGroupOrganizer.Position(0, 0), List.of());
        compiler.compile(credentialPipeline("source.clickhouse", config), null, layout);
        config.put("password", null);
        compiler.compile(credentialPipeline("source.clickhouse", config), null, layout);
        config.put("password", "");
        compiler.compile(credentialPipeline("source.clickhouse", config), null, layout);
        assertThat(applicationServices.get("application")).hasSize(1);
        assertThat(createdProperties.getFirst()).doesNotContainKey("Password");
        config.put("password", "null"); // A real four-character password is distinct from Java null.
        compiler.compile(credentialPipeline("source.clickhouse", config), null, layout);
        compiler.compile(credentialPipeline("source.clickhouse", config), null, layout);
        assertThat(applicationServices.get("application")).hasSize(2);
        assertThat(createdProperties.get(1)).containsEntry("Password", "null");
        config.remove("password");
        var otherLayout = new PipelineGroupOrganizer.Layout("root", "other-application", "other-application", "任务", "owned",
                new PipelineGroupOrganizer.Position(0, 0), List.of());
        compiler.compile(credentialPipeline("source.clickhouse", config), null, otherLayout);
        assertThat(applicationServices.get("other-application")).hasSize(1);
        assertThat(createdProperties).hasSize(3);
    }

    private Pipeline credentialPipeline(String component, Map<String, Object> config) {
        return new Pipeline("validation", "可选JDBC凭据验证", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", component, "来源",
                        component.startsWith("source.") ? "source" : "sink", 0, 0, new java.util.LinkedHashMap<>(config))), List.of()),
                null, null, null, null, null, null, null);
    }

    @Test
    void registeredClickHouseComplexProjectionSurvivesTheParallelHiveInputPolicy() throws Exception {
        NifiClient nifi = configuredNifi("/opt/nifi/drivers/");
        var compiler = new DslCompiler(nifi, mock(ManifestRegistry.class), mock(FieldMappingService.class), mock(HiveModule.class));
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sources/clickhouse.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        var method = DslCompiler.class.getDeclaredMethod("applySourceFetchDatabaseType", ComponentManifest.class,
                String.class, Map.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> config = Map.of("dbType", "CLICKHOUSE", "_runtimeHiveTargetInput", true,
                "sourceColumns", List.of(Map.of("columnName", "inst_id", "dataType", "Int64"),
                        Map.of("columnName", "params", "dataType", "Map(String, String)"),
                        Map.of("columnName", "tags", "dataType", "Array(String)")));
        var properties = new java.util.LinkedHashMap<String, String>();
        method.invoke(compiler, manifest, DateTableFetchPolicy.NATIVE_TYPE, config, properties);
        assertThat(properties.get("Columns to Return")).contains("`inst_id`", "toJSONString(`params`)", "toJSONString(`tags`)");
        assertThat(properties.get("Columns to Return")).doesNotContain("CAST(`inst_id`");
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "installed", "current", "secure-legacy", "secure-current"})
    void openHiveSourceRequiresItsSmallCompatibilityAssetBesideTheOriginalClient(String state) throws Exception {
        String nativeJar = "/opt/nifi/drivers/hive-jdbc-standalone.jar";
        String bridgeJar = "/opt/nifi/drivers/haitong-hive-jdbc-compat-"+(state.endsWith("current")?"1.0.1":"1.0.0")+".jar";
        String locations = !state.equals("missing") ? bridgeJar + "," + nativeJar : nativeJar;
        NifiClient nifi = configuredNifi("HIVE_OPEN_SOURCE=" + locations);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        try (var stream = getClass().getResourceAsStream("/manifests/sources/hive.json")) {
            when(registry.get("source.hive")).thenReturn(new ObjectMapper().readValue(stream, ComponentManifest.class));
        }
        var compiler = new DslCompiler(nifi, mock(NifiNodeRuntimeResolver.class), registry,
                mock(FieldMappingService.class), mock(HiveModule.class), "");
        var configuration=new java.util.LinkedHashMap<String,Object>(Map.of("dbType", "HIVE", "hiveConnectionMode", "open-source",
                "host", "validation.invalid", "database", "business", "table", "source_table", "incrementalColumn", "updated_at"));
        if(state.startsWith("secure-")){
            configuration.putAll(Map.of("authMode","KERBEROS","principal","hive/validation.invalid@VALIDATION.TEST",
                    "userPrincipal","validation@VALIDATION.TEST","keytabPath","/validation/client.keytab","krb5ConfPath","/validation/krb5.conf"));
        }
        var pipeline = credentialPipeline("source.hive",configuration);
        if (state.equals("missing")||state.equals("secure-legacy")) {
            assertThatThrownBy(() -> compiler.compile(pipeline)).hasMessageContaining("haitong-hive-jdbc-compat-1.0.1.jar");
        } else {
            compiler.compile(pipeline);
            @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
            verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"), eq("来源/dbcp"), properties.capture());
            assertThat(properties.getValue()).containsEntry("Database Driver Locations", locations)
                    .containsEntry("Database Driver Class Name", "com.linewell.dataelement.metautil.jdbc.NifiHiveJdbcDriver");
            if(state.startsWith("secure-"))assertThat(properties.getValue().get("Database Connection URL"))
                    .contains("user.keytab=/validation/client.keytab");
            if(state.startsWith("secure-"))assertThat(properties.getValue())
                    .containsEntry(com.linewell.dataelement.metautil.jdbc.HiveKerberosSupport.KRB5_CONF,"/validation/krb5.conf");
        }
    }

    @ParameterizedTest
    @CsvSource({"NCLOB,missing", "NCLOB,installed", "BINARY_FLOAT,missing", "BINARY_FLOAT,installed",
            "BINARY_DOUBLE,missing", "BINARY_DOUBLE,installed", "BLOB,missing", "BLOB,installed"})
    void oracleVendorTargetsRequireAndSelectTheirScopedCompatibilityClient(String physicalType, String state) throws Exception {
        String nativeJar = "/opt/nifi/drivers/ojdbc8-12.2.0.1.jar";
        String locations = state.equals("installed")
                ? "/opt/nifi/drivers/haitong-oracle-jdbc-compat-1.0.4.jar," + nativeJar : nativeJar;
        NifiClient nifi = configuredNifi("ORACLE_TYPED=" + locations + ";ORACLE=" + nativeJar);
        when(nifi.findProcessorType("LinewellPutDatabaseRecord")).thenReturn(java.util.Optional.of(
                "com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
        ManifestRegistry registry = mock(ManifestRegistry.class);
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/jdbc-sink.json")) {
            when(registry.get("sink.jdbc")).thenReturn(new ObjectMapper().readValue(stream, ComponentManifest.class));
        }
        var compiler = new DslCompiler(nifi, mock(NifiNodeRuntimeResolver.class), registry,
                mock(FieldMappingService.class), mock(HiveModule.class), "");
        var config = new java.util.LinkedHashMap<String, Object>(Map.of("dbType", "ORACLE",
                "jdbcUrl", "jdbc:oracle:thin:@validation.invalid:1521/validation", "table", "VALIDATION",
                "writerType", "LINEWELL_PUT_DATABASE_RECORD", "statementType", "INSERT",
                "targetColumns", List.of(Map.of("columnName", "VALUE", "columnType", physicalType, "dataType", "text"))));
        if (state.equals("missing")) {
            assertThatThrownBy(() -> compiler.compile(credentialPipeline("sink.jdbc", config)))
                    .hasMessageContaining("ORACLE_TYPED").hasMessageContaining("haitong-oracle-jdbc-compat-1.0.4.jar");
        } else {
            compiler.compile(credentialPipeline("sink.jdbc", config));
            @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
            verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"), eq("来源/dbcp"), properties.capture());
            assertThat(properties.getValue()).containsEntry("Database Driver Locations", locations)
                    .containsEntry("Database Driver Class Name", "com.linewell.dataelement.metautil.jdbc.NifiOracleJdbcDriver")
                    .containsEntry("Database Connection URL", "jdbc:haitong:oracle:thin:@validation.invalid:1521/validation");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"CLOB", "NUMBER(38,9)", "TIMESTAMP(6)"})
    void ordinaryOracleTargetsKeepTheOriginalDriverAndUrl(String physicalType) throws Exception {
        String nativeJar = "/opt/nifi/drivers/ojdbc8-12.2.0.1.jar";
        NifiClient nifi = configuredNifi("ORACLE=" + nativeJar);
        when(nifi.findProcessorType("LinewellPutDatabaseRecord")).thenReturn(java.util.Optional.of(
                "com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
        ManifestRegistry registry = mock(ManifestRegistry.class);
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/jdbc-sink.json")) {
            when(registry.get("sink.jdbc")).thenReturn(new ObjectMapper().readValue(stream, ComponentManifest.class));
        }
        var config = Map.<String,Object>of("dbType", "ORACLE", "jdbcUrl", "jdbc:oracle:thin:@validation.invalid:1521/validation",
                "table", "VALIDATION", "writerType", "LINEWELL_PUT_DATABASE_RECORD", "statementType", "INSERT",
                "targetColumns", List.of(Map.of("columnName", "VALUE", "columnType", physicalType)));
        new DslCompiler(nifi, mock(NifiNodeRuntimeResolver.class), registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(credentialPipeline("sink.jdbc", config));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"), eq("来源/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations", nativeJar)
                .containsEntry("Database Driver Class Name", "oracle.jdbc.OracleDriver")
                .containsEntry("Database Connection URL", "jdbc:oracle:thin:@validation.invalid:1521/validation");
    }

    @ParameterizedTest
    @CsvSource({
            "source.gaussdb,gaussdb,GAUSSDB_NATIVE,opengaussjdbc.jar,com.huawei.opengauss.jdbc.Driver,opengauss,false",
            "source.gaussdb,gaussdb-jdbc,GAUSSDB_JDBC,gaussdbjdbc.jar,com.huawei.gaussdb.jdbc.Driver,gaussdb,false",
            "sink.jdbc,gaussdb,GAUSSDB_NATIVE,opengaussjdbc.jar,com.huawei.opengauss.jdbc.Driver,opengauss,false",
            "sink.jdbc,gaussdb-jdbc,GAUSSDB_JDBC,gaussdbjdbc.jar,com.huawei.gaussdb.jdbc.Driver,gaussdb,false",
            "source.gaussdb,gaussdb,GAUSSDB_NATIVE,opengaussjdbc.jar,com.huawei.opengauss.jdbc.Driver,opengauss,true",
            "source.gaussdb,gaussdb-jdbc,GAUSSDB_JDBC,gaussdbjdbc.jar,com.huawei.gaussdb.jdbc.Driver,gaussdb,true",
            "sink.jdbc,gaussdb,GAUSSDB_NATIVE,opengaussjdbc.jar,com.huawei.opengauss.jdbc.Driver,opengauss,true",
            "sink.jdbc,gaussdb-jdbc,GAUSSDB_JDBC,gaussdbjdbc.jar,com.huawei.gaussdb.jdbc.Driver,gaussdb,true"
    })
    void nativeGaussSourcesAndTargetsUseTheReviewedDriverNames(
            String component, String variant, String nodeKey, String filename,
            String driverClass, String protocol, boolean explicitMapping) throws Exception {
        String expectedPath = "/opt/nifi/drivers/" + filename;
        NifiClient nifi = configuredNifi(explicitMapping
                ? nodeKey + "=" + expectedPath + ";GAUSSDB=/legacy/other-driver.jar"
                : "/opt/nifi/drivers/");
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        String manifestPath = component.startsWith("source.")
                ? "/manifests/sources/gaussdb.json" : "/manifests/sinks/jdbc-sink.json";
        try (var stream = getClass().getResourceAsStream(manifestPath)) {
            when(registry.get(component)).thenReturn(new ObjectMapper().readValue(stream, ComponentManifest.class));
        }
        String url = "jdbc:" + protocol + "://validation.invalid:8000/business";
        Map<String, Object> config = Map.of("dbType", "GAUSSDB", "gaussdbVariant", variant,
                "jdbcUrl", url, "host", "validation.invalid", "port", "8000", "database", "business",
                "table", "validation_table", "incrementalColumn", "updated_at",
                "writerType", "NIFI_PUT_DATABASE_RECORD");
        Pipeline pipeline = new Pipeline("validation", "GaussDB 驱动交付验证", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("gauss", component, "GaussDB",
                        component.startsWith("source.") ? "source" : "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("GaussDB/dbcp"), properties.capture());
        assertThat(properties.getValue())
                .containsEntry("Database Driver Class Name", driverClass)
                .containsEntry("Database Driver Locations", expectedPath)
                .containsEntry("Database Connection URL", url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source.oracle", "sink.jdbc"})
    void usesOracle8ForSourceAndTargetWhenTheRuntimeNodeConfiguresADirectory(String component) throws Exception {
        assertOracleDriverLocation(component, "/opt/nifi/lib/jdbc/", Map.of(),
                "/opt/nifi/lib/jdbc/ojdbc8-12.2.0.1.jar");
    }

    @Test
    void preservesAnExplicitRuntimeNodeOracleJar() throws Exception {
        assertOracleDriverLocation("source.oracle", "ORACLE=/opt/nifi/custom/oracle.jar", Map.of(),
                "/opt/nifi/custom/oracle.jar");
    }

    @Test
    void preservesAnExplicitTaskOracleJarOverTheRuntimeNodeDirectory() throws Exception {
        assertOracleDriverLocation("source.oracle", "/opt/nifi/lib/jdbc", Map.of(
                "driverLocations", "/opt/nifi/custom/task-oracle.jar"), "/opt/nifi/custom/task-oracle.jar");
    }

    @Test
    void expandsLegacyKingbase8TargetTypeUsingTheCurrentTargetNodesDriverDirectory() throws Exception {
        NifiClient nifi = configuredNifi("/opt/nifi/lib/jdbc/");
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/jdbc-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.jdbc")).thenReturn(manifest);
        Map<String, Object> config = new java.util.LinkedHashMap<>(Map.of(
                "dbType", "kingbase8", "jdbcUrl", "jdbc:kingbase8://example.test:54321/target",
                "username", "target-user", "password", "target-password", "table", "TARGET_TABLE",
                "writerType", "NIFI_PUT_DATABASE_RECORD"));
        Pipeline pipeline = new Pipeline("task", "Kingbase 目标", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("target", "sink.jdbc", "目标表",
                        "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("目标表/dbcp"), properties.capture());
        assertThat(properties.getValue())
                .containsEntry("Database Driver Class Name", "com.kingbase8.Driver")
                .containsEntry("Database Driver Locations", "/opt/nifi/lib/jdbc/kingbase8.jar");
    }

    private void assertOracleDriverLocation(String component, String nodeLocation,
                                           Map<String, Object> overrides, String expected) throws Exception {
        NifiClient nifi = configuredNifi(nodeLocation);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        String manifestPath = "source.oracle".equals(component)
                ? "/manifests/sources/oracle.json" : "/manifests/sinks/jdbc-sink.json";
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream(manifestPath)) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get(component)).thenReturn(manifest);
        Map<String, Object> config = new java.util.LinkedHashMap<>(Map.of(
                "dbType", "ORACLE", "jdbcUrl", "jdbc:oracle:thin:@//example.test:1521/prod",
                "username", "test-user", "password", "test-password", "table", "TEST_TABLE", "incrementalColumn", "UPDATED_AT",
                "writerType", "NIFI_PUT_DATABASE_RECORD"));
        config.putAll(overrides);
        Pipeline pipeline = new Pipeline("task", "Oracle", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("oracle", component, "Oracle",
                        component.startsWith("source.") ? "source" : "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("Oracle/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations", expected);
        if ("source.oracle".equals(component) && overrides.isEmpty() && nodeLocation.endsWith("/")) {
            assertThat(manifest.compile().controllerServices().getFirst().properties()
                    .get("Database Driver Locations")).endsWith("/ojdbc8-12.2.0.1.jar");
        }
    }

    @Test
    void usesTheRuntimeNodesExactOceanBaseJarInsteadOfItsJdbcDirectory() throws Exception {
        NifiClient nifi = configuredNifi(
                "OCEANBASE_ORACLE=/opt/nifi/nifi-current/lib/jdbc/oceanbase-client-2.4.17.jar");
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        when(registry.get("source.oceanbase")).thenReturn(oceanBaseManifest());

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(oceanBasePipeline());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("OceanBase Oracle/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations",
                "/opt/nifi/nifi-current/lib/jdbc/oceanbase-client-2.4.17.jar");
    }

    @Test
    void expandsRuntimeNodeDriverDirectoryUsingTheManagedVendorJar() throws Exception {
        NifiClient nifi = configuredNifi("/opt/nifi/nifi-current/lib/jdbc/");
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        when(registry.get("source.oceanbase")).thenReturn(oceanBaseManifest());

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(oceanBasePipeline());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("OceanBase Oracle/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations",
                "/opt/nifi/nifi-current/lib/jdbc/oceanbase-client-2.4.10.jar");
    }

    @ParameterizedTest
    @CsvSource({
            "source.oceanbase,MYSQL,jdbc:mysql://example.test:2883/prod?useSSL=true&serverTimezone=GMT%2B8",
            "source.mysql,oceanbase-mysql,jdbc:oceanbasemysql://example.test:2883/prod?useSSL=true&serverTimezone=GMT%2B8",
            "source.oceanbase,OCEANBASE_MYSQL,jdbc:oceanbase://example.test:2883/prod?useSSL=true&serverTimezone=GMT%2B8",
            "source.oceanbase,OCEANBASE_MYSQL,"
    })
    void configuresOceanBaseMysqlSourceWithNativeUrlDriverAndJar(String component, String type, String url)
            throws Exception {
        String nativeJar = "/opt/nifi/lib/jdbc/oceanbase-client-2.4.17.jar";
        NifiClient nifi = configuredNifi(
                "MYSQL=/opt/nifi/lib/jdbc/mysql-connector-j-8.4.0.jar;OCEANBASE_MYSQL=" + nativeJar);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver resolver = mock(NifiNodeRuntimeResolver.class);
        when(registry.get("source.oceanbase")).thenReturn(oceanBaseManifest());
        var cfg = new java.util.LinkedHashMap<String, Object>(Map.of(
                "dbType", type, "host", "example.test", "port", "2883", "database", "prod",
                "username", "test-user@tenant#cluster", "password", "test-password",
                "table", "TEST_TABLE", "incrementalColumn", "UPDATED_AT"));
        if (url != null) cfg.put("jdbcURL", url);
        Pipeline pipeline = new Pipeline("task", "OceanBase 接入", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", component, "OceanBase MySQL",
                        "source", 0, 0, cfg)), List.of()), null, null, null, null, null, null, null);

        new DslCompiler(nifi, resolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("OceanBase MySQL/dbcp"), properties.capture());
        String expected = "jdbc:oceanbase://example.test:2883/prod?" + (url == null
                ? "useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true"
                : "useSSL=true&serverTimezone=GMT%2B8");
        assertThat(properties.getValue()).containsEntry("Database Connection URL", expected)
                .containsEntry("Database Driver Class Name", "com.oceanbase.jdbc.Driver")
                .containsEntry("Database Driver Locations", nativeJar)
                .containsEntry("Database User", "test-user@tenant#cluster");
        assertThat(new com.oceanbase.jdbc.Driver().acceptsURL(properties.getValue().get("Database Connection URL"))).isTrue();
    }

    private NifiClient configuredNifi(String jdbcDriverLocations) {
        NifiClient nifi = mock(NifiClient.class);
        var assignedNode = runtimeNode(jdbcDriverLocations);
        when(nifi.jdbcDriverLocation(anyString()))
                .thenAnswer(call -> assignedNode.jdbcDriverLocation(call.getArgument(0)));
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));
        return nifi;
    }

    private ComponentManifest oceanBaseManifest() throws Exception {
        try (var stream = getClass().getResourceAsStream("/manifests/sources/oceanbase.json")) {
            return new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
    }

    private Pipeline oceanBasePipeline() {
        return new Pipeline("task", "OceanBase 接入", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.oceanbase", "OceanBase Oracle",
                        "source", 0, 0, Map.of(
                                "dbType", "OCEANBASE_ORACLE",
                                "compatibleMode", "ORACLE",
                                "jdbcUrl", "jdbc:oceanbase:oracle://example.test:2881/prod",
                                "host", "example.test",
                                "port", "2881",
                                "database", "prod",
                                "username", "test-user",
                                "password", "test-password",
                                "table", "TEST_TABLE", "incrementalColumn", "UPDATED_AT"))), List.of()),
                null, null, null, null, null, null, null);
    }

    private NifiNodeRuntimeResolver.RuntimeNode runtimeNode(String jdbcDriverLocations) {
        return new NifiNodeRuntimeResolver.RuntimeNode("tenant", "node", "leefy-prod", "http://leefy.test",
                "user", "password", false, "root", jdbcDriverLocations);
    }

    private NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }
}

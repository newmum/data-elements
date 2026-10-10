package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class RegisteredDatasourceTypeTest {
    @ParameterizedTest
    @CsvSource({
        "kingbase8,kingbase,KINGBASE,jdbc:kingbase8://db-host:54321/business",
        "KINGBASE,kingbase,KINGBASE,jdbc:kingbase8://db-host:54321/business",
        "tdsql-pg,tdsql-pg,POSTGRESQL,jdbc:postgresql://db-host:5432/business",
        "tdsql_mysql,tdsql-mysql,MYSQL,jdbc:mysql://db-host:3306/business",
        "vastbase,hailiang,POSTGRESQL,jdbc:postgresql://db-host:5432/business",
        "openGauss,gaussdb,GAUSSDB,jdbc:postgresql://db-host:5432/business",
        "dm,dameng,DAMENG,jdbc:dm://db-host:5236/business",
        "MS SQL 2012+,sqlserver,SQLSERVER,jdbc:sqlserver://db-host:1433;databaseName=business",
        "oracle12+,oracle,ORACLE,jdbc:oracle:thin:@//db-host:1521/business",
        "oceanbase_oracle,oceanbase,OCEANBASE_ORACLE,jdbc:oceanbase:oracle://db-host:2881/business",
        "oceanbasemysql,oceanbase,OCEANBASE_MYSQL,jdbc:mysql://db-host:2881/business",
        "mrs-hive,hive,HIVE,jdbc:hive2://db-host:10000/business",
        "hetuengine,hetu,HETU,jdbc:trino://db-host:29861/business",
        "mrsdoris,doris,DORIS,jdbc:mysql://db-host:9030/business",
        "mrsstarrocks,starrocks,STARROCKS,jdbc:mysql://db-host:9030/business",
        "ch,clickhouse,CLICKHOUSE,jdbc:clickhouse://db-host:8123/business",
        "apacheiotdb,iotdb,IOTDB,jdbc:iotdb://db-host:22260/",
        "db2,db2,DB2,jdbc:db2://db-host:50000/business",
        "mariadb,mariadb,MARIADB,jdbc:mariadb://db-host:3306/business",
        "gbase8a,gbase8a,GBASE8A,jdbc:gbase://db-host:5258/business",
        "gbase8s,gbase8s,GBASE8S,jdbc:gbasedbt-sqli://db-host:9088/business",
        "oscar,oscar,OSCAR,jdbc:oscar://db-host:2003/business",
        "highgo,highgo,HIGHGO,jdbc:highgo://db-host:5866/business"
    })
    void aliasesKeepTheirManifestDriverProtocolAndVendor(String raw, String manifest, String type, String protocol) {
        Map<String, Object> config = Map.of("host", "db-host", "database", "business");
        assertEquals("source." + manifest, RegisteredDatasourceType.sourceManifest(raw, config));
        assertEquals(type, RegisteredDatasourceType.configType(raw, config));
        assertTrue(RegisteredDatasourceType.jdbcUrl(raw, config, Map.of()).startsWith(protocol));
    }

    @Test
    void everyShippedSourceManifestIsReachableFromRegistration() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        var files = resolver.getResources("classpath*:manifests/sources/*.json");
        int count = 0;
        for (var file : files) {
            try (var input = file.getInputStream()) {
                String manifest = json.readTree(input).get("key").asText();
                String raw = manifest.substring("source.".length());
                assertEquals(manifest, RegisteredDatasourceType.sourceManifest(raw, Map.of()), file.toString());
                count++;
            }
        }
        assertEquals(31, count);
    }

    @Test
    void everyRegisteredTypeEitherHasARealManifestOrAnExplicitUnsupportedError() {
        Set<DatabaseType> unsupported = Set.of(DatabaseType.MONGODB, DatabaseType.VERTICA, DatabaseType.MAXCOMPUTE);
        for (DatabaseType type : DatabaseType.values()) {
            if (unsupported.contains(type)) {
                assertThrows(IllegalArgumentException.class, () -> RegisteredDatasourceType.sourceManifest(type.getCode(), Map.of()));
            } else {
                assertNotNull(RegisteredDatasourceType.sourceManifest(type.getCode(), Map.of()));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> RegisteredDatasourceType.sourceManifest(null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> RegisteredDatasourceType.sourceManifest("unknown-vendor", Map.of()));
    }

    @Test
    void nonJdbcConnectorsNeverReceiveFabricatedMysqlUrls() {
        for (String raw : Set.of("ftp", "sftp", "api", "minio", "kafka", "hdfs", "hbase", "es")) {
            assertNull(RegisteredDatasourceType.jdbcUrl(raw, Map.of("url", "https://api.test"), Map.of()));
        }
        assertEquals("source.sftp", RegisteredDatasourceType.sourceManifest("ftp", Map.of("ftpProtocol", "sftp")));
    }

    @Test
    void reusesRegisteredJdbcUrlAndOracleSidAndOceanbaseMode() {
        assertEquals("jdbc:kingbase8://registered/business?currentSchema=PUBLIC",
                RegisteredDatasourceType.jdbcUrl("kingbase8", Map.of(), Map.of("jdbc_url",
                        "jdbc:kingbase8://registered/business?currentSchema=PUBLIC")));
        assertEquals("jdbc:oracle:thin:@db:1521:service", RegisteredDatasourceType.jdbcUrl("oracle",
                Map.of("host", "db", "database", "service", "connectionType", "SID"), Map.of()));
        assertEquals("OCEANBASE_ORACLE", RegisteredDatasourceType.configType("oceanbase", Map.of("compatibleMode", "ORACLE")));
        assertEquals("KINGBASE", RegisteredDatasourceType.sinkType("kingbase8", Map.of()));
    }
}

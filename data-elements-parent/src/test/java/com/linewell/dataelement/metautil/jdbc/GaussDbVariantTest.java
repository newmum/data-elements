package com.linewell.dataelement.metautil.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.integration.nifi.canvas.compile.RegisteredDatasourceType;
import com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class GaussDbVariantTest {
    @ParameterizedTest
    @CsvSource({"gaussdb-jdbc,gaussdb", "gaussdb,opengauss"})
    void nativeUrlMatchesTheVerifiedVendorConnection(String product, String protocol) {
        var config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.GAUSSDB);
        config.setHost("validation.invalid");
        config.setDatabase("business");
        config.setConnectorProperties(Map.of("gaussdbVariant", product, "sslMode", "prefer"));
        String base = "jdbc:" + protocol + "://validation.invalid:8000/business?autoBalance=true";
        assertEquals(base, config.buildJdbcUrl());
        config.setSchema("业务");
        config.setConnectorProperties(Map.of("gaussdbVariant", product, "sslMode", "verify-full"));
        assertEquals(base + "&currentSchema=%E4%B8%9A%E5%8A%A1&sslmode=verify-full", config.buildJdbcUrl());
        config.setExtraParams("autoBalance=false&connectTimeout=10");
        assertEquals(base.replace("autoBalance=true", "autoBalance=false&connectTimeout=10"), config.buildJdbcUrl());
        config.setExtraParams("connectTimeout=10");
        assertEquals(base + "&connectTimeout=10", config.buildJdbcUrl());
    }

    @ParameterizedTest
    @CsvSource({"com.huawei.gaussdb.jdbc.Driver,b2ff93c2db4bad4693d5e1b471b470a35ede36489c96efc93de714013c229441,jdbc:gaussdb://validation.invalid:8000/business?autoBalance=true",
            "com.huawei.opengauss.jdbc.Driver,2fb069dc0e6ab5d727738d810f126ab040ae96f8010c56b0c400e1fbe86bc711,jdbc:opengauss://validation.invalid:8000/business?autoBalance=true"})
    void mavenRuntimeContainsExactlyTheApprovedVendorDrivers(String driverClass, String sha256, String url) throws Exception {
        Class<?> type = Class.forName(driverClass);
        var location = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(java.nio.file.Files.isRegularFile(location), "The approved vendor JAR must be a Maven runtime dependency");
        String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(location)));
        assertEquals(sha256, actual);
        assertTrue(((java.sql.Driver) type.getDeclaredConstructor().newInstance()).acceptsURL(url));
    }

    @ParameterizedTest
    @CsvSource({"gaussdb,opengauss,8000,com.huawei.opengauss.jdbc.Driver", "gaussdb-jdbc,gaussdb,8000,com.huawei.gaussdb.jdbc.Driver", "opengauss,opengauss,5432,org.opengauss.Driver",
            "dws,postgresql,8000,org.postgresql.Driver", "mysql,mysql,3306,com.mysql.cj.jdbc.Driver",
            "legacy-postgresql,postgresql,5432,org.postgresql.Driver"})
    void productControlsRegistrationProbeAndCanvasDriver(String product,String protocol,int port,String driver) {
        var values=Map.<String,Object>of("dbType","gaussdb","gaussdbVariant",product,"host","validation.invalid","database","business");
        DataSourceConfig config=new DataSourceConfig();config.setDatabaseType(DatabaseType.GAUSSDB);
        config.setHost("validation.invalid");config.setDatabase("business");config.setConnectorProperties(values);
        assertEquals(driver,config.getJdbcDriverClass());
        assertEquals(port,config.getPortOrDefault());
        assertTrue(config.buildJdbcUrl().startsWith("jdbc:"+protocol+"://validation.invalid:"+port+"/business"));
        assertEquals(product.equals("mysql")?DatabaseType.MYSQL:DatabaseType.GAUSSDB,config.getEffectiveDatabaseType());
        assertEquals(config.buildJdbcUrl(),RegisteredDatasourceType.jdbcUrl("gaussdb",values,Map.of()));
        Object probe=ReflectionTestUtils.invokeMethod(new ConnectionTestController(null,null,null),"resolve","source.gaussdb",values);
        assertEquals(driver,ReflectionTestUtils.getField(probe,"driverClass"));
        assertEquals(config.buildJdbcUrl(),ReflectionTestUtils.getField(probe,"url"));
    }

    @Test void mismatchedOrUnknownProductNeverFallsBackToAnotherVendor() {
        var values=Map.<String,Object>of("gaussdbVariant","gaussdb","jdbcUrl","jdbc:postgresql://validation.invalid/business");
        assertThrows(IllegalArgumentException.class,()->RegisteredDatasourceType.jdbcUrl("gaussdb",values,Map.of()));
        assertThrows(IllegalArgumentException.class,()->GaussDbVariant.resolve("unknown-product"));
    }

    @Test void nativeAndOpenGaussDoNotShareDriverClasses() {
        assertNotEquals(GaussDbVariant.GAUSSDB.driverClass(),GaussDbVariant.OPENGAUSS.driverClass());
        assertNotEquals(GaussDbVariant.GAUSSDB.driverClass(),GaussDbVariant.DWS.driverClass());
    }

    @Test void openHiveBuildsHttpTlsAndNoSaslWithoutMrsProfile() {
        DataSourceConfig config=new DataSourceConfig();config.setDatabaseType(DatabaseType.HIVE);
        config.setHost("validation.invalid");config.setDatabase("business");config.setAuthMode("NOSASL");config.setSsl(true);
        config.setConnectorProperties(Map.of("hiveConnectionMode","open-source","transportMode","http","httpPath","cliservice"));
        assertEquals("open-source",config.getHiveConnectionMode());
        assertTrue(config.buildJdbcUrl().contains(";transportMode=http;httpPath=cliservice;ssl=true;auth=noSasl"));
        assertFalse(config.buildJdbcUrl().contains("user.keytab"));
    }
}

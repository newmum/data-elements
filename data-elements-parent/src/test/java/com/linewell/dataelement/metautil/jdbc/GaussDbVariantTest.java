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

package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.linewell.dataelement.mapping.core.MappingException;
import com.alibaba.druid.pool.DruidDataSource;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import java.sql.Driver;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MappingRegisteredConnectionTest {
    @Test void gaussProductsBindTheirSelectedVendorDriverAndNeverShareTheSamePrefixPool()throws Exception {
        var open=mock(Driver.class);var huawei=mock(Driver.class);
        var delegate=(Driver)Class.forName("org.h2.Driver").getDeclaredConstructor().newInstance();
        String url="jdbc:opengauss://validation.invalid:15432/validation";
        when(open.connect(eq(url),any(Properties.class))).thenAnswer(call->delegate.connect("jdbc:h2:mem:validation_mapping_gauss;DB_CLOSE_DELAY=-1",new Properties()));
        var service=new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),new FieldMappingService());
        var cfg=new LinkedHashMap<String,Object>(Map.of("dbType","GAUSSDB","gaussdbVariant","opengauss","jdbcURL",url));
        var before=new LinkedHashMap<>(cfg);
        try(var selected=mockStatic(AbstractMetadataExplorer.class)) {
            selected.when(()->AbstractMetadataExplorer.loadGaussDriver("opengauss")).thenReturn(open);
            selected.when(()->AbstractMetadataExplorer.loadGaussDriver("gaussdb")).thenReturn(huawei);
            try {
                var first=registeredPool(service,cfg);assertSame(open,first.getDriver());
                for(int index=0;index<2;index++)try(var connection=service.openRegistered("db",Map.of("db",cfg))){assertFalse(connection.isClosed());}
                assertSame(first,registeredPool(service,cfg));
                var nativeCfg=new LinkedHashMap<String,Object>(cfg);nativeCfg.put("gaussdbVariant","gaussdb");
                var nativePool=registeredPool(service,nativeCfg);assertNotSame(first,nativePool);assertSame(huawei,nativePool.getDriver());
                selected.verify(()->AbstractMetadataExplorer.loadGaussDriver("opengauss"),times(1));
                selected.verify(()->AbstractMetadataExplorer.loadGaussDriver("gaussdb"),times(1));
                assertEquals(before,cfg);
            }finally{service.close();}
        }
    }
    @Test void explicitOpenHiveBorrowsItsIsolatedDriverAndNeverFallsBackToTheBundledSameNameClass()throws Exception {
        var isolated=mock(Driver.class);String url="jdbc:hive2://validation.invalid:10000/validation";
        var delegate=(Driver)Class.forName("org.h2.Driver").getDeclaredConstructor().newInstance();
        when(isolated.connect(eq(url),any(java.util.Properties.class))).thenAnswer(call->
                delegate.connect("jdbc:h2:mem:validation_mapping_isolated_driver;DB_CLOSE_DELAY=-1",new java.util.Properties()));
        var service=new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),mock(FieldMappingService.class));
        var config=new LinkedHashMap<String,Object>(Map.of("jdbcURL",url,"driverClassName","org.apache.hive.jdbc.HiveDriver",
                "hiveConnectionMode","open-source","username","validation_reader","password","validation-only",
                "jdbcProperties",Map.of("hive.fetch.task.conversion","none")));
        var before=new LinkedHashMap<>(config);
        try(var selected=mockStatic(AbstractMetadataExplorer.class)){
            selected.when(AbstractMetadataExplorer::loadOpenSourceHiveDriver).thenReturn(isolated);
            try{
                var pool=registeredPool(service,config);assertSame(isolated,pool.getDriver());
                assertEquals("none",pool.getConnectProperties().getProperty("hive.fetch.task.conversion"));
                assertEquals(0,pool.getConnectionErrorRetryAttempts());assertTrue(pool.isBreakAfterAcquireFailure());assertTrue(pool.isFailFast());
                for(int iteration=0;iteration<2;iteration++)try(var connection=service.openRegistered("db",Map.of("db",config));
                        var statement=connection.createStatement();var rows=statement.executeQuery("SELECT 1")){
                    assertTrue(rows.next());assertEquals(1,rows.getInt(1));
                }
                selected.verify(AbstractMetadataExplorer::loadOpenSourceHiveDriver,times(1));
                verify(isolated,atLeastOnce()).connect(eq(url),argThat(properties->"validation_reader".equals(properties.getProperty("user"))
                        &&"validation-only".equals(properties.getProperty("password"))));
                assertEquals(before,config);
            }finally{service.close();}
        }
    }

    @Test void changingHiveClientOrAuthenticationProfileCannotReuseThePreviousPool()throws Exception {
        var isolated=mock(Driver.class);var service=new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),mock(FieldMappingService.class));
        var config=new LinkedHashMap<String,Object>(Map.of("jdbcURL","jdbc:hive2://validation.invalid:10000/validation",
                "driverClassName","org.apache.hive.jdbc.HiveDriver","hiveConnectionMode","open-source","authMode","NONE"));
        try(var selected=mockStatic(AbstractMetadataExplorer.class)){
            selected.when(AbstractMetadataExplorer::loadOpenSourceHiveDriver).thenReturn(isolated);
            try{
                var first=registeredPool(service,config);assertSame(first,registeredPool(service,new LinkedHashMap<>(config)));
                var mrs=new LinkedHashMap<String,Object>(config);mrs.put("hiveConnectionMode","huawei-mrs");mrs.put("authMode","KERBEROS");
                var secured=registeredPool(service,mrs);assertNotSame(first,secured);assertNotSame(isolated,secured.getDriver());
                assertEquals("org.apache.hive.jdbc.HiveDriver",secured.getDriverClassName());
                var changedAuth=new LinkedHashMap<String,Object>(config);changedAuth.put("authMode","KERBEROS");
                assertNotSame(first,registeredPool(service,changedAuth));
                var changedProperties=new LinkedHashMap<String,Object>(config);changedProperties.put("jdbcProperties",Map.of("hive.fetch.task.conversion","none"));
                assertNotSame(first,registeredPool(service,changedProperties));
            }finally{service.close();}
        }
    }

    @Test void missingIsolatedOpenHiveClientDoesNotFallBackToMrsAndUnauthorizedIdsDoNotLoadAnyDriver()throws Exception {
        var service=new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),mock(FieldMappingService.class));
        try(var selected=mockStatic(AbstractMetadataExplorer.class)){
            selected.when(AbstractMetadataExplorer::loadOpenSourceHiveDriver).thenThrow(new IllegalArgumentException("未安装开源 Hive JDBC 客户端"));
            try{
                var error=assertThrows(MappingException.class,()->service.openRegistered("foreign-db",Map.of()));
                assertEquals("RESOURCE_FORBIDDEN",error.code());selected.verifyNoInteractions();
                assertThrows(IllegalArgumentException.class,()->service.openRegistered("db",Map.of("db",Map.of(
                        "jdbcURL","jdbc:hive2://validation.invalid:10000/validation","hiveConnectionMode","open-source"))));
                selected.verify(AbstractMetadataExplorer::loadOpenSourceHiveDriver,times(1));
            }finally{service.close();}
        }
    }

    private static DruidDataSource registeredPool(UnifiedMappingService service,Map<String,Object> config)throws Exception {
        var method=UnifiedMappingService.class.getDeclaredMethod("pool",String.class,Map.class);method.setAccessible(true);
        return (DruidDataSource)method.invoke(service,"db",Map.of("db",config));
    }
    @Test void hiveColdConnectHasABoundedBudgetWithoutIncreasingRetriesForOtherProducts()throws Exception {
        var service=new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),new FieldMappingService());
        try{
            var hive=registeredPool(service,Map.of("jdbcURL","jdbc:hive2://validation.invalid:10000/validation","hiveConnectionMode","mrs"));
            var ordinary=registeredPool(service,Map.of("jdbcURL","jdbc:h2:mem:validation_pool_budget"));
            assertEquals(45000,hive.getMaxWait());assertEquals(10000,ordinary.getMaxWait());
            assertEquals(0,hive.getConnectionErrorRetryAttempts());assertTrue(hive.isBreakAfterAcquireFailure());assertTrue(hive.isFailFast());
        }finally{service.close();}
    }
    @Test void hostRegistrationUsesDeclaredEndpointAndTlsWithoutWritingConfiguration() {
        var source=new LinkedHashMap<String,Object>(Map.of("dbType","sqlserver","host","validation.invalid","port",1433,"database","validation_db",
                "encrypt",true,"trustServerCertificate",false,"username","validation_reader","password","  validation secret  "));
        var before=new LinkedHashMap<>(source);
        assertEquals("jdbc:sqlserver://validation.invalid:1433;databaseName=validation_db;encrypt=true;trustServerCertificate=false;loginTimeout=30;socketTimeout=60000",UnifiedMappingService.registeredJdbcUrl(source));
        assertEquals(before,source);
    }
    @Test void explicitJdbcSettingsRemainAuthoritative() {
        String url="jdbc:sqlserver://validation.invalid:1444;databaseName={validation;name};encrypt=strict";
        assertEquals(url,UnifiedMappingService.registeredJdbcUrl(Map.of("jdbcURL",url,"dbType","SQLSERVER","encrypt",false)));
        assertEquals("jdbc:h2:mem:validation",UnifiedMappingService.registeredJdbcUrl(Map.of("url","jdbc:h2:mem:validation")));
    }
    @Test void missingEndpointsAndOtherProductsAreNotGuessed() {
        assertEquals("",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","database","validation")));
        assertEquals("",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","ORACLE","host","validation.invalid","database","validation")));
        assertEquals("",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","unknown","host","validation.invalid","database","validation")));
    }
    @Test void mysqlHostRegistrationUsesMetadataUrlRulesAndPreservesExplicitSettings() {
        var config=new LinkedHashMap<String,Object>(Map.of("dbType","mysql","host","validation.invalid","database","validation","port",3307));
        var before=new LinkedHashMap<>(config);
        assertEquals("jdbc:mysql://validation.invalid:3307/validation?useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true",UnifiedMappingService.registeredJdbcUrl(config));
        assertEquals(before,config);
        config.put("extraParams","useSSL=true&serverTimezone=UTC");
        assertEquals("jdbc:mysql://validation.invalid:3307/validation?useSSL=true&serverTimezone=UTC",UnifiedMappingService.registeredJdbcUrl(config));
        config.put("jdbcURL","jdbc:mysql://explicit.invalid:3306/validation?useSSL=true");
        assertEquals(config.get("jdbcURL"),UnifiedMappingService.registeredJdbcUrl(config));
        for(String database:List.of("validation?user=other","validation/other","validation#other","validation;user=other"))
            assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","MYSQL","host","validation.invalid","database",database)));
        assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","MYSQL","host","validation.invalid","database","validation","port",0)));
    }
    @Test void verticaHostRegistrationUsesItsOwnDefaultPortAndRetainsDeclaredOptions() {
        var config=new LinkedHashMap<String,Object>(Map.of("dbType","vertica","host","validation.invalid","database","validation","currentSchema","public"));
        var before=new LinkedHashMap<>(config);
        assertEquals("jdbc:vertica://validation.invalid:5433/validation",UnifiedMappingService.registeredJdbcUrl(config));
        assertEquals(before,config);
        config.put("port",15433);config.put("extraParams","SSL=true&ConnectionLoadBalance=false");
        assertEquals("jdbc:vertica://validation.invalid:15433/validation?SSL=true&ConnectionLoadBalance=false",UnifiedMappingService.registeredJdbcUrl(config));
        config.put("jdbcURL","jdbc:vertica://explicit.invalid:5434/validation_explicit");
        assertEquals(config.get("jdbcURL"),UnifiedMappingService.registeredJdbcUrl(config));
        assertEquals("",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","VERTICA","database","validation")));
        assertEquals("",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","VERTICA","host","validation.invalid")));
    }
    @Test void verticaHostModeRejectsInvalidPortsAndUrlPathInjectionBeforeAnyConnection() {
        for(String database:List.of("validation?user=other","validation/other","validation#other","validation;user=other","validation\\other"))
            assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","VERTICA","host","validation.invalid","database",database)));
        for(String port:List.of("0","65536","invalid"))
            assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","VERTICA","host","validation.invalid","database","validation","port",port)));
        assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","VERTICA","host","validation.invalid?user=other","database","validation")));
    }
    @Test void verticaPoolInitializesItsRealDeclaredDriverWithoutUrlInferenceOrOpeningConnections()throws Exception {
        var service=new UnifiedMappingService(mock(RegisteredDictionaryCatalog.class),new FieldMappingService());
        try {
            var pool=registeredPool(service,Map.of("dbType","vertica","host","validation.invalid","database","validation"));
            assertDoesNotThrow(pool::init);
            assertEquals("com.vertica.jdbc.Driver",pool.getDriverClassName());
            assertEquals("com.vertica.jdbc.Driver",pool.getDriver().getClass().getName());
            assertEquals(0,pool.getPoolingCount());
            assertEquals(0,pool.getActiveCount());
        } finally {service.close();}
    }
    @ParameterizedTest @ValueSource(strings={"0","65536","invalid"})
    void invalidPortsFailBeforeConnecting(String port) {
        assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","host","validation.invalid","database","validation","port",port)));
    }
    @Test void hostModeDoesNotAcceptConnectionPropertyInjection() {
        assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","host","validation.invalid;user=other","database","validation")));
        assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","host","validation.invalid","database","validation;user=other")));
        assertThrows(MappingException.class,()->UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","host","validation.invalid","database","validation","encrypt","true;user=other")));
    }
    @Test void explicitExtraParametersAndLegacyDefaultsKeepTheirMeaning() {
        String prefix="jdbc:sqlserver://validation.invalid:1433;databaseName=validation";
        assertEquals(prefix+";encrypt=strict;applicationName=validation",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","host","validation.invalid","database","validation","extraParams","encrypt=strict;applicationName=validation","encrypt",false)));
        assertEquals(prefix+";encrypt=false;trustServerCertificate=true;loginTimeout=30;socketTimeout=60000",UnifiedMappingService.registeredJdbcUrl(Map.of("dbType","SQLSERVER","host","validation.invalid","database","validation")));
    }
}

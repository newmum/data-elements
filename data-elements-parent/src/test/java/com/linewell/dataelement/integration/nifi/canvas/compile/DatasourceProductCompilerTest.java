package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.*;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DatasourceProductCompilerTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final NifiClient nifi=mock(NifiClient.class);
    private final ManifestRegistry registry=mock(ManifestRegistry.class);
    private final HiveModule hive=mock(HiveModule.class);

    private void compile(String type,Map<String,Object> config) throws Exception {
        ComponentManifest manifest;try(var input=getClass().getResourceAsStream("/manifests/sources/"+type+".json")) {
            manifest=mapper.readValue(input,ComponentManifest.class);
        }
        when(registry.get("source."+type)).thenReturn(manifest);
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble())).thenReturn(new NifiEntity(null,Map.of("id","group"),null,null));
        when(nifi.createProcessor(anyString(),anyString(),anyString(),anyDouble(),anyDouble(),anyMap(),nullable(String.class),nullable(String.class)))
                .thenAnswer(call->new NifiEntity(null,Map.of("id",UUID.randomUUID().toString()),null,null));
        when(nifi.createControllerService(anyString(),anyString(),anyString(),anyMap()))
                .thenAnswer(call->new NifiEntity(null,Map.of("id",UUID.randomUUID().toString()),null,null));
        Map<String,Object> node=Map.of("id","source","manifestKey","source."+type,"category","source","label","validation","config",config,"x",0,"y",0);
        Pipeline pipeline=mapper.convertValue(Map.of("id","validation","name","validation","dsl",Map.of("nodes",List.of(node),"edges",List.of())),Pipeline.class);
        new DslCompiler(nifi,registry,new FieldMappingService(),hive).compile(pipeline);
    }

    @ParameterizedTest
    @CsvSource({"gaussdb,com.huawei.opengauss.jdbc.Driver,opengauss", "gaussdb-jdbc,com.huawei.gaussdb.jdbc.Driver,gaussdb", "opengauss,org.opengauss.Driver,opengauss",
            "dws,org.postgresql.Driver,postgresql", "mysql,com.mysql.cj.jdbc.Driver,mysql"})
    void gaussProductControlsRealCompiledPool(String variant,String driver,String protocol) throws Exception {
        var config=new LinkedHashMap<String,Object>(Map.of("gaussdbVariant",variant,"host","validation.invalid","database","business",
                "username","validation","table","orders","incrementalColumn","updated_at","driverLocations","/lib/validation-driver.jar"));
        compile("gaussdb",config);
        verify(nifi).createControllerService(anyString(),eq("org.apache.nifi.dbcp.DBCPConnectionPool"),anyString(),argThat(properties ->
                driver.equals(properties.get("Database Driver Class Name"))
                && properties.get("Database Connection URL").startsWith("jdbc:"+protocol+"://")
                && "/lib/validation-driver.jar".equals(properties.get("Database Driver Locations"))));
    }

    @Test void explicitOpenHiveDoesNotLoadStaleMrsProfile() throws Exception {
        compile("hive",new LinkedHashMap<>(Map.of("hiveConnectionMode","open-source","hiveProfile","default","host","validation.invalid",
                "database","business","table","orders","incrementalColumn","updated_at","driverLocations","/lib/hive")));
        verify(hive,never()).resolveRuntimeProfile(anyString());
        verify(nifi).createControllerService(anyString(),eq("org.apache.nifi.dbcp.DBCPConnectionPool"),anyString(),argThat(properties ->
                properties.get("Database Connection URL").startsWith("jdbc:hive2://validation.invalid:10000/business")
                        && !properties.containsKey("Kerberos User Service")));
        verify(nifi,never()).createControllerService(anyString(),eq("org.apache.nifi.kerberos.KerberosKeytabUserService"),anyString(),anyMap());
    }

    @Test void verticaHasARealSourceAndVendorDriver() throws Exception {
        compile("vertica",new LinkedHashMap<>(Map.of("host","validation.invalid","database","business","table","orders",
                "incrementalColumn","updated_at","driverLocations","/lib/vertica.jar")));
        verify(nifi).createControllerService(anyString(),eq("org.apache.nifi.dbcp.DBCPConnectionPool"),anyString(),argThat(properties ->
                "com.vertica.jdbc.Driver".equals(properties.get("Database Driver Class Name"))
                && properties.get("Database Connection URL").startsWith("jdbc:vertica://validation.invalid:5433/business")));
    }

    @Test void ftpsSourceNeverCompilesToPlainFtp() throws Exception {
        compile("ftp",new LinkedHashMap<>(Map.of("protocol","ftps","hostname","validation.invalid","port",990,"remotePath","/validation")));
        verify(nifi).createProcessor(anyString(),eq(FtpsSourceCompiler.TYPE),anyString(),anyDouble(),anyDouble(),argThat(properties ->
                "fetch".equals(properties.get("ftps.operation"))&&properties.get("Script Body").contains("execPROT('P')")),anyString(),eq("TIMER_DRIVEN"));
        verify(nifi,never()).createProcessor(anyString(),eq("org.apache.nifi.processors.standard.GetFTP"),anyString(),anyDouble(),anyDouble(),anyMap(),any(),any());
    }
}

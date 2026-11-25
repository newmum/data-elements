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
                properties.get("Database Connection URL").startsWith("jdbc:haitong:hive2://validation.invalid:10000/business")
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

    @Test void mongoBatchFetchAndTotalLimitAreIndependentAndKeepJsonContract() throws Exception {
        compile("mongodb",new LinkedHashMap<>(Map.of("host","validation.invalid","database","validation","table","records",
                "batchSize",7,"fetchSize",13,"queryLimit",19,"queryJson","{\"active\":true}","projectionJson","{\"id\":1}","sortJson","{\"id\":1}")));
        verify(nifi).createProcessor(anyString(),eq("org.apache.nifi.processors.mongodb.GetMongo"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                "7".equals(p.get("Results Per FlowFile")) && "13".equals(p.get("Batch Size")) && "19".equals(p.get("Limit"))
                && "{\"active\":true}".equals(p.get("Query")) && "{\"id\":1}".equals(p.get("Sort")) && "{\"id\":1}".equals(p.get("Projection"))
                && "Standard".equals(p.get("JSON Type")) && "yyyy-MM-dd'T'HH:mm:ss.SSSXXX".equals(p.get("Date Format"))),anyString(),anyString());
    }

    @Test void legacyMongoDefaultsPreserve1000WithoutAddingAQueryLimit() throws Exception {
        compile("mongodb",new LinkedHashMap<>(Map.of("host","validation.invalid","database","validation","table","records")));
        verify(nifi).createProcessor(anyString(),endsWith(".GetMongo"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                "1000".equals(p.get("Results Per FlowFile")) && "1000".equals(p.get("Batch Size")) && "{}".equals(p.get("Query")) && !p.containsKey("Limit")),anyString(),anyString());
    }

    @Test void iotdbHasRealQueryLimitAndIndependentRecordAndFetchWindows() throws Exception {
        compile("iotdb",new LinkedHashMap<>(Map.of("host","validation.invalid","table","root.validation.device","username","validation",
                "batchSize",17,"maxRowsPerRun",0,"queryLimit",29)));
        verify(nifi).createProcessor(anyString(),endsWith(".ExecuteSQLRecord"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                "SELECT * FROM root.validation.device LIMIT 29".equals(p.get("SQL select query"))
                && "17".equals(p.get("Max Rows Per FlowFile")) && "0".equals(p.get("Fetch Size"))),anyString(),anyString());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"ftp","sftp"})
    void nativeFilePollingUsesConfiguredDirectoryBatch(String type) throws Exception {
        compile(type,new LinkedHashMap<>(Map.of("hostname","validation.invalid","remotePath","/validation","remotePollBatchSize",37)));
        verify(nifi).createProcessor(anyString(),endsWith(type.equals("ftp") ? ".GetFTP" : ".GetSFTP"),anyString(),anyDouble(),anyDouble(),
                argThat(p -> "37".equals(p.get("Remote Poll Batch Size")) && "false".equals(p.get("Delete Original"))),anyString(),anyString());
    }

    @Test void ftpsFileLimitIsForwardedToExistingBoundedStatefulScript() {
        org.assertj.core.api.Assertions.assertThat(FtpsSourceCompiler.properties(Map.of("maxFilesPerRun",3),"",false)).containsEntry("ftps.maxFiles","3");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"-1","0","1.5","2147483648","1;drop table x"})
    void invalidMongoControlsFailBeforeAnyNativeMutation(String value) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> compile("mongodb",Map.of("batchSize",value)))
                .isInstanceOf(DslCompiler.NodeConfigException.class).hasMessageContaining("单批最大条数");
        verifyNoInteractions(nifi);
    }

    @Test void invalidQueryDocumentsAndInjectedIotdbLimitsFailBeforeNativeCalls() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> compile("mongodb",Map.of("queryJson","[]"))).isInstanceOf(DslCompiler.NodeConfigException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> compile("iotdb",Map.of("queryLimit","1;drop x"))).isInstanceOf(DslCompiler.NodeConfigException.class);
        verifyNoInteractions(nifi);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={20,100})
    void allSourceReadControlsValidateInMemoryAtBatchScale(int count) throws Exception {
        var manifest=mapper.readValue(getClass().getResourceAsStream("/manifests/sources/mongodb.json"),ComponentManifest.class);
        when(registry.get("source.mongodb")).thenReturn(manifest);
        var nodes=new ArrayList<Pipeline.Node>();
        for(int i=0;i<count;i++)nodes.add(new Pipeline.Node("source-"+i,"source.mongodb","validation","source",0,0,Map.of("batchSize",7,"fetchSize",13,"queryLimit",19)));
        Pipeline pipeline=mapper.convertValue(Map.of("id","validation","dsl",new Pipeline.Dsl(1,nodes,List.of())),Pipeline.class);
        new DslCompiler(nifi,registry,new FieldMappingService(),hive).preflightSources(pipeline);
        verifyNoInteractions(nifi);
    }

    @Test void kafkaPollControlReachesBothNativeAndLegacyConnectionServices() throws Exception {
        for(String service:List.of("org.apache.nifi.kafka.service.Kafka3ConnectionService","com.linewell.dataelement.nifi.HaitongLegacyKafkaConnectionService")) {
            clearInvocations(nifi);
            compile("kafka",new LinkedHashMap<>(Map.of("bootstrapServers","validation.invalid:9092","topic","validation","kafkaServiceType",service,"maxPollRecords",37,"maxUncommittedSize","10 MB","maxUncommittedTime","200 millis")));
            verify(nifi).createControllerService(anyString(),eq(service),anyString(),argThat(p -> "37".equals(p.get("max.poll.records"))));
            verify(nifi).createProcessor(anyString(),endsWith(".ConsumeKafka"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                    "10 MB".equals(p.get("Max Uncommitted Size")) && "200 millis".equals(p.get("Max Uncommitted Time"))),anyString(),anyString());
        }
    }

    @Test void objectListingBatchAndHdfsRecursionAreActuallyCompiled() throws Exception {
        compile("minio",new LinkedHashMap<>(Map.of("minioEndpoint","http://validation.invalid:9000","minioBucket","validation","minioAccessKey","validation","minioSecretKey","validation","listingBatchSize",23,"minioFilePattern",".*selected.*")));
        verify(nifi).createProcessor(anyString(),endsWith(".ListS3"),anyString(),anyDouble(),anyDouble(),argThat(p -> "23".equals(p.get("Listing Batch Size"))),anyString(),anyString());
        verify(nifi).createProcessor(anyString(),endsWith(".RouteOnAttribute"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                p.containsKey("csv") && p.get("csv").contains(":and(${filename:matches(${literal('") && p.get("csv").contains("'):base64Decode()})}")),nullable(String.class),nullable(String.class));
        compile("hdfs",new LinkedHashMap<>(Map.of("clientConfigDir","/validation","path","/validation","recurseSubdirectories",false)));
        verify(nifi).createProcessor(anyString(),endsWith(".ListHDFS"),anyString(),anyDouble(),anyDouble(),argThat(p -> "false".equals(p.get("Recurse Subdirectories"))),anyString(),anyString());
    }

    @ParameterizedTest @CsvSource({"OBJECT_KEY,false", "FILE_NAME,true"})
    void minioFilenamePatternScopeDoesNotChangeTheExactObjectKeyOrFetchAddress(String scope,boolean basename) throws Exception {
        String object="registered/nested/orders_1.xlsx";
        compile("minio",new LinkedHashMap<>(Map.of("minioEndpoint","http://validation.invalid:9000","minioBucket","validation",
                "minioAccessKey","validation","minioSecretKey","validation","minioObjectKey",object,
                "minioFilePattern","(?i)^orders_.*\\.xlsx$","minioFilePatternScope",scope)));
        verify(nifi).createProcessor(anyString(),endsWith(".RouteOnAttribute"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                p.containsKey("xlsx") && p.get("xlsx").contains("filename:substringAfterLast('/')")==basename),nullable(String.class),nullable(String.class));
        verify(nifi).createProcessor(anyString(),endsWith(".RouteOnAttribute"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                ("${filename:base64Encode():equals('"+Base64.getEncoder().encodeToString(object.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"')}").equals(p.get("selected"))),nullable(String.class),nullable(String.class));
        verify(nifi).createProcessor(anyString(),endsWith(".FetchS3Object"),anyString(),anyDouble(),anyDouble(),argThat(p ->
                "${filename}".equals(p.get("Object Key"))),nullable(String.class),nullable(String.class));
    }

    @Test void invalidMinioFilenameScopeFailsBeforeAnyNativeMutation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> compile("minio",Map.of("minioFilePatternScope","UNSUPPORTED")))
                .isInstanceOf(DslCompiler.NodeConfigException.class).hasMessageContaining("文件匹配范围");
        verifyNoInteractions(nifi);
    }

    @Test void mysqlConnectionDefaultFetchIsNotLostWhenThePoolIsRebuilt() throws Exception {
        for(String url:List.of("jdbc:mysql://validation.invalid/business?useSSL=true", "jdbc:mysql://validation.invalid/business?defaultFetchSize=999&useCursorFetch=false&useSSL=true")) {
            clearInvocations(nifi);
            compile("mysql",new LinkedHashMap<>(Map.of("host","validation.invalid","database","business","table","records","jdbcUrl",url,"defaultFetchSize",37,"maxRowsPerRun",0)));
            verify(nifi).createControllerService(anyString(),eq("org.apache.nifi.dbcp.DBCPConnectionPool"),anyString(),argThat(p -> {
                String actual=p.get("Database Connection URL");return actual.contains("defaultFetchSize=37") && !actual.contains("defaultFetchSize=999") && actual.contains("useSSL=true") && actual.contains("tinyInt1isBit=false") && actual.contains(url.contains("useCursorFetch=false") ? "useCursorFetch=false" : "useCursorFetch=true");
            }));
            verify(nifi).createProcessor(anyString(),endsWith(".ExecuteSQLRecord"),anyString(),anyDouble(),anyDouble(),argThat(p -> "0".equals(p.get("Fetch Size"))),nullable(String.class),nullable(String.class));
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"1&useSSL=false","2147483648","-1","1.5"})
    void malformedDriverFetchCountCannotReachPoolUrl(String value) throws Exception {
        var method=DslCompiler.class.getDeclaredMethod("buildSourceDbcpProperties",Map.class);method.setAccessible(true);
        var compiler=new DslCompiler(nifi,registry,new FieldMappingService(),hive);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> method.invoke(compiler,Map.of("dbType","MYSQL","host","validation.invalid","database","validation","jdbcUrl","jdbc:mysql://validation.invalid/validation","defaultFetchSize",value)))
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(nifi,never()).createControllerService(anyString(),anyString(),anyString(),anyMap());
    }
}

package com.haitong;

import com.haitong.core.Json;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.location=classpath:/application.yml","db.type=h2","db.init-schema=true","db.username=sa","db.password=",
    "db.url=jdbc:h2:mem:magic-business;MODE=MySQL;DB_CLOSE_DELAY=-1","relay.worker.enabled=false","relay.limits.min-free-disk-bytes=0",
    "relay.security.api-key=magic-producer-0123456789abcdef0123456789","relay.security.admin-key=magic-admin-0123456789abcdef0123456789",
    "relay.standalone-registry.sync-enabled=true","relay.standalone-registry.sync-key=magic-sync-0123456789abcdef0123456789",
    "relay.routes.table-to-ftp.targets[0]=local","relay.registration-registry.base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class MagicBusinessHttpTest {
    static final String ADMIN="magic-admin-0123456789abcdef0123456789",CLIENT="magic-producer-0123456789abcdef0123456789";
    static final Path WORK=Path.of("../logs/haitong-magic-first/test-"+UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("relay.work-dir",WORK::toString);r.add("logging.file.name",()->WORK.resolve("service.log").toString());r.add("magic-api.resource.location",()->WORK.resolve("magic-api").toString());r.add("magic-api.show-url",()->false);}
    @Autowired MockMvc mvc;@Autowired Json json;
    @Autowired JdbcTemplate jdbc;
    final java.util.concurrent.atomic.AtomicInteger sqlReads=new java.util.concurrent.atomic.AtomicInteger();
    long reads(){return sqlReads.get();}
    void installSqlCounter(){
        var original=jdbc.getDataSource();
        var counted=new org.springframework.jdbc.datasource.DelegatingDataSource(original){
            @Override public java.sql.Connection getConnection()throws java.sql.SQLException{
                var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(original);
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("close")){org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,original);return null;}
                        if(method.getName().equals("prepareStatement")&&args[0].toString().stripLeading().toUpperCase().startsWith("SELECT"))sqlReads.incrementAndGet();
                        var result=method.invoke(connection,args);
                        if(method.getName().equals("createStatement"))return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Statement.class},(statement,m,a)->{
                            try{if(m.getName().equals("executeQuery")&&a[0].toString().stripLeading().toUpperCase().startsWith("SELECT"))sqlReads.incrementAndGet();return m.invoke(result,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                        });
                        return result;
                    }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            }
        };
        jdbc.setDataSource(counted);
    }
    @Test void registryAndTableValidationUseBoundedSqlAndSourceGrants()throws Exception{
        installSqlCounter();
        for(int size:List.of(20,100)){
            var sources=new ArrayList<Map<String,Object>>();
            for(int i=0;i<size;i++)sources.add(Map.of("datasourceId","magic-batch-"+size+"-"+i,"datasourceName","synthetic","clientIds",List.of("default"),"tables",List.of(Map.of("tableName","demo","columns",List.of(Map.of("columnName","record_id","required",true))))));
            sqlReads.set(0);
            mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","magic-registry-"+size).contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",sources)))).andExpect(status().isCreated()).andExpect(jsonPath("$.datasourceCount").value(size));
            assertEquals(4,reads(),"Two HTTP audit clock reads + business clock + idempotency read, independent of source count");
            var records=new ArrayList<Map<String,Object>>();for(int i=0;i<size;i++)records.add(Map.of("record_id",i));
            mvc.perform(post("/api/v1/data-push").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-records-"+size).contentType("application/json").content(json.bytes(Map.of("deliveryType","FTP","datasourceId","magic-batch-"+size+"-0","tableName","demo","records",records)))).andExpect(status().isAccepted());
        }
        jdbc.update("UPDATE ht_standalone_datasource_client SET client_id='another-source-client' WHERE datasource_id='magic-batch-100-0'");
        mvc.perform(post("/api/v1/data-push").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-denied").contentType("application/json").content(json.bytes(Map.of("deliveryType","FTP","datasourceId","magic-batch-100-0","tableName","demo","records",List.of(Map.of("record_id",1)))))).andExpect(status().isForbidden());
    }
    @Test void registryStatusIsReadOnlyBoundedAndRestrictedToServiceIdentity()throws Exception{
        installSqlCounter();
        for(int size:List.of(20,100)){
            var sources=new ArrayList<Map<String,Object>>();var refs=new ArrayList<Map<String,Object>>();
            for(int i=0;i<size;i++){
                String id="validation-status-"+size+"-"+i;
                sources.add(Map.of("datasourceId",id,"datasourceName","synthetic","clientIds",List.of("default"),"tables",List.of(Map.of("tableId","table-"+i,"tableName","demo","schemaVersion","1","columns",List.of(Map.of("columnName","record_id","required",true))))));
                refs.add(Map.of("datasourceId",id,"tableName","demo"));
            }
            mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","validation-status-seed-"+size).contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",sources)))).andExpect(status().isCreated());
            int ledger=jdbc.queryForObject("SELECT COUNT(*) FROM ht_standalone_registry_sync",Integer.class);
            sqlReads.set(0);
            mvc.perform(post("/api/v1/data-push").header("X-Standalone-Registry-Key","magic-sync-0123456789abcdef0123456789").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","registryAction","STATUS","tableRefs",refs))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readOnly").value(true)).andExpect(jsonPath("$.tables.length()").value(size)).andExpect(jsonPath("$.tables[0].definitionHash").isString());
            assertEquals(4,reads(),"Two audit reads and two bounded mirror reads for either 20 or 100 references");
            assertEquals(ledger,jdbc.queryForObject("SELECT COUNT(*) FROM ht_standalone_registry_sync",Integer.class));
            System.out.println("registry status "+size+": mirror reads=2; no idempotency or mirror writes");
        }
        var query=Map.of("deliveryType","REGISTRY_SYNC","registryAction","STATUS","tableRefs",List.of(Map.of("datasourceId","validation-status-20-0","tableName","demo")));
        mvc.perform(post("/api/v1/data-push").header("X-Api-Key",CLIENT).contentType("application/json").content(json.bytes(query))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/data-push").header("X-Standalone-Registry-Key","magic-sync-0123456789abcdef0123456789").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","registryAction","STATUS","tableRefs",Collections.nCopies(1001,Map.of("datasourceId","validation-status-20-0","tableName","demo")))))).andExpect(status().isPayloadTooLarge());
    }
    @Test void magicOwnsNativeContractsAndAuthentication()throws Exception{
        mvc.perform(get("/internal/v1/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/overview").header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.deliveryCounts").exists());
        mvc.perform(get("/internal/v1/deliveries").header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/internal/v1/deliveries?limit=201").header("X-Admin-Key",ADMIN)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/messages/missing").header("X-Api-Key",CLIENT)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/data-push").header("X-Api-Key",CLIENT).header("Idempotency-Key","missing-route").contentType("application/json").content("{\"deliveryType\":\"other\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/internal/push-credentials/issue").header("X-Admin-Key",ADMIN).contentType("application/json").content("{\"clientId\":\"push-magic-test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.revealed").value(true));
        mvc.perform(post("/internal/push-credentials/issue").header("X-Admin-Key",ADMIN).contentType("application/json").content("{\"clientId\":\"push-magic-test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.revealed").value(false));
    }
    @Test void invalidSnapshotAndDatabaseFailureAreAtomic()throws Exception{
        var source=Map.of("datasourceId","magic-rollback","datasourceName","synthetic","clientIds",List.of("default"),"tables",List.of());
        jdbc.execute("ALTER TABLE ht_standalone_datasource ADD CONSTRAINT magic_fail CHECK(datasource_id <> 'magic-rollback')");
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","magic-rollback").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",List.of(source))))).andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ht_standalone_registry_sync WHERE idempotency_key='magic-rollback'",Integer.class));
        jdbc.execute("ALTER TABLE ht_standalone_datasource DROP CONSTRAINT magic_fail");
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","magic-duplicate-source").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",List.of(source,source))))).andExpect(status().isUnprocessableEntity());
    }
    @Test void legacyFilePagesUseThreeBusinessReadsForTwentyAndOneHundredRows()throws Exception{
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO ht_file_checkpoint(id,owner,source_type,source_scope,source_key,route_code,original_name,payload_ref,content_sha256,file_size,content_format,config_snapshot,status,retry_audit,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,'JSONL','{}','READY','[]',?,0)","magic-page-"+i,"magic-owner","HTTP_FILE","magic-page","magic-page-"+i,"demo","demo.jsonl","spool/demo","hash",10,100L-i);
        installSqlCounter();
        for(int limit:List.of(20,100)){
            sqlReads.set(0);
            mvc.perform(get("/internal/v1/files?limit="+limit).header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(limit));
            assertEquals(5,reads(),"One page and two aggregates plus two HTTP audit clock reads");
        }
    }
    @Test void administrationQueriesAndPayloadRemainNativeResponses()throws Exception{
        for(String path:List.of("config-summary","files?limit=100","quarantined","connectors/database/health","exchange-logs?from=0&to=1","reconciliation/summary?from=0&to=1"))mvc.perform(get("/internal/v1/"+path).header("X-Admin-Key",ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/internal/v1/openapi").header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/yaml"));
        mvc.perform(post("/api/v1/ingest/demo").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-payload").contentType("application/json").content("{\"v\":1}")).andExpect(status().isAccepted());
        String id=jdbc.queryForObject("SELECT id FROM ht_message WHERE source_key='magic-payload'",String.class);
        mvc.perform(get("/internal/v1/messages/"+id+"/payload").header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(content().bytes("{\"v\":1}".getBytes()));
        mvc.perform(post("/api/v1/ingest/demo").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-payload").contentType("application/json").content("{ \"v\":1}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/ingest/demo").header("X-Api-Key",CLIENT).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
}

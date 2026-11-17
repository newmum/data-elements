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
    @Test void platformDigestsAreBatchProvisionedAndStaleRotationsCannotOverwriteNewKeys()throws Exception{
        installSqlCounter();
        for(int size:List.of(20,100)){
            var sources=new ArrayList<Map<String,Object>>();
            for(int i=0;i<size;i++){
                String id="validation-credential-"+size+"-"+i,client="push-"+id;
                String hash=com.haitong.core.Rules.sha256(("validation-secret-for-"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                sources.add(Map.of("datasourceId",id,"datasourceName","validation","clientIds",List.of(client),
                    "credential",Map.of("clientId",client,"apiKeySha256",hash,"previousSha256",""),
                    "tables",List.of(Map.of("tableName","demo","columns",List.of(Map.of("columnName","record_id","required",true))))));
            }
            sqlReads.set(0);
            mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","validation-credential-sync-"+size).contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",sources))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.credentialCount").value(size));
            assertEquals(5,reads(),"One extra batch credential read for either 20 or 100 sources");
            mvc.perform(post("/api/v1/data-push").header("X-Api-Key","validation-secret-for-validation-credential-"+size+"-0").header("Idempotency-Key","validation-credential-push-"+size).contentType("application/json")
                .content(json.bytes(Map.of("deliveryType","FTP","datasourceId","validation-credential-"+size+"-0","tableName","demo","records",List.of(Map.of("record_id",1)))))).andExpect(status().isAccepted());
        }
        String id="validation-credential-20-0",client="push-"+id;
        String oldHash=com.haitong.core.Rules.sha256(("validation-secret-for-"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String newHash=com.haitong.core.Rules.sha256("validation-replacement-key-0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var source=new LinkedHashMap<String,Object>();source.put("datasourceId",id);source.put("datasourceName","validation");source.put("clientIds",List.of(client));source.put("tables",List.of());
        source.put("credential",Map.of("clientId",client,"apiKeySha256",newHash,"previousSha256",oldHash));
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","validation-credential-rotate").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasource",source)))).andExpect(status().isCreated());
        source.put("credential",Map.of("clientId",client,"apiKeySha256",oldHash,"previousSha256",""));
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","validation-credential-stale").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasource",source)))).andExpect(status().isConflict());
        assertEquals(newHash,jdbc.queryForObject("select api_key_sha256 from ht_push_api_client where client_id=?",String.class,client));
        mvc.perform(post("/api/v1/data-push").header("X-Api-Key","validation-secret-for-validation-credential-100-0").header("Idempotency-Key","validation-credential-denied").contentType("application/json")
            .content(json.bytes(Map.of("deliveryType","FTP","datasourceId",id,"tableName","demo","records",List.of(Map.of("record_id",1)))))).andExpect(status().isForbidden());
        source.put("credential",Map.of("clientId","push-foreign-source","apiKeySha256",oldHash));
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","validation-credential-wrong-binding").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasource",source)))).andExpect(status().isUnprocessableEntity());
    }
    void installSqlCounter(){
        var original=jdbc.getDataSource();
        var requestThread = Thread.currentThread();
        var counted=new org.springframework.jdbc.datasource.DelegatingDataSource(original){
            @Override public java.sql.Connection getConnection()throws java.sql.SQLException{
                var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(original);
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("close")){org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,original);return null;}
                        if(Thread.currentThread()==requestThread&&method.getName().equals("prepareStatement")&&args[0].toString().stripLeading().toUpperCase().startsWith("SELECT"))sqlReads.incrementAndGet();
                        var result=method.invoke(connection,args);
                        if(method.getName().equals("createStatement"))return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Statement.class},(statement,m,a)->{
                            try{if(Thread.currentThread()==requestThread&&m.getName().equals("executeQuery")&&a[0].toString().stripLeading().toUpperCase().startsWith("SELECT"))sqlReads.incrementAndGet();return m.invoke(result,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
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
        var tableReads = new LinkedHashMap<Integer, Long>();
        for(int size:List.of(20,100)){
            var sources=new ArrayList<Map<String,Object>>();
            for(int i=0;i<size;i++)sources.add(Map.of("datasourceId","magic-batch-"+size+"-"+i,"datasourceName","synthetic","clientIds",List.of("default"),"tables",List.of(Map.of("tableName","demo","columns",List.of(Map.of("columnName","record_id","required",true))))));
            sqlReads.set(0);
            mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","magic-registry-"+size).contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",sources)))).andExpect(status().isCreated()).andExpect(jsonPath("$.datasourceCount").value(size));
            assertEquals(4,reads(),"Two HTTP audit clock reads + business clock + idempotency read, independent of source count");
            var records=new ArrayList<Map<String,Object>>();for(int i=0;i<size;i++)records.add(Map.of("record_id",i));
            sqlReads.set(0);
            mvc.perform(post("/api/v1/push").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-records-"+size).contentType("application/json").content(json.bytes(Map.of("deliveryType","FTP","datasourceId","magic-batch-"+size+"-0","tableName","demo","records",records)))).andExpect(status().isAccepted());
            tableReads.put(size, reads());
        }
        assertEquals(tableReads.get(20), tableReads.get(100), "Table pushes use fixed reads for either batch size");
        System.out.println("canonical table push: one HTTP request per batch, SQL reads=" + tableReads);
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
        mvc.perform(get("/gateway/config/check")).andExpect(status().isUnauthorized());
        mvc.perform(get("/gateway/config/check").header("X-Admin-Key",ADMIN)).andExpect(status().isOk());
        mvc.perform(post("/push/message/status").header("X-Admin-Key",ADMIN).contentType("application/json").content("{}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_BODY"));
        mvc.perform(get("/internal/v1/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/overview").header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.deliveryCounts").exists());
        mvc.perform(get("/internal/v1/deliveries").header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/internal/v1/deliveries?limit=201").header("X-Admin-Key",ADMIN)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/messages/missing").header("X-Api-Key",CLIENT)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/data-push").header("X-Api-Key",CLIENT).header("Idempotency-Key","missing-route").contentType("application/json").content("{\"deliveryType\":\"other\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/internal/push-credentials/issue").header("X-Admin-Key",ADMIN).contentType("application/json").content("{\"clientId\":\"push-magic-test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.revealed").value(true));
        mvc.perform(post("/internal/push-credentials/issue").header("X-Admin-Key",ADMIN).contentType("application/json").content("{\"clientId\":\"push-magic-test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.revealed").value(false));
        mvc.perform(post("/internal/push-credentials/issue").header("X-Admin-Key", ADMIN)
                .contentType("application/json").content("{\"clientId\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("clientId必须为数据源推送调用方编号"));
    }

    @Test void diagnosticAliasesKeepTheSameAuthenticationAndResponses() throws Exception {
        var gateway = mvc.perform(get("/gateway/config/check").header("X-Admin-Key", ADMIN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(get("/gateway/config-check")).andExpect(status().isUnauthorized());
        var legacyGateway = mvc.perform(get("/gateway/config-check").header("X-Admin-Key", ADMIN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(diagnosticBody(gateway), diagnosticBody(legacyGateway));

        assertDiagnosticResponse("/gateway/token-status", "/gateway/token/status", "{\"dryRun\":true}", 200);
        assertDiagnosticResponse("/gateway/data-push", "/gateway/data/push",
                "{\"dryRun\":true,\"deliveryType\":\"FTP\",\"datasourceId\":\"validation\",\"tableName\":\"demo\",\"records\":[{}]}", 200);
        assertDiagnosticResponse("/ftp-diagnostics/probe", "/ftp/connect/probe", "{}", 403);
        assertDiagnosticResponse("/push-operations/data-push", "/push/debug/send", "{\"deliveryType\":\"invalid\"}", 422);
        assertDiagnosticResponse("/push-operations/message-status", "/push/message/status", "{\"messageId\":\"validation-missing\"}", 404);
        assertDiagnosticResponse("/push-operations/template-test", "/push/template/test", "{\"deliveryType\":\"invalid\"}", 422);
        mvc.perform(post("/gateway/config-check").header("X-Admin-Key", ADMIN)).andExpect(status().isNotFound());

        for (String path : List.of("/push/debug/send", "/push-operations/data-push")) {
            mvc.perform(post(path).header("X-Standalone-Registry-Key", "magic-sync-0123456789abcdef0123456789")
                            .contentType("application/json").content("{\"deliveryType\":\"REGISTRY_SYNC\",\"registryAction\":\"STATUS\",\"tableRefs\":[{\"datasourceId\":\"validation-missing\",\"tableName\":\"demo\"}]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.readOnly").value(true));
        }
    }

    @Test void canonicalPushAndCredentialRoutesShareTheLegacyWriteContracts() throws Exception {
        var table = Map.of("tableName", "demo", "columns", List.of(Map.of("columnName", "record_id", "required", true)));
        var source = Map.of("datasourceId", "validation-short-routes", "datasourceName", "Validation",
                "clientIds", List.of("default"), "tables", List.of(table));
        mvc.perform(post("/api/v1/push").header("X-Standalone-Registry-Key","magic-sync-0123456789abcdef0123456789")
                        .header("Idempotency-Key", "validation-short-registry").contentType("application/json")
                        .content(json.bytes(Map.of("deliveryType", "REGISTRY_SYNC", "datasources", List.of(source)))))
                .andExpect(status().isCreated());
        var input = Map.of("deliveryType", "FTP", "datasourceId", "validation-short-routes", "tableName", "demo",
                "records", List.of(Map.of("record_id", 1)));
        var encoded = json.bytes(input);
        var accepted = mvc.perform(post("/api/v1/push").header("X-Api-Key", CLIENT)
                        .header("Idempotency-Key", "validation-short-push").contentType("application/json").content(encoded))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        String messageId = json.object(accepted.getContentAsByteArray()).get("messageId").toString();
        try {
            // 投递还在排队时，相同正文仍返回 202，并重用原消息和 Location。
            mvc.perform(post("/api/v1/data-push").header("X-Api-Key", CLIENT)
                            .header("Idempotency-Key", "validation-short-push").contentType("application/json").content(encoded))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.messageId").value(messageId))
                    .andExpect(jsonPath("$.duplicate").value(true))
                    .andExpect(header().string("Location", accepted.getHeader("Location")))
                    .andExpect(header().string("Cache-Control", "no-store"));
            var different = new LinkedHashMap<String, Object>(input);
            different.put("records", List.of(Map.of("record_id", 2)));
            mvc.perform(post("/api/v1/data-push").header("X-Api-Key", CLIENT)
                            .header("Idempotency-Key", "validation-short-push").contentType("application/json").content(json.bytes(different)))
                    .andExpect(status().isConflict());
        } finally {
            jdbc.update("DELETE FROM ht_delivery WHERE message_id = ?", messageId);
            jdbc.update("DELETE FROM ht_message WHERE id = ?", messageId);
        }

        var issue = "{\"clientId\":\"push-validation-short-routes\"}";
        mvc.perform(post("/internal/credentials/issue").header("X-Admin-Key", ADMIN)
                        .contentType("application/json").content(issue))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revealed").value(true))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/internal/push-credentials/issue").header("X-Admin-Key", ADMIN)
                        .contentType("application/json").content(issue))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revealed").value(false))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        mvc.perform(post("/internal/credentials/issue").header("X-Api-Key", CLIENT)
                        .contentType("application/json").content(issue)).andExpect(status().isUnauthorized());
    }

    private void assertDiagnosticResponse(String oldPath, String path, String body, int expectedStatus) throws Exception {
        var current = mvc.perform(post(path).header("X-Api-Key", CLIENT).header("Idempotency-Key", "validation-diagnostic")
                        .contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray();
        var previous = mvc.perform(post(oldPath).header("X-Api-Key", CLIENT).header("Idempotency-Key", "validation-diagnostic")
                        .contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray();
        assertEquals(diagnosticBody(current), diagnosticBody(previous));
    }

    private Map<String, Object> diagnosticBody(byte[] response) {
        var body = new HashMap<>(json.object(response));
        body.remove("timestamp");
        return body;
    }
    @Test void invalidSnapshotAndDatabaseFailureAreAtomic()throws Exception{
        var source=Map.of("datasourceId","magic-rollback","datasourceName","synthetic","clientIds",List.of("default"),"tables",List.of());
        jdbc.execute("ALTER TABLE ht_standalone_datasource ADD CONSTRAINT magic_fail CHECK(datasource_id <> 'magic-rollback')");
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","magic-rollback").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",List.of(source))))).andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ht_standalone_registry_sync WHERE idempotency_key='magic-rollback'",Integer.class));
        jdbc.execute("ALTER TABLE ht_standalone_datasource DROP CONSTRAINT magic_fail");
        mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","magic-duplicate-source").contentType("application/json").content(json.bytes(Map.of("deliveryType","REGISTRY_SYNC","datasources",List.of(source,source))))).andExpect(status().isUnprocessableEntity());
    }
    @Test void adminFileAndLogPagesUseBoundedReadsForTwentyAndOneHundredRows()throws Exception{
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO ht_file_checkpoint(id,owner,source_type,source_scope,source_key,route_code,original_name,payload_ref,content_sha256,file_size,content_format,config_snapshot,status,retry_audit,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,'JSONL','{}','READY','[]',?,0)","magic-page-"+i,"magic-owner","HTTP_FILE","magic-page","magic-page-"+i,"demo","demo.jsonl","spool/demo","hash",10,100L-i);
        var entries = new ArrayList<Object[]>();
        for (int i = 0; i < 100; i++) entries.add(new Object[]{"validation-log-" + i, 1000L + i});
        jdbc.batchUpdate("""
                INSERT INTO ht_exchange_log (id, protocol, direction, event_type, phase, outcome, metadata, created_at)
                VALUES (?, 'HTTP', 'IN', 'HTTP_ACCESS', 'FINISH', 'RESPONDED', '{}', ?)
                """, entries);
        installSqlCounter();
        for(int limit:List.of(20,100)){
            sqlReads.set(0);
            mvc.perform(get("/internal/v1/files?limit="+limit).header("X-Admin-Key",ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(limit));
            assertEquals(5,reads(),"One page and two aggregates plus two HTTP audit clock reads");
            sqlReads.set(0);
            mvc.perform(get("/internal/v1/logs?from=1000&to=1100&limit="+limit).header("X-Admin-Key",ADMIN))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(limit));
            assertEquals(3, reads(), "One log page plus two HTTP audit clock reads");
            System.out.println("admin exchange logs " + limit + ": one request, one page read, two audit reads");
        }
    }
    @Test void administrationQueriesAndPayloadRemainNativeResponses()throws Exception{
        for(String path:List.of("config","files?limit=100","quarantined","health?name=database","logs?from=0&to=1","reconcile?from=0&to=1"))mvc.perform(get("/internal/v1/"+path).header("X-Admin-Key",ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/internal/v1/openapi").header("X-Admin-Key",ADMIN)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/yaml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/internal/v1/payload:")));
        mvc.perform(post("/api/v1/ingest/demo").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-payload").contentType("application/json").content("{\"v\":1}")).andExpect(status().isAccepted());
        String id=jdbc.queryForObject("SELECT id FROM ht_message WHERE source_key='magic-payload'",String.class);
        for (String path : List.of("/internal/v1/payload?id="+id,
                "/internal/v1/messages/"+id+"/payload?id=validation-other")) {
            mvc.perform(get(path).header("X-Admin-Key",ADMIN)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "attachment; filename=message-"+id+".bin"))
                    .andExpect(content().bytes("{\"v\":1}".getBytes()));
        }
        mvc.perform(post("/api/v1/ingest/demo").header("X-Api-Key",CLIENT).header("Idempotency-Key","magic-payload").contentType("application/json").content("{ \"v\":1}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/ingest/demo").header("X-Api-Key",CLIENT).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }

    @Test void adminAliasesKeepTheirPermissionsAndPathParameterPriority() throws Exception {
        for (String path : List.of("config", "config-summary", "health?name=database",
                "connectors/database/health?name=validation-other", "logs?from=0&to=1", "exchange-logs?from=0&to=1",
                "reconcile?from=0&to=1", "reconciliation/summary?from=0&to=1")) {
            mvc.perform(get("/internal/v1/"+path).header("X-Api-Key", CLIENT)).andExpect(status().isUnauthorized());
            mvc.perform(get("/internal/v1/"+path).header("X-Admin-Key", ADMIN)).andExpect(status().isOk());
        }
        String id = "00000000-0000-0000-0000-000000000106";
        var writePaths = Map.of("retry?id="+id, "{\"reason\":\"validation retry\"}",
                "deliveries/"+id+"/retry", "{\"reason\":\"validation retry\"}",
                "resolve?id="+id, "{\"reason\":\"validation resolution\",\"decision\":\"MARK_SUCCEEDED\",\"evidence\":\"validation evidence\"}",
                "deliveries/"+id+"/resolve", "{\"reason\":\"validation resolution\",\"decision\":\"MARK_SUCCEEDED\",\"evidence\":\"validation evidence\"}",
                "fileretry?id="+id, "{\"reason\":\"validation retry\"}",
                "files/"+id+"/retry", "{\"reason\":\"validation retry\"}",
                "scan?route=validation-missing", "{}", "ftp-scans/validation-missing", "{}");
        for (var entry : writePaths.entrySet()) {
            String path = "/internal/v1/"+entry.getKey();
            mvc.perform(post(path).header("X-Api-Key", CLIENT)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("X-Admin-Key", ADMIN).contentType("application/json").content(entry.getValue()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(get("/internal/v1/health").header("X-Admin-Key", ADMIN)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/internal/v1/ftp-scans/validation-missing").header("X-Admin-Key", ADMIN)).andExpect(status().isNotFound());
    }
}

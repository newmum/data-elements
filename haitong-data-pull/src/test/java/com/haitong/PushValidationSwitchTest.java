package com.haitong;

import com.haitong.api.PushAccess;
import com.haitong.config.RelayProperties;
import com.haitong.core.*;
import com.haitong.core.Models.Kind;
import com.haitong.files.FileUploadService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.location=classpath:/application.yml","db.type=h2","db.init-schema=true","db.username=sa","db.password=",
    "db.url=jdbc:h2:mem:push-validation;MODE=MySQL;DB_CLOSE_DELAY=-1","relay.worker.enabled=false","relay.limits.min-free-disk-bytes=0",
    "relay.security.push-validation-enabled=false","relay.security.api-key=123","relay.security.clients.unused.api-key=env:UNSET_UNUSED_PUSH_KEY",
    "relay.security.admin-key=validation-admin-0123456789abcdef0123456789","relay.standalone-registry.sync-enabled=true",
    "relay.standalone-registry.sync-key=validation-sync-0123456789abcdef0123456789",
    "relay.routes.table-to-ftp.targets[0]=local","relay.files.enabled=true","relay.files.unregistered-targets[0]=local"})
@AutoConfigureMockMvc
class PushValidationSwitchTest {
    static final Path WORK=Path.of("../logs/haitong-data-pull/validation-switch-"+UUID.randomUUID()).toAbsolutePath();
    static final String ADMIN="validation-admin-0123456789abcdef0123456789";
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){
        r.add("relay.work-dir",WORK::toString);r.add("logging.file.name",()->WORK.resolve("service.log").toString());
        r.add("magic-api.resource.location",()->WORK.resolve("magic-api").toString());r.add("magic-api.show-url",()->false);
    }
    @Autowired MockMvc mvc;@Autowired Json json;@Autowired JdbcTemplate jdbc;
    @Autowired RelayProperties properties;@Autowired FileUploadService files;@Autowired DeliveryWorker worker;
    Map<String,Object> batch(int size){
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<size;i++)rows.add(Map.of("unregistered_field",i));
        return Map.of("deliveryType","FTP","datasourceId","validation_unregistered_source","tableName","validation_unregistered_table","records",rows);
    }
    Map<String,Object> response(org.springframework.test.web.servlet.MvcResult value)throws Exception{
        return json.object(value.getResponse().getContentAsByteArray());
    }
    @Test void acceptsWithoutKeyAndRegistrationAndPreservesIdempotency()throws Exception{
        for(int count:List.of(20,100)){
            String key="validation-open-"+count;
            var accepted=response(mvc.perform(post("/api/v1/data-push").header("Idempotency-Key",key)
                .contentType("application/json").content(json.bytes(batch(count)))).andExpect(status().isAccepted()).andReturn());
            String id=accepted.get("messageId").toString();
            assertEquals(PushAccess.OWNER,jdbc.queryForObject("SELECT owner FROM ht_message WHERE id=?",String.class,id));
            mvc.perform(post("/api/v1/data-push").header("X-Api-Key","invalid-is-ignored").header("Idempotency-Key",key)
                .contentType("application/json").content(json.bytes(batch(count)))).andExpect(status().isAccepted()).andExpect(jsonPath("$.messageId").value(id)).andExpect(jsonPath("$.duplicate").value(true));
            mvc.perform(get("/api/v1/messages/"+id)).andExpect(status().isOk());
            mvc.perform(post("/api/v1/data-push").header("Idempotency-Key",key).contentType("application/json").content(json.bytes(batch(count+1)))).andExpect(status().isConflict());
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ht_standalone_datasource",Integer.class));
    }
    @Test void twentyAndOneHundredRowsUseTheSameReadsAndZeroRegistrationReads()throws Exception{
        var original=jdbc.getDataSource();var reads=new java.util.concurrent.atomic.AtomicInteger();
        var registrationReads=new java.util.concurrent.atomic.AtomicInteger();
        jdbc.setDataSource(new org.springframework.jdbc.datasource.DelegatingDataSource(original){
            @Override public java.sql.Connection getConnection()throws java.sql.SQLException{
                var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(original);
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("close")){org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,original);return null;}
                        if(method.getName().equals("prepareStatement")){
                            String sql=args[0].toString().stripLeading().toUpperCase(Locale.ROOT);
                            if(sql.startsWith("SELECT")){reads.incrementAndGet();if(sql.contains("HT_STANDALONE_"))registrationReads.incrementAndGet();}
                        }
                        return method.invoke(connection,args);
                    }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            }
        });
        try{
            List<Integer> counts=new ArrayList<>();
            for(int size:List.of(20,100)){
                reads.set(0);registrationReads.set(0);
                mvc.perform(post("/api/v1/data-push").header("Idempotency-Key","counted-open-"+size).contentType("application/json").content(json.bytes(batch(size)))).andExpect(status().isAccepted());
                counts.add(reads.get());assertEquals(0,registrationReads.get());
            }
            assertTrue(counts.getFirst()>0);assertEquals(counts.getFirst(),counts.getLast(),counts.toString());
            Files.writeString(WORK.resolve("read-counts.json"),json.write(Map.of("records",List.of(20,100),"httpRequests",List.of(1,1),"selects",counts,"registrationSelects",List.of(0,0))));
        }finally{jdbc.setDataSource(original);}
    }
    @Test void switchingBackRequiresKeyAndRegisteredSourceEvenForAdmin()throws Exception{
        properties.getSecurity().setPushValidationEnabled(true);
        try{
            mvc.perform(post("/api/v1/data-push").header("Idempotency-Key","protected-missing-key").contentType("application/json").content(json.bytes(batch(1)))).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","protected-source").contentType("application/json").content(json.bytes(batch(1))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("STANDALONE_DATASOURCE_NOT_CONFIGURED"));
            mvc.perform(post("/api/v1/file-uploads").header("X-Admin-Key",ADMIN).header("Idempotency-Key","protected-files").contentType("application/json")
                .content(json.bytes(Map.of("datasourceId","unregistered","collectionId","files","fileName","test.pdf","size",10)))).andExpect(status().isNotFound());
        }finally{properties.getSecurity().setPushValidationEnabled(false);}
    }
    @Test void configuredServletContextSupportsTheSamePushAndAdminBoundaries()throws Exception{
        mvc.perform(post("/data/api/v1/data-push").contextPath("/data").header("Idempotency-Key","context-open-batch").contentType("application/json").content(json.bytes(batch(1)))).andExpect(status().isAccepted());
        mvc.perform(get("/data/actuator/health").contextPath("/data")).andExpect(status().isOk());
        mvc.perform(get("/data/internal/v1/overview").contextPath("/data")).andExpect(status().isUnauthorized());
        mvc.perform(post("/data/api/v1/data-push%20").contextPath("/data")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PATH"));
    }
    @Test void managementSyncExamplesAndActionsAreNotOpened()throws Exception{
        for(String path:List.of("/internal/v1/overview","/internal/v1/config-summary","/actuator/metrics","/gateway/config"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/push-credentials/issue").contentType("application/json").content("{\"clientId\":\"push-validation\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/data-push").header("Idempotency-Key","forbidden-sync").contentType("application/json").content("{\"deliveryType\":\"REGISTRY_SYNC\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_REGISTRY_SYNC"));
        mvc.perform(post("/validation/status-dict")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/actions/inspect")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/overview").header("X-Admin-Key",ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
    @Test void anonymousAccessCannotReadPreviouslyAuthenticatedMessages()throws Exception{
        var result=response(mvc.perform(post("/api/v1/data-push").header("X-Admin-Key",ADMIN).header("Idempotency-Key","admin-owned-batch")
            .contentType("application/json").content(json.bytes(batch(1)))).andExpect(status().isAccepted()).andReturn());
        mvc.perform(get("/api/v1/messages/"+result.get("messageId"))).andExpect(status().isNotFound());
    }
    @Test void basicValidationAndSizeStillApply()throws Exception{
        for(var invalid:List.of(Map.of("deliveryType","FTP","datasourceId","../x","tableName","ok","records",List.of(Map.of())),
            Map.of("deliveryType","FTP","datasourceId","ok","tableName","../outside","records",List.of(Map.of())),
            Map.of("deliveryType","FTP","datasourceId","ok","tableName","ok","records",List.of())))
            mvc.perform(post("/api/v1/data-push").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(json.bytes(invalid))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/data-push").contentType("application/json").content(json.bytes(batch(1)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/data-push").header("Idempotency-Key","oversized").contentType("application/json").content(" ".repeat(1048577))).andExpect(status().isPayloadTooLarge());
    }
    @Test void unregisteredMultipartFileCompletesAndDeliversWithoutAKey()throws Exception{
        byte[] data="%PDF-1.7\nUnregistered validation file\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var accepted=response(mvc.perform(post("/api/v1/file-uploads").header("Idempotency-Key","open-file-init").contentType("application/json")
            .content(json.bytes(Map.of("datasourceId","validation_files","collectionId","validation_documents","fileName","测试文件.pdf","size",data.length,"sha256",Rules.sha256(data)))))
            .andExpect(status().isAccepted()).andReturn());
        String id=accepted.get("uploadId").toString(),base="/api/v1/file-uploads/"+id;
        mvc.perform(put(base+"/parts/1").header("X-Part-SHA256",Rules.sha256(data)).contentType("application/octet-stream").content(data)).andExpect(status().isOk());
        mvc.perform(post(base+"/complete")).andExpect(status().isAccepted());
        mvc.perform(post(base+"/submit").header("Idempotency-Key","open-file-submit").contentType("application/json")
            .content(json.bytes(Map.of("businessKey","validation-document-001","metadata",Map.of("unregisteredDescription","无需登记"))))).andExpect(status().isAccepted());
        for(int i=0;i<10 && !files.status(PushAccess.identity(),id).get("state").equals("READY");i++)files.runOne();
        mvc.perform(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));
        assertTrue(worker.runOne(Kind.FILE_LOCAL));
        mvc.perform(get(base+"/content")).andExpect(status().isOk()).andExpect(content().bytes(data));
        mvc.perform(post("/api/v1/file-uploads/status-batch").contentType("application/json").content(json.bytes(Map.of("uploadIds",List.of(id,UUID.randomUUID().toString()))))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/file-uploads?limit=20")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post(base+"/receipts")).andExpect(status().isUnauthorized());
        assertEquals(0,jdbc.queryForObject("SELECT reserved_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));
        assertEquals((long)data.length,jdbc.queryForObject("SELECT committed_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));
    }
}

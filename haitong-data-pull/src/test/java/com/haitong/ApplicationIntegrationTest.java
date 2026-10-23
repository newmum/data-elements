package com.haitong;

import com.haitong.config.*;
import com.haitong.connector.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import com.haitong.security.PushCredentialStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Full Spring/H2/HTTP integration tests. Run with `-Dhaitong.loopback.tests=true` on a host with a usable loopback interface. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.config.location=classpath:/application.yml",
    "db.type=h2", "db.init-schema=true", "db.username=sa", "db.password=",
    "db.url=jdbc:h2:mem:relaytests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "relay.work-dir=./target/test-data",
    "relay.worker.enabled=false",
    "relay.limits.min-free-disk-bytes=0",
    "relay.security.api-key=test-client-0123456789abcdef0123456789",
    "relay.security.admin-key=test-admin-0123456789abcdef0123456789",
    "relay.security.clients.other.api-key=test-other-0123456789abcdef0123456789",
    "relay.security.clients.other.channels[0]=demo",
    "relay.security.clients.other.actions[0]=inspect",
    "relay.targets.table-log.type=LOG",
    "relay.routes.table-to-log.targets[0]=table-log",
    "relay.routes.table-to-log.required-fields[0]=tableName",
    "relay.routes.table-to-log.required-fields[1]=records",
    "relay.routes.table-to-ftp.enabled=true",
    "relay.routes.table-to-ftp.registration-managed=false",
    "relay.routes.table-to-ftp.targets[0]=table-log",
    "relay.routes.table-to-ftp.required-fields[0]=tableName",
    "relay.routes.table-to-ftp.required-fields[1]=records",
    "relay.routes.table-to-kafka.enabled=true",
    "relay.routes.table-to-kafka.registration-managed=false",
    "relay.routes.table-to-kafka.targets[0]=table-log",
    "relay.routes.table-to-kafka.required-fields[0]=tableName",
    "relay.routes.table-to-kafka.required-fields[1]=records",
    "relay.standalone.datasources.source-001.clients[0]=default",
    "relay.standalone.datasources.source-001.tables.customer.schema-version=v1",
    "relay.standalone.datasources.source-001.tables.customer.required-fields[0]=id",
    "relay.standalone.datasources.source-001.tables.customer.allowed-fields[0]=id",
    "relay.standalone.datasources.source-001.tables.customer.allowed-fields[1]=name",
    "relay.routes.csv-import.targets[0]=local",
    "relay.routes.csv-import.file-format=CSV",
    "relay.routes.raw-file.targets[0]=local",
    "relay.routes.raw-file.file-format=BINARY"
})
@EnabledIfSystemProperty(named="haitong.loopback.tests",matches="true")
@TestMethodOrder(MethodOrderer.MethodName.class)
class ApplicationIntegrationTest {
    private static final String KEY="test-client-0123456789abcdef0123456789";
    private static final String OTHER="test-other-0123456789abcdef0123456789";
    private final HttpClient http=HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired Json json;
    @Autowired RelayRepository repo;
    @Autowired IngressService ingress;
    @Autowired DeliveryWorker worker;
    @Autowired FileService files;
    @Autowired RelayProperties config;
    @Autowired KafkaSource source;
    @Autowired PushCredentialStore pushCredentials;

    @BeforeEach void clean() {
        JdbcTemplate db=repo.jdbc();
        db.update("DELETE FROM ht_exchange_log");db.update("DELETE FROM ht_delivery");db.update("DELETE FROM ht_message");db.update("DELETE FROM ht_file_checkpoint");db.update("DELETE FROM ht_push_api_client");
    }
    private byte[] bytes(String s) {return s.getBytes(StandardCharsets.UTF_8);}
    private Map<String,Object> object(String s) {return json.object(bytes(s));}
    private HttpResponse<String> request(String method,String path,String key,String id,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(key!=null)b.header("X-Api-Key",key);if(id!=null)b.header("Idempotency-Key",id);
        if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private Accepted accept(String id) {return ingress.http("default","demo",id,bytes("{\"value\":1}"));}

    @Test void authenticationAndManagementIsolation() throws Exception {
        assertEquals(401,request("POST","/api/v1/ingest/demo",null,"test","{}" ).statusCode());
        assertEquals(401,request("GET","/internal/v1/overview",KEY,null,null).statusCode());
        assertEquals(200,request("GET","/actuator/health",null,null,null).statusCode());
    }
    @Test void datasourcePushCredentialIsOneTimeAndRestrictedToPushChannels() {
        var first=pushCredentials.issue("push-source-001",false);
        assertTrue(first.revealed());assertFalse(first.apiKey().isBlank());
        var identity=pushCredentials.authenticate(first.apiKey());
        assertNotNull(identity);assertEquals("push-source-001",identity.id());
        assertTrue(identity.channel("table-to-ftp"));assertTrue(identity.channel("table-to-kafka"));assertFalse(identity.channel("demo"));
        assertFalse(pushCredentials.issue("push-source-001",false).revealed());
        var rotated=pushCredentials.issue("push-source-001",true);
        assertTrue(rotated.revealed());assertNull(pushCredentials.authenticate(first.apiKey()));assertNotNull(pushCredentials.authenticate(rotated.apiKey()));
    }
    @Test void receiptDeduplicationAndBodyConflict() throws Exception {
        var first=request("POST","/api/v1/ingest/demo",KEY,"idem","{\"v\":1}");
        assertEquals(202,first.statusCode(),first.body());String id=(String)object(first.body()).get("messageId");
        var second=request("POST","/api/v1/ingest/demo",KEY,"idem","{\"v\":1}");
        assertEquals(id,object(second.body()).get("messageId"));assertEquals(true,object(second.body()).get("duplicate"));
        assertEquals(1,repo.deliveries(id).size());
        assertEquals(409,request("POST","/api/v1/ingest/demo",KEY,"idem","{ \"v\":1}").statusCode());
        assertEquals(404,request("GET","/api/v1/messages/"+id,OTHER,null,null).statusCode());
    }
    @Test void simpleLogSceneAcceptsOneTableBatchAndPrintsItsSafeSummary() throws Exception {
        String body="{\"tableName\":\"customer\",\"batchId\":\"customer-001\",\"records\":[{\"id\":\"C001\"},{\"id\":\"C002\"}]}";
        var response=request("POST","/api/v1/simple/log",KEY,"customer-001",body);
        assertEquals(202,response.statusCode(),response.body());String id=(String)object(response.body()).get("messageId");
        assertTrue(worker.runOne(Kind.LOG));assertEquals("SUCCEEDED",repo.deliveries(id).getFirst().status());
        assertEquals(422,request("POST","/api/v1/simple/log",KEY,"invalid-table","{\"tableName\":\"customer\",\"records\":[]}").statusCode());
    }
    @Test void unifiedDataPushRoutesByDeliveryTypeAndKeepsThatChoiceIdempotent() throws Exception {
        String body="{\"deliveryType\":\"log\",\"tableName\":\"customer\",\"batchId\":\"customer-002\",\"records\":[{\"id\":\"C003\"}]}";
        var accepted=request("POST","/api/v1/data-push",KEY,"customer-002",body);
        assertEquals(202,accepted.statusCode(),accepted.body());String id=(String)object(accepted.body()).get("messageId");
        assertEquals("table-to-log",repo.message(id).orElseThrow().routeCode());
        assertEquals(id,object(request("POST","/api/v1/data-push",KEY,"customer-002",body).body()).get("messageId"));
        assertEquals(422,request("POST","/api/v1/data-push",KEY,"unsupported-target","{\"deliveryType\":\"HTTP\",\"tableName\":\"customer\",\"records\":[{\"id\":\"C004\"}]}").statusCode());
        assertEquals(422,request("POST","/api/v1/data-push",KEY,"missing-target","{\"tableName\":\"customer\",\"records\":[{\"id\":\"C005\"}]}").statusCode());
    }
    @Test void standaloneTablePushUsesLocalContractWithoutThePlatformRegistry() throws Exception {
        String body="{\"deliveryType\":\"FTP\",\"datasourceId\":\"source-001\",\"tableName\":\"customer\",\"records\":[{\"id\":\"C006\",\"name\":\"Alice\"}]}";
        var accepted=request("POST","/api/v1/data-push",KEY,"standalone-customer-001",body);
        assertEquals(202,accepted.statusCode(),accepted.body());String id=(String)object(accepted.body()).get("messageId");
        assertEquals("table-to-ftp",repo.message(id).orElseThrow().routeCode());
        assertEquals("standalone",json.object(repo.message(id).orElseThrow().sourceMetadata().getBytes(StandardCharsets.UTF_8)).get("mode"));
        assertTrue(worker.runOne(Kind.LOG));assertEquals("SUCCEEDED",repo.deliveries(id).getFirst().status());
        assertEquals(422,request("POST","/api/v1/data-push",KEY,"standalone-source-missing","{\"deliveryType\":\"FTP\",\"datasourceId\":\"unknown\",\"tableName\":\"customer\",\"records\":[{\"id\":\"C007\"}]}" ).statusCode());
        assertEquals(422,request("POST","/api/v1/data-push",KEY,"standalone-field-missing","{\"deliveryType\":\"FTP\",\"datasourceId\":\"source-001\",\"tableName\":\"customer\",\"records\":[{\"name\":\"Alice\"}]}" ).statusCode());
        for(String removed:List.of("schemaVersion","occurredAt")) {
            var batch=new LinkedHashMap<String,Object>(Map.of("deliveryType","FTP","datasourceId","source-001","tableName","customer","records",List.of(Map.of("id","C007","name","Demo"))));
            batch.put(removed,"obsolete");
            assertEquals(422,request("POST","/api/v1/data-push",KEY,"removed-"+removed,new Json().write(batch)).statusCode());
        }
    }
    @Test void invalidJsonAndUnsupportedEnvelopeAreRejected() throws Exception {
        assertEquals(422,request("POST","/api/v1/ingest/demo",KEY,"duplicate-fields","{\"x\":1,\"x\":2}").statusCode());
        assertEquals(422,request("POST","/api/v1/ingest/demo",KEY,"array","[1,2]").statusCode());
        assertEquals(400,request("POST","/api/v1/ingest/demo",KEY,null,"{}").statusCode());
    }
    @Test void localDeliveryAndSafeRepeatDoNotCreateNewTask() throws Exception {
        Accepted a=accept("local");assertTrue(worker.runOne(Kind.LOCAL));
        assertEquals("SUCCEEDED",repo.deliveries(a.id()).getFirst().status());
        var duplicate=request("POST","/api/v1/ingest/demo",KEY,"local","{\"value\":1}");
        assertEquals(200,duplicate.statusCode());assertFalse(worker.runOne(Kind.LOCAL));
        try(var paths=Files.walk(Path.of(config.getWorkDir(),"out"))) {
            assertTrue(paths.anyMatch(p->p.getFileName().toString().startsWith(a.id()) && p.toString().endsWith(".done")));
        }
    }
    @Test void concurrentIdenticalRequestsHaveOneIdentity() throws Exception {
        try(ExecutorService pool=Executors.newFixedThreadPool(8)) {
            List<Callable<Accepted>> tasks=new ArrayList<>();for(int i=0;i<16;i++)tasks.add(()->accept("concurrent"));
            Set<String> ids=new HashSet<>();for(Future<Accepted> f:pool.invokeAll(tasks))ids.add(f.get().id());
            assertEquals(1,ids.size());assertEquals(1,repo.deliveries(ids.iterator().next()).size());
        }
    }
    @Test void claimHasOneWinnerAndExpiredLeaseCannotCommit() throws Exception {
        accept("claim");
        try(ExecutorService pool=Executors.newFixedThreadPool(8)) {
            List<Callable<Optional<Delivery>>> tasks=new ArrayList<>();for(int i=0;i<8;i++)tasks.add(()->repo.claim(Kind.LOCAL,300));
            List<Delivery> winners=new ArrayList<>();for(var f:pool.invokeAll(tasks))f.get().ifPresent(winners::add);
            assertEquals(1,winners.size());Delivery old=winners.getFirst();
            repo.jdbc().update("UPDATE ht_delivery SET lease_until=0 WHERE id=?",old.id());repo.recoverExpired();
            assertFalse(repo.complete(old,"{}"));assertEquals("RETRY_WAIT",repo.delivery(old.id()).orElseThrow().status());
        }
    }
    @Test void unsafeLostActionBecomesUnknown() {
        ActionPlan plan=new ActionPlan("backend-job","http","http://127.0.0.1:9000/start","POST",false,1,List.of());
        var a=ingress.action("default","unsafe",bytes("{}"),plan);
        Delivery d=repo.claim(Kind.ACTION,300).orElseThrow();
        repo.jdbc().update("UPDATE ht_delivery SET lease_until=0 WHERE id=?",d.id());repo.recoverExpired();
        assertEquals("UNKNOWN",repo.deliveries(a.id()).getFirst().status());assertFalse(repo.retry(d.id(),"[]"));
    }
    @Test void kafkaAcknowledgesAfterPersistentHandoffAndDeduplicatesCoordinates() {
        AtomicBoolean ack=new AtomicBoolean();ConsumerRecord<byte[],byte[]> record=new ConsumerRecord<>("input",0,123,null,bytes("{\"value\":1}"));
        source.receive(record,()->{
            assertTrue(repo.findMessage("kafka:main:v1","from-kafka","input:0:123").isPresent());ack.set(true);
        });
        assertTrue(ack.get());source.receive(record,()->{});
        assertEquals(1,repo.jdbc().queryForObject("SELECT COUNT(*) FROM ht_message",Integer.class));
    }
    @Test void invalidKafkaValueIsQuarantinedBeforeAcknowledgment() {
        AtomicBoolean ack=new AtomicBoolean();
        source.receive(new ConsumerRecord<>("input",0,124,null,bytes("not-json")),()->ack.set(true));
        assertTrue(ack.get());assertEquals(1,repo.quarantined(10,0).size());assertEquals(0,repo.pendingDeliveries());
    }
    @Test void csvQuotedNewlineUsesLogicalRecordsAndCompletionWaitsForDelivery() throws Exception {
        String csv="id,note\r\n1,\"hello\nworld\"\r\n2,\"a,b\"\r\n";
        Accepted a=files.upload("default","csv-import","csv-key","events.csv",new ByteArrayInputStream(bytes(csv)));
        assertTrue(files.runOne());assertEquals(2L,repo.file(a.id()).orElseThrow().lastRecordNo());
        assertEquals("INGESTED",files.status(a.id(),"default",false).get("status"));
        while(worker.runOne(Kind.LOCAL)) { /* deterministic drain */ }
        assertEquals("COMPLETED",files.status(a.id(),"default",false).get("status"));
        assertEquals(a.id(),files.upload("default","csv-import","csv-key","events.csv",new ByteArrayInputStream(bytes(csv))).id());
        assertFalse(files.runOne());
    }
    @Test void multipartUploadCreatesFileCheckpoint() throws Exception {
        String boundary="relay-test-boundary",body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"events.jsonl\"\r\nContent-Type: application/octet-stream\r\n\r\n{\"x\":1}\n\r\n--"+boundary+"--\r\n";
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/files/demo"))
            .header("X-Api-Key",KEY).header("Idempotency-Key","multipart")
            .header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(202,response.statusCode(),response.body());
        assertNotNull(object(response.body()).get("fileId"));
    }
    @Test void partialBadFileDoesNotBecomeCompleted() throws Exception {
        Accepted a=files.upload("default","demo","bad-file","events.jsonl",new ByteArrayInputStream(bytes("{\"x\":1}\nnot-json\n")));
        assertTrue(files.runOne());while(worker.runOne(Kind.LOCAL)) { }
        assertNotEquals("COMPLETED",files.status(a.id(),"default",false).get("status"));assertEquals(1,repo.quarantined(10,0).size());
    }
    @Test void snapshotFreezesTargetAndSuccessfulPayloadPurgeKeepsDeduplication() {
        Accepted a=accept("snapshot");String plan=repo.deliveries(a.id()).getFirst().plan();
        config.getTargets().get("local").setDirectory("changed-after-receipt");
        try {assertEquals(plan,repo.deliveries(a.id()).getFirst().plan());assertTrue(worker.runOne(Kind.LOCAL));}
        finally {config.getTargets().get("local").setDirectory("out");}
        assertEquals(1,repo.purgeSuccessfulPayloads(repo.now()+1000));assertTrue(accept("snapshot").duplicate());
        assertNull(repo.message(a.id()).orElseThrow().payload());
    }
    @Test void builtInInspectIsRealValidation() throws Exception {
        var response=request("POST","/api/v1/actions/inspect",KEY,null,"{\"deviceCode\":\"D001\"}");
        assertEquals(200,response.statusCode(),response.body());assertTrue(response.body().contains("sha256"));
    }

    @Test void ledgerSeparatesUniqueInputAndDuplicates() {
        byte[] raw=bytes("{\"data\":{\"deviceCode\":\"A1\"}}");
        Accepted a=ingress.http("default","demo","ledger-input",raw);Accepted b=ingress.http("default","demo","ledger-input",raw);
        assertEquals(a.id(),b.id());
        assertEquals(1L,repo.jdbc().queryForObject("SELECT COUNT(*) FROM ht_exchange_log WHERE message_id=? AND event_type='MESSAGE_RECEIVE' AND outcome='ACCEPTED'",Long.class,a.id()));
        assertEquals(1L,repo.jdbc().queryForObject("SELECT COUNT(*) FROM ht_exchange_log WHERE message_id=? AND outcome='DUPLICATE'",Long.class,a.id()));
    }
    @Test void ledgerDeliveryRetainsStartFinishAndDigest() {
        Accepted a=ingress.http("default","demo","ledger-send",bytes("{\"x\":1}"));assertTrue(worker.runOne(Kind.LOCAL));
        assertEquals(2L,repo.jdbc().queryForObject("SELECT COUNT(*) FROM ht_exchange_log WHERE message_id=? AND event_type='DELIVERY'",Long.class,a.id()));
        assertEquals(1L,repo.jdbc().queryForObject("SELECT COUNT(DISTINCT attempt_id) FROM ht_exchange_log WHERE message_id=? AND event_type='DELIVERY'",Long.class,a.id()));
        assertEquals(1L,repo.jdbc().queryForObject("SELECT COUNT(*) FROM ht_exchange_log WHERE message_id=? AND phase='FINISH' AND outcome='SUCCEEDED'",Long.class,a.id()));
    }
    @Test void fileRecordCountMismatchIsNotCompleted() throws Exception {
        Accepted f=files.upload("default","demo","count-mismatch","count.jsonl",new ByteArrayInputStream(bytes("{\"a\":1}\n")));
        repo.jdbc().update("UPDATE ht_file_checkpoint SET expected_records=2 WHERE id=?",f.id());assertTrue(files.runOne());
        assertEquals("FAILED",repo.file(f.id()).orElseThrow().status());
        assertEquals("FILE_RECORD_COUNT_MISMATCH",repo.file(f.id()).orElseThrow().errorCode());
    }
}

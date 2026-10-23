package com.haitong;

import com.haitong.config.*;
import com.haitong.connector.LocalConnector;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.files.*;
import com.haitong.store.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class UnstructuredFileTest {
    static final Secrets.Identity ALICE=new Secrets.Identity("alice",false,Set.of("*"),Set.of());
    static final Secrets.Identity BOB=new Secrets.Identity("bob",false,Set.of("*"),Set.of());
    static final Secrets.Identity RECEIVER=new Secrets.Identity("receiver",false,Set.of(),Set.of());
    static class CountingJdbc extends JdbcTemplate {
        int selects;CountingJdbc(DriverManagerDataSource ds){super(ds);}
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args){if(sql.startsWith("SELECT "))selects++;return super.queryForList(sql,args);}
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args){if(sql.startsWith("SELECT "))selects++;return super.queryForObject(sql,type,args);}
    }
    static class Fixture implements AutoCloseable {
        final Json json=new Json();final RelayProperties relay=new RelayProperties();final FileTransferProperties properties=new FileTransferProperties();
        final CountingJdbc jdbc;final RelayRepository ledger;final FileContractCatalog catalog;final FileUploadRepository repository;final FileStorage storage;final FileUploadService service;final TransactionTemplate tx;
        Fixture() throws Exception {
            relay.setWorkDir(Path.of("../logs/haitong-files/test-data",UUID.randomUUID().toString()).toAbsolutePath().normalize().toString());
            relay.getLimits().setMinFreeDiskBytes(0);relay.getSecurity().setApiKey("unit-client-0123456789abcdef0123456789");relay.getSecurity().setAdminKey("unit-admin-0123456789abcdef0123456789");
            var local=new RelayProperties.Target();local.setType("LOCAL");local.setDirectory("files-out");relay.setTargets(Map.of("archive",local));
            Secrets secrets=new Secrets(relay);RouteCatalog routes=new RouteCatalog(relay,secrets);properties.setEnabled(true);properties.setPartSizeBytes(65536);properties.setMaxActiveUploads(200);
            var c=new FileTransferProperties.CollectionContract();c.setTenantId("ga");c.setDatasourceId("police");c.setCollectionId("case-files");c.setTableName("case_record");c.setFieldName("document_id");c.setClients(List.of("alice"));c.setReadClients(List.of("receiver"));c.setReceiptClients(Map.of("archive","receiver"));c.setTargets(List.of("archive"));c.setRequireMalwareScan(false);c.setMaxActiveUploads(200);c.setMetadataTypes(Map.of("caseNo","STRING"));c.setRequiredMetadata(List.of("caseNo"));
            var other=new FileTransferProperties.CollectionContract();other.setTenantId("ct");other.setDatasourceId("other");other.setCollectionId("other-files");other.setClients(List.of("bob"));other.setTargets(List.of("archive"));other.setRequireMalwareScan(false);
            properties.setCollections(Map.of("cases",c,"other",other));catalog=new FileContractCatalog(properties,routes,json);catalog.afterPropertiesSet();
            var ds=new DriverManagerDataSource("jdbc:h2:mem:unstructured-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("db/h2.sql")).execute(ds);
            jdbc=new CountingJdbc(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));ledger=new RelayRepository(jdbc,DatabaseDialect.H2,new ExchangeLog(jdbc,DatabaseDialect.H2,json),json);
            repository=new FileUploadRepository(jdbc,DatabaseDialect.H2,ledger,catalog,properties);repository.afterPropertiesSet();storage=new FileStorage(relay,properties);storage.afterPropertiesSet();
            service=new FileUploadService(repository,catalog,properties,storage,tx,json,new IngressService(ledger,tx,json,relay,routes),ledger);
        }
        String init(String key,byte[] bytes) throws Exception {return named(key,"案卷.pdf",bytes);}
        String named(String key,String name,byte[] bytes) throws Exception {return service.initialize(ALICE,key,new FileUploadService.Init("police","case-files",name,bytes.length,Rules.sha256(bytes))).get("uploadId").toString();}
        void parts(String id,byte[] bytes) throws Exception {int no=1;for(int from=0;from<bytes.length;from+=65536){byte[] part=Arrays.copyOfRange(bytes,from,Math.min(bytes.length,from+65536));service.putPart(ALICE,id,no++,Rules.sha256(part),part.length,new ByteArrayInputStream(part));}}
        void submit(String id){service.submit(ALICE,id,"push-"+id,new FileUploadService.Submission("case-001",Map.of("caseNo","DEMO-001")));}
        @Override public void close() throws Exception {storage.close();jdbc.execute("SHUTDOWN");}
    }
    private static byte[] pdf(int size){byte[] bytes=new byte[size];new Random(42).nextBytes(bytes);System.arraycopy("%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,bytes,0,9);return bytes;}
    private static void error(int status,Runnable action){assertEquals(status,assertThrows(RelayException.class,action::run).status());}
    @Test void resumableUploadPublishesOneLedgerMessageAndSeparateBusinessReceipt() throws Exception {
        try(var f=new Fixture()){
            byte[] bytes=pdf(170000);String id=f.init("case-one",bytes);f.submit(id);
            assertEquals("WAITING_FILE",f.service.status(ALICE,id).get("deliveryStatus"));
            error(409,()->f.service.complete(ALICE,id));f.parts(id,bytes);
            byte[] first=Arrays.copyOf(bytes,65536);f.service.putPart(ALICE,id,1,Rules.sha256(first),first.length,new ByteArrayInputStream(first));
            assertEquals(id,f.init("case-one",bytes));
            assertEquals(3,f.service.parts(ALICE,id,0,100).get("parts") instanceof List<?> list?list.size():0);
            f.service.complete(ALICE,id);assertTrue(f.service.runOne());
            var upload=f.repository.raw(id,false).orElseThrow();assertEquals("READY",upload.state());assertEquals("COMMITTED",upload.text("quota_state"));assertEquals(Rules.sha256(bytes),upload.text("final_sha256"));
            assertArrayEquals(bytes,Files.readAllBytes(f.service.content(ALICE,id)));assertEquals("HEADER_VERIFIED_NOT_SCANNED",upload.text("security_state"));
            String message=upload.text("message_id");var deliveries=f.ledger.deliveries(message);assertEquals(1,deliveries.size());assertEquals(Kind.FILE_LOCAL,deliveries.getFirst().kind());assertEquals("INGESTED",f.ledger.file(id).orElseThrow().status());
            assertEquals(0,Base64.getDecoder().decode(f.ledger.message(message).orElseThrow().payload()).length);
            error(404,()->f.service.status(BOB,id));assertEquals("READY",f.service.status(RECEIVER,id).get("state"));
            var d=deliveries.getFirst();var receipt=new FileUploadService.Receipt(d.id(),upload.text("final_sha256"),"ACCEPTED","demo-received-001");
            error(404,()->f.service.receipt(ALICE,id,"receipt-1",receipt));error(409,()->f.service.receipt(RECEIVER,id,"receipt-1",receipt));
            var packet=new Packet(message,d.id(),f.ledger.message(message).orElseThrow().routeCode(),f.ledger.message(message).orElseThrow().createdAt(),null,upload.text("payload_path"),upload.text("file_name"),Map.of());
            var result=new LocalConnector(f.relay,f.json).deliver(f.json.read(d.plan(),TargetPlan.class),packet);assertEquals((long)bytes.length,result.get("size"));
            f.jdbc.update("UPDATE ht_delivery SET status='SUCCEEDED' WHERE id=?",d.id());assertEquals(false,f.service.receipt(RECEIVER,id,"receipt-1",receipt).get("duplicate"));assertEquals(true,f.service.receipt(RECEIVER,id,"receipt-1",receipt).get("duplicate"));
            error(409,()->f.service.receipt(RECEIVER,id,"receipt-2",new FileUploadService.Receipt(d.id(),upload.text("final_sha256"),"REJECTED","different")));
            assertEquals("SUCCEEDED",f.service.status(ALICE,id).get("deliveryStatus"));assertFalse(f.service.runOne());
        }
    }
    @Test void corruptBodiesCannotReplaceConfirmedPartsAndRejectedFilesReleaseQuota() throws Exception {
        try(var f=new Fixture()){
            byte[] bytes=pdf(500);String id=f.init("checksum",bytes);f.parts(id,bytes);byte[] corrupt=bytes.clone();corrupt[40]++;
            RelayException bad=assertThrows(RelayException.class,()->f.service.putPart(ALICE,id,1,Rules.sha256(bytes),corrupt.length,new ByteArrayInputStream(corrupt)));assertEquals("PART_CHECKSUM_MISMATCH",bad.code());
            assertEquals("COMPLETE",f.repository.part(id,1).orElseThrow().get("state"));f.service.complete(ALICE,id);f.service.runOne();assertArrayEquals(bytes,Files.readAllBytes(f.service.content(ALICE,id)));
            byte[] wrong="not a pdf".getBytes();String invalid=f.init("invalid",wrong);f.parts(invalid,wrong);f.service.complete(ALICE,invalid);f.service.runOne();assertEquals("REJECTED",f.repository.raw(invalid,false).orElseThrow().state());f.service.runOne();assertEquals("RELEASED",f.repository.raw(invalid,false).orElseThrow().text("quota_state"));
            assertEquals((long)bytes.length,f.jdbc.queryForObject("SELECT committed_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));assertEquals(0L,f.jdbc.queryForObject("SELECT reserved_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));
        }
    }
    @Test void interruptedPartCanResumeAndExpiredUnsubmittedSessionIsReclaimed() throws Exception {
        try(var f=new Fixture()){
            byte[] bytes=pdf(900);String id=f.init("interrupted",bytes);
            InputStream interrupted=new InputStream(){int i;public int read() throws IOException {if(i++>30)throw new IOException("simulated disconnect");return 1;}};
            assertThrows(IOException.class,()->f.service.putPart(ALICE,id,1,Rules.sha256(bytes),bytes.length,interrupted));assertTrue(f.repository.part(id,1).isEmpty());f.parts(id,bytes);
            f.jdbc.update("UPDATE ht_file_upload SET expires_at=1 WHERE upload_id=?",id);assertTrue(f.service.runOne());assertEquals("EXPIRED",f.repository.raw(id,false).orElseThrow().state());assertEquals(0L,f.jdbc.queryForObject("SELECT reserved_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));
        }
    }
    @Test void twentyAndHundredRowsUseFixedQueriesAndMixedTenantBatchIsRejected() throws Exception {
        try(var f=new Fixture()){
            List<String> ids=new ArrayList<>();for(int i=0;i<100;i++)ids.add(f.init("batch-"+i,pdf(30)));
            for(int count:List.of(20,100)){f.jdbc.selects=0;assertEquals(count,f.service.statuses(ALICE,ids.subList(0,count)).size());assertEquals(3,f.jdbc.selects,"批量状态必须固定3次读取");f.jdbc.selects=0;assertEquals(count,((List<?>)f.service.page(ALICE,count,0).get("rows")).size());assertEquals(4,f.jdbc.selects,"分页必须固定4次读取");}
            String foreign=f.service.initialize(BOB,"foreign",new FileUploadService.Init("other","other-files","other.pdf",30,null)).get("uploadId").toString();
            List<String> mixed=new ArrayList<>(ids.subList(0,19));mixed.add(foreign);f.jdbc.selects=0;error(404,()->f.service.statuses(ALICE,mixed));assertEquals(1,f.jdbc.selects);assertEquals(0L,f.service.page(RECEIVER,20,0).get("total"));
            assertThrows(RelayException.class,()->f.service.initialize(BOB,"forged",new FileUploadService.Init("police","case-files","forged.pdf",30,null)));
        }
    }
    @Test void readyFileAfterWorkerInterruptionCanPublishFromPersistedCheckpoint() throws Exception {
        try(var f=new Fixture()){
            byte[] bytes=pdf(100);String id=f.init("restart",bytes);f.parts(id,bytes);f.service.complete(ALICE,id);f.service.runOne();assertEquals("",f.repository.raw(id,false).orElseThrow().text("message_id"));
            f.submit(id);f.jdbc.update("UPDATE ht_file_upload SET lease_token='crashed',lease_until=1 WHERE upload_id=?",id);assertTrue(f.service.runOne());String message=f.repository.raw(id,false).orElseThrow().text("message_id");assertTrue(f.ledger.message(message).isPresent());assertFalse(f.service.runOne());assertEquals(1,f.ledger.deliveries(message).size());
        }
    }
    @Test void concurrentPartIsFencedAndCannotBeCompletedWhileItIsWriting() throws Exception {
        try(var f=new Fixture();var pool=java.util.concurrent.Executors.newFixedThreadPool(1)){
            byte[] bytes=pdf(1000);String id=f.init("concurrent-part",bytes);
            var entered=new java.util.concurrent.CountDownLatch(1);var resume=new java.util.concurrent.CountDownLatch(1);
            InputStream held=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int off,int len){entered.countDown();try{if(!resume.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return super.read(b,off,len);}};
            var first=pool.submit(()->f.service.putPart(ALICE,id,1,Rules.sha256(bytes),bytes.length,held));assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(409,assertThrows(RelayException.class,()->f.service.putPart(ALICE,id,1,Rules.sha256(bytes),bytes.length,new ByteArrayInputStream(bytes))).status());
            error(409,()->f.service.complete(ALICE,id));resume.countDown();assertEquals("COMPLETE",first.get(10,java.util.concurrent.TimeUnit.SECONDS).get("status"));
            f.service.complete(ALICE,id);f.service.runOne();assertArrayEquals(bytes,Files.readAllBytes(f.service.content(ALICE,id)));
        }
    }
    @Test void collectionQuotaFailureRollsBackGlobalReservationAndAbortIsIdempotent() throws Exception {
        try(var f=new Fixture()){
            var c=f.catalog.get("police","case-files");f.jdbc.update("UPDATE ht_storage_quota SET limit_bytes=99 WHERE quota_key=?",f.catalog.quotaKey(c));
            assertEquals(503,assertThrows(RelayException.class,()->f.init("no-space",pdf(100))).status());
            assertEquals(0L,f.jdbc.queryForObject("SELECT reserved_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));assertEquals(0L,f.jdbc.queryForObject("SELECT COUNT(*) FROM ht_file_upload",Long.class));
            f.jdbc.update("UPDATE ht_storage_quota SET limit_bytes=100000 WHERE quota_key=?",f.catalog.quotaKey(c));String id=f.init("cancel",pdf(100));f.service.abort(ALICE,id);f.service.abort(ALICE,id);f.service.runOne();assertFalse(f.service.runOne());
            assertEquals(0L,f.jdbc.queryForObject("SELECT reserved_bytes FROM ht_storage_quota WHERE quota_key='GLOBAL'",Long.class));assertEquals("RELEASED",f.repository.raw(id,false).orElseThrow().text("quota_state"));
        }
    }
    @Test void kafkaDescriptorContainsOnlyPublicReferenceAndNeverBinaryOrDiskPath(){
        var worker=new DeliveryWorker(null,null,new Json(),null,null,null,null,null,null);
        try{byte[] raw=pdf(1000);var packet=new Packet("m","d","file.cases",1,raw,"C:/private/opaque-payload","a.pdf",Map.of("fileId","file-1","contentUrl","/api/v1/file-uploads/file-1/content","sha256",Rules.sha256(raw)));
            var descriptor=worker.fileReference(packet);assertFalse(descriptor.isFile());var data=new Json().object(descriptor.bytes());assertEquals("d",data.get("deliveryId"));assertEquals("file-1",data.get("fileId"));assertFalse(new String(descriptor.bytes(),java.nio.charset.StandardCharsets.UTF_8).contains("private"));assertTrue(descriptor.bytes().length<1000);
        }finally{worker.destroy();}
    }
    @Test void lostCommitResponseCannotDeleteAnAlreadyConfirmedPart() throws Exception {
        try(var f=new Fixture()){
            byte[] bytes=pdf(200);String id=f.init("lost-commit",bytes);var transactions=new java.util.concurrent.atomic.AtomicInteger();
            var uncertain=new TransactionTemplate(f.tx.getTransactionManager()){
                @Override public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action){T result=super.execute(action);if(transactions.incrementAndGet()==2)throw new org.springframework.dao.DataAccessResourceFailureException("simulated response loss after successful commit");return result;}
            };
            var service=new FileUploadService(f.repository,f.catalog,f.properties,f.storage,uncertain,f.json,null,f.ledger);
            assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->service.putPart(ALICE,id,1,Rules.sha256(bytes),bytes.length,new ByteArrayInputStream(bytes)));
            var part=f.repository.part(id,1).orElseThrow();assertEquals("COMPLETE",part.get("state"));assertTrue(Files.exists(Path.of(part.get("payload_path").toString())));
            f.service.complete(ALICE,id);f.service.runOne();assertArrayEquals(bytes,Files.readAllBytes(f.service.content(ALICE,id)));
        }
    }
    @Test void realGeneratedPhotosAndDocxUploadAndMacroContainerIsRejected() throws Exception {
        try(var f=new Fixture()){
            var image=new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB);
            for(String format:List.of("jpg","png")){
                var output=new ByteArrayOutputStream();assertTrue(javax.imageio.ImageIO.write(image,format,output));byte[] bytes=output.toByteArray();
                String id=f.named("photo-"+format,"现场照片."+format,bytes);f.parts(id,bytes);f.service.complete(ALICE,id);f.service.runOne();assertEquals("READY",f.repository.raw(id,false).orElseThrow().state());assertArrayEquals(bytes,Files.readAllBytes(f.service.content(ALICE,id)));
            }
            for(boolean macro:List.of(false,true)){
                var output=new ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(output)){
                    Map<String,String> entries=new LinkedHashMap<>();entries.put("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");entries.put("word/document.xml","<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>DEMO document</w:t></w:r></w:p></w:body></w:document>");
                    if(macro)entries.put("word/vbaProject.bin","rejected test macro marker");
                    for(var entry:entries.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
                }
                byte[] bytes=output.toByteArray();String id=f.named("document-"+macro,"案件文档.docx",bytes);f.parts(id,bytes);f.service.complete(ALICE,id);f.service.runOne();assertEquals(macro?"REJECTED":"READY",f.repository.raw(id,false).orElseThrow().state());
                if(macro){f.service.runOne();assertEquals("FILE_TYPE_MISMATCH",f.repository.raw(id,false).orElseThrow().text("error_code"));}
                else assertArrayEquals(bytes,Files.readAllBytes(f.service.content(ALICE,id)));
            }
        }
    }
}

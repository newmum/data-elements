package com.haitong;

import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.config.location=classpath:/application.yml","db.type=h2","db.init-schema=true","db.username=sa","db.password=",
    "db.url=jdbc:h2:mem:file-http;MODE=MySQL;DB_CLOSE_DELAY=-1","relay.worker.enabled=false","relay.limits.min-free-disk-bytes=0",
    "relay.security.api-key=file-producer-0123456789abcdef0123456789","relay.security.admin-key=file-admin-0123456789abcdef0123456789",
    "relay.security.clients.receiver.api-key=file-receiver-0123456789abcdef0123456789",
    "relay.files.max-streams=1","relay.files.enabled=true","relay.files.collections.cases.tenant-id=ga","relay.files.collections.cases.datasource-id=police",
    "relay.files.collections.cases.table-name=case_record","relay.files.collections.cases.field-name=document_id","relay.files.collections.cases.file-code=case_document",
    "relay.files.collections.cases.collection-id=case-files","relay.files.collections.cases.clients[0]=default","relay.files.collections.cases.read-clients[0]=receiver",
    "relay.files.collections.cases.targets[0]=local","relay.files.collections.cases.receipt-clients.local=receiver","relay.files.collections.cases.require-malware-scan=false",
    "relay.files.collections.cases.metadata-types.caseNo=STRING","relay.files.collections.cases.required-metadata[0]=caseNo"
})
@EnabledIfSystemProperty(named="haitong.loopback.tests",matches="true")
class FileUploadHttpTest {
    private static final String PRODUCER="file-producer-0123456789abcdef0123456789",RECEIVER="file-receiver-0123456789abcdef0123456789";
    private static final Path WORK=Path.of(System.getProperty("haitong.mysql.work-dir","../logs/haitong-files/http-"+UUID.randomUUID())).toAbsolutePath().normalize();
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) throws IOException {
        r.add("relay.work-dir",WORK::toString);r.add("logging.file.name",()->WORK.resolve("service.log").toString());
        r.add("magic-api.resource.location",()->WORK.resolve("magic-api").toString());r.add("magic-api.show-url",()->false);
        // Optional real MySQL verification uses a private properties file, never command-line credentials.
        String privateConfig=System.getProperty("haitong.mysql.config");if(privateConfig!=null){
            Properties values=new Properties();try(Reader reader=Files.newBufferedReader(Path.of(privateConfig),java.nio.charset.StandardCharsets.UTF_8)){values.load(reader);}
            for(String key:List.of("db.url","db.username","db.password")){String value=Objects.requireNonNull(values.getProperty(key));r.add(key,()->value);}
            r.add("db.type",()->"mysql");r.add("db.init-schema",()->false);
        }
    }
    @LocalServerPort int port;@Autowired Json json;@Autowired DeliveryWorker worker;@Autowired RelayRepository repo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    record Reply(int statusCode,byte[] body,Map<String,List<String>> headers) {}
    Reply call(String method,String path,String key,String idem,byte[] body,Map<String,String> headers) throws Exception {
        // Blocking sockets also work on Windows/JDK installations with a broken selector pipe.
        var request=(HttpURLConnection)URI.create("http://127.0.0.1:"+port+path).toURL().openConnection(Proxy.NO_PROXY);request.setConnectTimeout(10000);request.setReadTimeout(120000);request.setRequestMethod(method);request.setRequestProperty("X-Api-Key",key);
        if(idem!=null)request.setRequestProperty("Idempotency-Key",idem);headers.forEach(request::setRequestProperty);
        try{if(body!=null){request.setDoOutput(true);if(headers.containsKey("Transfer-Encoding"))request.setChunkedStreamingMode(8192);else request.setFixedLengthStreamingMode(body.length);try(OutputStream out=request.getOutputStream()){out.write(body);}}
            int code=request.getResponseCode();InputStream in=code>=400?request.getErrorStream():request.getInputStream();byte[] result=in==null?new byte[0]:in.readAllBytes();if(in!=null)in.close();return new Reply(code,result,request.getHeaderFields());
        }finally{request.disconnect();}
    }
    Map<String,Object> object(Reply response,int status){assertEquals(status,response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));return json.object(response.body());}
    Map<String,Object> post(String path,String idem,Object body) throws Exception {return object(call("POST",path,PRODUCER,idem,json.bytes(body),Map.of("Content-Type","application/json")),202);}
    Map<String,Object> awaitReady(String id) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);Map<String,Object> state;
        do{state=object(call("GET","/api/v1/file-uploads/"+id,PRODUCER,null,null,Map.of()),200);if(state.get("state").equals("READY")&&!state.get("deliveryStatus").equals("WAITING_FILE"))return state;Thread.sleep(200);}while(System.nanoTime()<deadline);
        fail("File did not become ready: "+state);return state;
    }
    @Test void authenticatedHttpLifecycleRangeAndReceipt() throws Exception {
        byte[] content="%PDF-1.7\nDemo file upload and delivery\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String nonce=UUID.randomUUID().toString();
        var init=post("/api/file/init","http-case-"+nonce,Map.of("datasourceId","police","collectionId","case-files","fileName","测试案卷.pdf","size",content.length,"sha256",Rules.sha256(content)));
        String id=init.get("uploadId").toString();String legacy="/api/v1/file-uploads/"+id;
        String query="?id="+id;
        assertEquals("/api/file/status"+query,init.get("statusUrl"));
        assertEquals(404,call("GET","/api/file/status"+query,RECEIVER,null,null,Map.of()).statusCode());
        var accepted=post("/api/file/submit"+query,"http-submit-"+nonce,Map.of("businessKey","DEMO-HTTP-001","metadata",Map.of("caseNo","DEMO-HTTP-001")));String message=accepted.get("messageId").toString();
        assertEquals("WAITING_FILE",object(call("GET","/api/v1/messages/"+message,PRODUCER,null,null,Map.of()),200).get("status"));
        assertEquals(200,call("PUT",legacy+"/parts/1?id=ignored&part=99",PRODUCER,null,content,Map.of("Content-Type","application/octet-stream","X-Part-SHA256",Rules.sha256(content))).statusCode());
        object(call("POST","/api/file/complete"+query,PRODUCER,null,null,Map.of()),202);
        assertEquals(legacy+"/content",awaitReady(id).get("contentUrl"));assertTrue(worker.runOne(Kind.FILE_LOCAL));
        assertEquals("/api/file/content"+query,object(call("GET","/api/file/status"+query,PRODUCER,null,null,Map.of()),200).get("contentUrl"));
        var range=call("GET","/api/file/content"+query,RECEIVER,null,null,Map.of("Range","bytes=5-12"));assertEquals(206,range.statusCode());assertArrayEquals(Arrays.copyOfRange(content,5,13),range.body());
        assertEquals(416,call("GET","/api/file/content"+query,RECEIVER,null,null,Map.of("Range","bytes=900-1000")).statusCode());
        for(String url:List.of("/api/file/content"+query,legacy+"/content")) {
            var head=call("HEAD",url,RECEIVER,null,null,Map.of());
            assertEquals(200,head.statusCode());assertEquals(0,head.body().length);
            assertEquals(content.length,Long.parseLong(head.headers().get("Content-Length").getFirst()));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ht_exchange_log WHERE phase='START' AND source_ref=?",
                Integer.class, "HEAD /api/file/content"));
        String contentUrl = "/api/file/content" + query;
        var partialHead = call("HEAD", contentUrl, RECEIVER, null, null, Map.of("Range", "bytes=5-12"));
        assertEquals(206, partialHead.statusCode());
        assertEquals(0, partialHead.body().length);
        for (String header : List.of("Content-Range", "Content-Length", "Content-Type", "ETag",
                "Accept-Ranges", "Content-Disposition", "Cache-Control", "X-Content-Type-Options")) {
            assertNotNull(range.headers().get(header), header);
            assertEquals(range.headers().get(header), partialHead.headers().get(header), header);
        }
        var invalidRange = call("HEAD", contentUrl, RECEIVER, null, null, Map.of("Range", "bytes=900-1000"));
        assertEquals(416, invalidRange.statusCode());
        assertEquals("bytes */" + content.length, invalidRange.headers().get("Content-Range").getFirst());
        assertEquals(0, invalidRange.body().length);
        var headWithStaleEtag = call("HEAD", contentUrl, RECEIVER, null, null,
                Map.of("Range", "bytes=5-12", "If-Range", "\"invalid\""));
        assertEquals(200, headWithStaleEtag.statusCode());
        assertEquals(Integer.toString(content.length), headWithStaleEtag.headers().get("Content-Length").getFirst());
        assertEquals(0, headWithStaleEtag.body().length);
        assertEquals(401, call("HEAD", contentUrl, "invalid", null, null, Map.of()).statusCode());
        assertEquals(404, call("HEAD", "/api/file/content?id=validation-missing", RECEIVER, null, null, Map.of()).statusCode());
        assertArrayEquals(content,call("GET",legacy+"/content",RECEIVER,null,null,Map.of("Range","bytes=5-9","If-Range","\"wrong\"")).body());
        var delivery=repo.deliveries(message).getFirst();var receipt=Map.of("deliveryId",delivery.id(),"sha256",Rules.sha256(content),"status","ACCEPTED","businessRef","received-http-001");
        assertEquals(200,call("POST","/api/file/receipt"+query,RECEIVER,"receipt-http-"+nonce,json.bytes(receipt),Map.of("Content-Type","application/json")).statusCode());
        assertEquals(422,call("POST","/api/file/init",PRODUCER,"forge-tenant",json.bytes(Map.of("tenantId","ct","datasourceId","police","collectionId","case-files","fileName","x.pdf","size",10)),Map.of("Content-Type","application/json")).statusCode());
        assertEquals(1,repo.deliveries(message).size());
    }
    @Test void singleRequestHttpUploadIsDefaultAndUsesExistingContentAndDeliveryLifecycle() throws Exception {
        byte[] content="%PDF-1.7\nSingle request demo\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String query="?datasourceId=police&tableName=case_record&fieldName=document_id&fileName="+URLEncoder.encode("演示案卷.pdf",java.nio.charset.StandardCharsets.UTF_8);
        String key="single-http-"+UUID.randomUUID();String endpoint="/api/v1/file-uploads/single"+query;
        assertEquals(401,call("POST",endpoint,"invalid",key,content,Map.of("Content-Type","application/octet-stream")).statusCode());
        assertEquals(404,call("POST",endpoint,RECEIVER,key,content,Map.of("Content-Type","application/octet-stream")).statusCode());
        var accepted=object(call("POST",endpoint,PRODUCER,key,content,Map.of("Content-Type","application/octet-stream","Transfer-Encoding","chunked")),202);
        String id=accepted.get("fileId").toString();assertEquals("SINGLE",accepted.get("uploadMode"));
        assertEquals(id,object(call("POST",endpoint,PRODUCER,key,content,Map.of("Content-Type","application/octet-stream")),202).get("fileId"));
        post("/api/v1/file-uploads/"+id+"/submit",key,Map.of("businessKey","SINGLE-DEMO","metadata",Map.of("caseNo","SINGLE-DEMO")));
        awaitReady(id);assertTrue(worker.runOne(Kind.FILE_LOCAL));
        assertArrayEquals(content,call("GET","/api/v1/file-uploads/"+id+"/content",RECEIVER,null,null,Map.of()).body());
        for(String obsolete:List.of("size=1","sha256="+Rules.sha256(content),"collectionId=case-files"))assertEquals(422,call("POST",endpoint+"&"+obsolete,PRODUCER,key,content,Map.of("Content-Type","application/octet-stream")).statusCode());
        assertEquals(422,call("POST",endpoint+"&fieldName=outside",PRODUCER,key,content,Map.of("Content-Type","application/octet-stream")).statusCode());
        assertEquals(422,call("POST",endpoint+"&tenantId=other",PRODUCER,key,content,Map.of("Content-Type","application/octet-stream")).statusCode());
    }
    byte[] multipart(String boundary,Map<String,String> fields,byte[] content) throws IOException {
        var out=new ByteArrayOutputStream();
        for(var entry:fields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"验证案卷.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(content);out.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));return out.toByteArray();
    }
    @Test void unifiedMultipartSingleSubmitsAndSupportsAliasWithoutBreakingJsonPost() throws Exception {
        String boundary="validation-file-push-boundary",key="validation-single-"+UUID.randomUUID();
        byte[] content="%PDF-1.7\nvalidation single push\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var fields=Map.of("datasourceId","police","fileCode","case_document","businessKey","validation-single","metadata","{\"caseNo\":\"VALIDATION-1\"}");
        byte[] body=multipart(boundary,fields,content);var headers=Map.of("Content-Type","multipart/form-data; boundary="+boundary);
        assertEquals(401,call("PUT","/api/v1/data-push","invalid",key,body,headers).statusCode());
        assertEquals(404,call("PUT","/api/v1/data-push",RECEIVER,key,body,headers).statusCode());
        var accepted=object(call("PUT","/api/push/single",PRODUCER,key,body,headers),202);String id=accepted.get("uploadId").toString();
        assertEquals("case_document",accepted.get("fileCode"));assertEquals("验证案卷.pdf",accepted.get("fileName"));
        assertEquals("/api/push/status?id="+id,accepted.get("statusUrl"));
        for (String legacy : List.of("/api/v1/data-push","/api/v1/data-push/single")) {
            var replay=object(call("PUT",legacy,PRODUCER,key,body,headers),202);
            assertEquals(id,replay.get("uploadId"));
            assertEquals("/api/v1/data-push/uploads/"+id,replay.get("statusUrl"));
        }
        awaitReady(id);assertTrue(worker.runOne(Kind.FILE_LOCAL));
        assertEquals("SUCCEEDED",object(call("GET","/api/v1/data-push/uploads/"+id,PRODUCER,null,null,Map.of()),200).get("deliveryStatus"));
        assertEquals(422,call("POST","/api/v1/data-push",PRODUCER,"validation-json",json.bytes(Map.of()),Map.of("Content-Type","application/json")).statusCode());
        assertEquals(422,call("PUT","/api/v1/data-push",PRODUCER,"validation-unknown",multipart(boundary,Map.of("datasourceId","police","fileCode","case_document","tenantId","other"),content),headers).statusCode());
    }
    @Test void unifiedChunksCombineCompletionAndSubmission() throws Exception {
        byte[] data="%PDF-1.7\nvalidation chunk push\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var init=post("/api/push/init","validation-init-"+UUID.randomUUID(),Map.of("datasourceId","police","fileCode","case_document","fileName","large.pdf","size",data.length));
        String id=init.get("uploadId").toString();
        String legacy="/api/v1/data-push/uploads/"+id;
        String status="/api/push/status?id="+id;
        assertEquals(status,init.get("statusUrl"));
        Map<String,String> partHeaders=Map.of("Content-Type","application/octet-stream","X-Part-SHA256",Rules.sha256(data));
        assertEquals(404,call("PUT","/api/push/part?id="+id+"&part=1",RECEIVER,null,data,partHeaders).statusCode());
        assertEquals(200,call("PUT",legacy+"/parts/1?id=ignored&part=99",PRODUCER,null,data,partHeaders).statusCode());
        assertEquals(200,call("PUT","/api/push/part?id="+id+"&part=1",PRODUCER,null,data,partHeaders).statusCode());
        var parts=object(call("GET","/api/push/parts?id="+id+"&after=0&limit=100",PRODUCER,null,null,Map.of()),200);
        assertEquals(1,((List<?>)parts.get("parts")).size());
        var accepted=post("/api/push/complete?id="+id,"validation-complete-"+UUID.randomUUID(),Map.of("businessKey","validation-chunk","metadata",Map.of("caseNo","VALIDATION-2")));
        assertEquals(status,accepted.get("statusUrl"));
        awaitReady(init.get("uploadId").toString());assertTrue(worker.runOne(Kind.FILE_LOCAL));
        assertEquals("SUCCEEDED",object(call("GET",status,PRODUCER,null,null,Map.of()),200).get("deliveryStatus"));
        assertEquals(legacy,object(call("GET",legacy,PRODUCER,null,null,Map.of()),200).get("statusUrl"));
    }
    @Test @EnabledIfSystemProperty(named="haitong.large-file.tests",matches="true") void threeGiBFileStreamsThroughRealHttpAndLocalDelivery() throws Exception {
        long size=3L*1024*1024*1024;Path source=WORK.resolve("three-gib.pdf");Files.createDirectories(WORK);
        try(FileChannel out=FileChannel.open(source,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            out.write(ByteBuffer.wrap("%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));out.position(size-1);out.write(ByteBuffer.wrap(new byte[]{0}));out.force(true);
        }
        long started=System.nanoTime();String hash=PayloadFiles.sha256(source);
        var init=post("/api/v1/file-uploads","large-http",Map.of("datasourceId","police","collectionId","case-files","fileName","three-gib.pdf","size",size,"sha256",hash));String id=init.get("uploadId").toString(),path="/api/v1/file-uploads/"+id;
        int partSize=((Number)init.get("partSize")).intValue();
        try(InputStream input=Files.newInputStream(source)){int no=0;byte[] part;while((part=input.readNBytes(partSize)).length>0){assertEquals(200,call("PUT",path+"/parts/"+(++no),PRODUCER,null,part,Map.of("Content-Type","application/octet-stream","X-Part-SHA256",Rules.sha256(part))).statusCode());}}
        post(path+"/submit","large-submit",Map.of("businessKey","DEMO-LARGE","metadata",Map.of("caseNo","DEMO-LARGE")));object(call("POST",path+"/complete",PRODUCER,null,null,Map.of()),202);
        var state=awaitReady(id);assertEquals(hash,state.get("sha256"));String message=state.get("messageId").toString();assertTrue(worker.runOne(Kind.FILE_LOCAL));assertEquals("SUCCEEDED",repo.deliveries(message).getFirst().status());
        Path destination;try(var stream=Files.walk(WORK.resolve("out"))){destination=stream.filter(p->p.getFileName().toString().startsWith(message)&&p.toString().endsWith(".pdf")).findFirst().orElseThrow();}
        assertEquals(size,Files.size(destination));assertEquals(hash,PayloadFiles.sha256(destination));
        Files.writeString(WORK.resolve("large-file-result.json"),json.write(Map.of("bytes",size,"parts",init.get("partCount"),"sha256",hash,"elapsedSeconds",(System.nanoTime()-started)/1e9,"deliveryStatus","SUCCEEDED","maxHeapBytes",Runtime.getRuntime().maxMemory())));
        // Remove this test's large synthetic input/output only; state and evidence stay in ignored logs.
        Files.delete(source);Files.delete(destination);Files.delete(Path.of(repo.file(id).orElseThrow().path()));
    }
    @Test @EnabledIfSystemProperty(named="haitong.example-client.tests",matches="true") void pythonStreamingClientCanRepeatWithOriginalIdempotencyKey() throws Exception {
        Files.createDirectories(WORK);Path source=WORK.resolve("client-example.pdf");Files.writeString(source,"%PDF-1.7\nExample client\n%%EOF");String key="client-example-"+UUID.randomUUID();
        Path metadata=WORK.resolve("example-metadata.json");Files.writeString(metadata,"{\"caseNo\":\"DEMO-CLIENT\"}");
        List<String> command=List.of("python","scripts/upload_file.py",source.toString(),"--base-url","http://127.0.0.1:"+port,"--datasource","police","--table","case_record","--field","document_id","--business-key","DEMO-CLIENT","--idempotency-key",key,"--metadata-file",metadata.toString(),"--wait-seconds","20");
        String message=null;
        for(int repeat=0;repeat<2;repeat++){
            Path output=WORK.resolve("example-client-"+repeat+".json"),error=WORK.resolve("example-client-"+repeat+".log");
            ProcessBuilder builder=new ProcessBuilder(command).redirectOutput(output.toFile()).redirectError(error.toFile());builder.environment().put("HAITONG_API_KEY",PRODUCER);Process client=builder.start();
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            try{while(client.isAlive()&&System.nanoTime()<deadline){worker.runOne(Kind.FILE_LOCAL);Thread.sleep(100);}assertFalse(client.isAlive(),"example client timed out");assertEquals(0,client.exitValue(),Files.readString(error));
                var result=json.object(Files.readAllBytes(output));var state=(Map<?,?>)result.get("current");assertEquals("SUCCEEDED",state.get("deliveryStatus"));if(message==null)message=state.get("messageId").toString();else assertEquals(message,state.get("messageId"));
            }finally{if(client.isAlive())client.destroyForcibly();}
        }
        assertEquals(1,repo.deliveries(message).size());
    }
}

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
    "relay.files.collections.cases.table-name=case_record","relay.files.collections.cases.field-name=document_id",
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
        byte[] content="%PDF-1.7\nDemo file upload and delivery\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);String base="/api/v1/file-uploads";
        String nonce=UUID.randomUUID().toString();
        var init=post(base,"http-case-"+nonce,Map.of("datasourceId","police","collectionId","case-files","fileName","测试案卷.pdf","size",content.length,"sha256",Rules.sha256(content)));String id=init.get("uploadId").toString();String path=base+"/"+id;
        assertEquals(404,call("GET",path,RECEIVER,null,null,Map.of()).statusCode());
        var accepted=post(path+"/submit","http-submit-"+nonce,Map.of("businessKey","DEMO-HTTP-001","metadata",Map.of("caseNo","DEMO-HTTP-001")));String message=accepted.get("messageId").toString();
        assertEquals("WAITING_FILE",object(call("GET","/api/v1/messages/"+message,PRODUCER,null,null,Map.of()),200).get("status"));
        assertEquals(200,call("PUT",path+"/parts/1",PRODUCER,null,content,Map.of("Content-Type","application/octet-stream","X-Part-SHA256",Rules.sha256(content))).statusCode());
        object(call("POST",path+"/complete",PRODUCER,null,null,Map.of()),202);awaitReady(id);assertTrue(worker.runOne(Kind.FILE_LOCAL));
        var range=call("GET",path+"/content",RECEIVER,null,null,Map.of("Range","bytes=5-12"));assertEquals(206,range.statusCode());assertArrayEquals(Arrays.copyOfRange(content,5,13),range.body());
        assertEquals(416,call("GET",path+"/content",RECEIVER,null,null,Map.of("Range","bytes=900-1000")).statusCode());
        assertEquals(content.length,Long.parseLong(call("HEAD",path+"/content",RECEIVER,null,null,Map.of()).headers().get("Content-Length").getFirst()));
        assertArrayEquals(content,call("GET",path+"/content",RECEIVER,null,null,Map.of("Range","bytes=5-9","If-Range","\"wrong\"")).body());
        var delivery=repo.deliveries(message).getFirst();var receipt=Map.of("deliveryId",delivery.id(),"sha256",Rules.sha256(content),"status","ACCEPTED","businessRef","received-http-001");
        assertEquals(200,call("POST",path+"/receipts",RECEIVER,"receipt-http-"+nonce,json.bytes(receipt),Map.of("Content-Type","application/json")).statusCode());
        assertEquals(422,call("POST",base,PRODUCER,"forge-tenant",json.bytes(Map.of("tenantId","ct","datasourceId","police","collectionId","case-files","fileName","x.pdf","size",10)),Map.of("Content-Type","application/json")).statusCode());
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
        List<String> command=List.of("python","examples/upload_file.py",source.toString(),"--base-url","http://127.0.0.1:"+port,"--datasource","police","--table","case_record","--field","document_id","--business-key","DEMO-CLIENT","--idempotency-key",key,"--metadata-file",metadata.toString(),"--wait-seconds","20");
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

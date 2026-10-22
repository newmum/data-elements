import com.haitong.core.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OfflineChecks {
    private static int passed;
    interface Checked {void run() throws Exception;}
    static void equal(Object expected,Object actual) {if(!Objects.equals(expected,actual)) throw new AssertionError("expected="+expected+", actual="+actual);passed++;}
    static void yes(boolean condition) {if(!condition) throw new AssertionError("condition failed");passed++;}
    static void fails(Checked op) throws Exception {try {op.run();} catch(Exception e) {passed++;return;}throw new AssertionError("expected failure");}
    static List<List<String>> csv(String text) throws Exception {
        List<List<String>> rows=new ArrayList<>();try(var reader=new CsvRecords(new StringReader(text),10000)){List<String> row;while((row=reader.next())!=null)rows.add(row);}return rows;
    }
    public static void main(String[] args) throws Exception {
        equal("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",Rules.sha256("abc".getBytes(StandardCharsets.UTF_8)));
        yes(Rules.constantEquals("a","a"));yes(!Rules.constantEquals("a","b"));yes(!Rules.constantEquals("a",null));
        equal("push-001",Rules.key("push-001"));fails(()->Rules.key(null));fails(()->Rules.key("a b"));fails(()->Rules.key("中"));fails(()->Rules.key("x".repeat(201)));
        equal("vendor-a",Rules.code("vendor-a"));fails(()->Rules.code("../a"));fails(()->Rules.code("bad/code"));
        equal("data.csv",Rules.fileName("data.csv"));fails(()->Rules.fileName("../data.csv"));fails(()->Rules.fileName("C:\\data.csv"));fails(()->Rules.fileName("file\nname"));
        equal("/a/b",Rules.remoteDirectory("//a//b/"));fails(()->Rules.remoteDirectory("/a/../b"));fails(()->Rules.remoteDirectory("relative"));
        yes(Rules.overlaps("/in","/in/nested"));yes(!Rules.overlaps("/in","/input"));yes(Rules.overlaps("/","/data"));
        fails(()->Rules.within(Path.of("/safe"),"../escape"));fails(()->Rules.within(Path.of("/safe"),"/absolute"));
        equal(".csv",Rules.extension("x.CSV"));equal(".bin",Rules.extension("no-extension"));
        equal("SUCCEEDED",Rules.aggregate(List.of("SUCCEEDED","SUCCEEDED")));equal("UNKNOWN",Rules.aggregate(List.of("UNKNOWN","SUCCEEDED")));
        equal("PARTIAL_SUCCESS",Rules.aggregate(List.of("SUCCEEDED","FAILED")));equal("FAILED",Rules.aggregate(List.of("FAILED","FAILED")));
        equal("RETRY_WAIT",Rules.aggregate(List.of("RETRY_WAIT","PENDING")));yes(Rules.terminal("FAILED"));yes(!Rules.terminal("RETRY_WAIT"));
        equal(1000L,Rules.backoffMs(1,0.5));yes(Rules.backoffMs(100,1)<=300000);yes(Rules.backoffMs(0,0)>0);
        equal(List.of(List.of("a","b"),List.of("1","2")),csv("a,b\r\n1,2\r\n"));
        equal(List.of(List.of("a,b","c")),csv("\"a,b\",c"));
        equal(List.of(List.of("a\nb","c")),csv("\"a\nb\",c\n"));
        equal(List.of(List.of("a\r\nb","c")),csv("\"a\r\nb\",c\r\n"));
        equal(List.of(List.of("say \"hi\"","")),csv("\"say \"\"hi\"\"\",\n"));
        equal(List.of(List.of("","","")),csv(",,"));equal(List.of(),csv(""));equal(List.of(List.of("")),csv("\"\""));
        equal(List.of(List.of("a","b")),csv("\uFEFFa,b"));
        fails(()->csv("a\"b,c"));fails(()->csv("\"unterminated"));fails(()->csv("\"a\"x,b"));
        fails(()->new CsvRecords(new StringReader("12345"),4).next());
        equal("hello",PayloadFiles.utf8("hello".getBytes(StandardCharsets.UTF_8)));
        fails(()->PayloadFiles.utf8(new byte[]{(byte)0xC3,(byte)0x28}));
        equal(3,PayloadFiles.readLimited(new ByteArrayInputStream(new byte[]{1,2,3}),3).length);
        fails(()->PayloadFiles.readLimited(new ByteArrayInputStream(new byte[]{1,2,3,4}),3));
        equal("abc",PayloadFiles.readLine(new StringReader("abc\n"),3));fails(()->PayloadFiles.readLine(new StringReader("abcd"),3));
        Path temp=Files.createTempDirectory("relay-offline-");
        var saved=PayloadFiles.save(temp,new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)),100,0);
        equal(7L,saved.size());yes(Files.exists(saved.path()));equal(saved.sha256(),PayloadFiles.sha256(saved.path()));
        fails(()->PayloadFiles.save(temp,new ByteArrayInputStream(new byte[20]),10,0));
        try(var listing=Files.list(temp)){yes(listing.noneMatch(p->p.toString().endsWith(".part")));}
        http();
        System.out.println("OFFLINE EXECUTABLE CHECKS PASSED: "+passed);
        System.out.println("Scope: JDK-only production invariants, CSV, spool durability mechanics, bounded HTTP. Spring/Kafka/FTP/JDBC integrations were NOT run here.");
    }
    private static void http() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var pool=Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());server.setExecutor(pool);AtomicInteger followed=new AtomicInteger();
        server.createContext("/echo",exchange->{byte[] bytes=exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/large",exchange->{byte[] bytes=new byte[1000];exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/target");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/target",exchange->{followed.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.createContext("/slow",exchange->{try{exchange.sendResponseHeaders(200,4);Thread.sleep(2500);exchange.getResponseBody().write(new byte[4]);}catch(Exception ignored){}finally{exchange.close();}});
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        try {
            var result=HttpCalls.call(URI.create(base+"/echo"),"POST","hello".getBytes(StandardCharsets.UTF_8),Map.of(),3,100);
            equal(200,result.status());equal("hello",new String(result.body(),StandardCharsets.UTF_8));
            fails(()->HttpCalls.call(URI.create(base+"/large"),"POST",new byte[0],Map.of(),3,64));
            equal(302,HttpCalls.call(URI.create(base+"/redirect"),"POST",new byte[0],Map.of(),3,64).status());equal(0,followed.get());
            long start=System.nanoTime();fails(()->HttpCalls.call(URI.create(base+"/slow"),"POST",new byte[0],Map.of(),1,64));
            yes(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2200);
        } finally {server.stop(0);pool.shutdownNow();}
    }
}

package com.haitong;

import com.haitong.config.*;
import com.haitong.connector.FtpConnector;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.apache.ftpserver.*;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Starts a real local FTP server on an ephemeral port. Does NOT test FTPS certificates or real customer servers. */
@EnabledIfSystemProperty(named="haitong.loopback.tests",matches="true")
class FtpProtocolTest {
    @TempDir Path root;
    FtpServer server;Process pythonServer;FtpConnector connector;FtpAddress address;Path home;
    @BeforeEach void setup() throws Exception {
        home=Files.createDirectories(root.resolve("ftp-home"));int port;
        try(ServerSocket socket=new ServerSocket(0)) {port=socket.getLocalPort();}
        String pythonModules=System.getProperty("haitong.ftp.python-module-path");
        if(pythonModules==null){
            FtpServerFactory factory=new FtpServerFactory();ListenerFactory listener=new ListenerFactory();listener.setPort(port);
            factory.addListener("default",listener.createListener());BaseUser user=new BaseUser();
            user.setName("relay");user.setPassword("test-password");user.setHomeDirectory(home.toString());user.setAuthorities(List.of(new WritePermission()));
            factory.getUserManager().save(user);server=factory.createServer();server.start();
        }else{
            Path script=root.resolve("ftp_server.py");try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/ftp_server.py"))){Files.copy(in,script);}
            Path logs=Files.createDirectories(Path.of("../logs/haitong-files"));pythonServer=new ProcessBuilder("python",script.toString(),Path.of(pythonModules).toAbsolutePath().normalize().toString(),home.toString(),Integer.toString(port)).redirectOutput(logs.resolve("python-ftp.log").toFile()).redirectErrorStream(true).start();
            boolean ready=false;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while(pythonServer.isAlive()&&System.nanoTime()<deadline){try(var socket=new java.net.Socket("127.0.0.1",port)){ready=true;break;}catch(java.io.IOException e){Thread.sleep(50);}}
            assertTrue(ready,"Python FTP fixture did not start");
        }
        RelayProperties p=new RelayProperties();p.setWorkDir(root.resolve("work").toString());
        p.getSecurity().setApiKey("test-client-0123456789abcdef0123456789");p.getSecurity().setAdminKey("test-admin-0123456789abcdef0123456789");
        p.getFtp().setPassword("test-password");p.getLimits().setMinFreeDiskBytes(0);
        connector=new FtpConnector(p,new Secrets(p),new Json());address=new FtpAddress("FTP","127.0.0.1",port,"relay",true,false,"UTF-8",5);
    }
    @AfterEach void stop() {if(server!=null)server.stop();if(pythonServer!=null){pythonServer.destroy();try{if(!pythonServer.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))pythonServer.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();pythonServer.destroyForcibly();}}}
    private TargetPlan target() {return new TargetPlan("ftp",Kind.FTP,"","/out",null,address,null);}
    private Packet packet(byte[] bytes) {
        return new Packet(
                "00000000-0000-4000-8000-000000000001",
                "00000000-0000-4000-8000-000000000002",
                "table-to-ftp", 0, bytes, null, null,
                Map.of("datasourceId", "source-123", "tableName", "demo_table", "schemaVersion", 1));
    }
    @Test void writesVerifiedFileAndManifestAndConvergesOnRetry() throws Exception {
        Packet p=packet("{\"records\":[{\"x\":1}]}".getBytes(StandardCharsets.UTF_8));var result=connector.deliver(target(),p);
        Path dir=home.resolve("out/source-123/demo_table");String name=result.get("fileName").toString();
        assertEquals("[{\"x\":1}]",Files.readString(dir.resolve(name)));assertTrue(Files.exists(dir.resolve(name+".done")));
        assertEquals(result,connector.deliver(target(),p));assertEquals(1,connector.markers(address,"/out/source-123/demo_table").size());
        assertEquals(result.get("sha256"), Rules.sha256(Files.readAllBytes(dir.resolve(name))));
    }
    @Test void changedContentsWithSameIdentityAreNotOverwritten() throws Exception {
        connector.deliver(target(),packet("{\"records\":[{\"x\":1}]}".getBytes(StandardCharsets.UTF_8)));
        var error=assertThrows(DeliveryFailure.class,()->connector.deliver(target(),packet("{\"records\":[{\"x\":2}]}".getBytes(StandardCharsets.UTF_8))));
        assertTrue(error.permanent);assertEquals("FTP_CONTENT_CONFLICT",error.code);
    }
    @Test void healthCheckDoesNotWriteFilesAndTraversalIsRejected() throws Exception {
        assertEquals("UP",connector.health(address).get("status"));
        try(var listing=Files.list(home)) {assertEquals(0,listing.count());}
        assertThrows(IllegalArgumentException.class,()->connector.marker(address,"/out","../secret"));
    }
    @Test void collectionFileUsesCollectionPathAndPreservesRawBytesAndBusinessManifest() throws Exception {
        Path work=Files.createDirectories(root.resolve("work"));byte[] bytes="%PDF-1.7\nDEMO FILE\n%%EOF".getBytes(StandardCharsets.US_ASCII);Path source=work.resolve("opaque-payload");Files.write(source,bytes);
        var packet=new Packet("11111111-1111-4111-8111-111111111111","22222222-2222-4222-8222-222222222222","file.cases",1,null,source.toString(),"案卷.pdf",Map.of("datasourceId","police","collectionId","case-files","fileId","fixed-file-1","contractVersion",1,"businessKey","DEMO-CASE","metadata",Map.of("caseNo","DEMO-CASE")));
        var target=new TargetPlan("ftp",Kind.FILE_FTP,"","/out",null,address,null);var first=connector.deliver(target,packet);var second=connector.deliver(target,packet);assertEquals(first,second);
        Path file=home.resolve("out/police/case-files/"+first.get("fileName"));assertArrayEquals(bytes,Files.readAllBytes(file));var marker=new Json().object(Files.readAllBytes(Path.of(file+".done")));
        assertEquals("fixed-file-1",marker.get("fileId"));assertEquals(packet.deliveryId(),marker.get("deliveryId"));assertEquals("case-files",marker.get("collectionId"));assertEquals("DEMO-CASE",marker.get("businessKey"));assertFalse(marker.containsKey("tableName"));
    }
}

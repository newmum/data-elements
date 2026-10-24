import java.net.ServerSocket;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.ssssssss.script.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/** Executes the canonical node APIs and selected-node synchronization against isolated H2/HTTP fixtures. */
public class PushNodeMagicTest {
    public static class Sort {
        public List<?> rows(List<?> rows,Map<?,?> body,Map<?,?> fields,String fallback,String id){return rows;}
    }
    static void check(boolean value,String label){PushRegistrySyncMagicTest.check(value,label);}
    static void rejected(Object value,int status){PushRegistrySyncMagicTest.rejected(value,status);}
    static void prepare(PushRegistrySyncMagicTest.Fixture f,String url){
        var dialects=new org.ssssssss.magicapi.modules.db.dialect.DialectAdapter();
        dialects.add(new org.ssssssss.magicapi.modules.db.dialect.MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
        f.db.setDialectAdapter(dialects);
        f.db.setResultProvider(new org.ssssssss.magicapi.core.interceptor.DefaultResultProvider("push-node-test"));
        f.jdbc.execute("create table if not exists push_node_t(tid varchar(32) primary key,tenant_id varchar(32),network_code varchar(64),node_name varchar(128),node_code varchar(64),base_url varchar(255),enabled int,is_default int,remark varchar(500),created_time timestamp,updated_time timestamp,is_del int,last_sync_time timestamp,last_sync_status varchar(16),last_sync_source_id varchar(64))");
        for(String id:List.of("node","foreign-node","disabled-node"))f.jdbc.update("insert into push_node_t(tid,tenant_id,network_code,node_name,node_code,base_url,enabled,is_default,is_del) values(?,?,'GAW',?,?,?, ?,0,0)",id,id.equals("foreign-node")?"ct":"ga",id,id,url,id.equals("disabled-node")?0:1);
        f.jdbc.execute("create table sym_dict_t(tid varchar(32),tenant_id varchar(32),dict_code varchar(64),parent_id varchar(32),is_del int)");
        f.jdbc.execute("insert into sym_dict_t values('network','ga','SSWL','0',0),('gaw','ga','GAW','network',0),('hlw','ga','HLW','network',0)");
    }
    static Object nodeCall(PushRegistrySyncMagicTest.Fixture f,String file,Map<String,Object> body)throws Exception {
        String content=Files.readString(Path.of("magic/api/03.数据接入/"+file));String script=content.substring(content.indexOf("================================")+32).replaceAll("(?m)^import (tenantRuntime|tableSort)\\r?\\n","");
        f.reads.set(0);return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",body,"db",f.db,"tenantRuntime",f.scope,"tableSort",new Sort())));
    }
    public static void main(String[] args)throws Exception {
        AtomicInteger calls=new AtomicInteger();AtomicInteger responseStatus=new AtomicInteger(200);AtomicInteger tables=new AtomicInteger();
        ServerSocket server=new ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"));
        Thread worker=new Thread(()->{while(!server.isClosed()){try(var socket=server.accept()){
            var input=new DataInputStream(socket.getInputStream());String request=input.readLine();Map<String,String> headers=new HashMap<>();String line;
            while((line=input.readLine())!=null&&!line.isEmpty()){int colon=line.indexOf(':');headers.put(line.substring(0,colon).toLowerCase(Locale.ROOT),line.substring(colon+1).trim());}
            String result="{\"status\":\"UP\"}";int status=200;
            if(request.contains("/api/v1/data-push")){
                calls.incrementAndGet();check("test-only-service-key".equals(headers.get("x-standalone-registry-key")),"server-side credential");
                check(headers.get("idempotency-key").startsWith("node-registry-node-source-"),"request identity");
                String body=new String(input.readNBytes(Integer.parseInt(headers.get("content-length"))),UTF_8);check(!body.contains("must-not-leak"),"source credentials excluded");
                Map<?,?> payload=new com.fasterxml.jackson.databind.ObjectMapper().readValue(body,Map.class);check("REGISTRY_SYNC".equals(payload.get("deliveryType")),"configuration-only envelope");tables.set(((List<?>)((Map<?,?>)payload.get("datasource")).get("tables")).size());
                status=responseStatus.get();result="{\"datasourceId\":\"source\"}";
            }
            byte[] bytes=result.getBytes(UTF_8);socket.getOutputStream().write(("HTTP/1.1 "+status+" Test\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes(UTF_8));socket.getOutputStream().write(bytes);
        }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}}});worker.setDaemon(true);worker.start();String url="http://127.0.0.1:"+server.getLocalPort();
        try {
            for(int count:List.of(20,100)){
                var f=new PushRegistrySyncMagicTest.Fixture(count);prepare(f,url);int before=calls.get();
                var dry=(Map<?,?>)f.call(Map.of("datasourceId","source","nodeId","node","dryRun",true));check(Boolean.TRUE.equals(dry.get("valid")),"selected-node preview");check(f.reads.get()==3,"preview fixed reads");check(calls.get()==before,"preview no HTTP");
                // The selected-node path adds one ownership/enabled lookup to
                // the ten reads used by a source synchronization without nodeId.
                var result=(Map<?,?>)f.call(Map.of("datasourceId","source","nodeId","node"));check(Boolean.TRUE.equals(result.get("synced")),"selected-node saved");check(f.reads.get()==11,"sync fixed reads "+f.reads);check(calls.get()==before+1 && tables.get()==count,"one complete HTTP request");
                check("SUCCESS".equals(f.jdbc.queryForObject("select last_sync_status from push_node_t where tid='node'",String.class)),"confirmed sync status");
                rejected(f.call(Map.of("datasourceId","source","nodeId","foreign-node")),403);rejected(f.call(Map.of("datasourceId","foreign","nodeId","node")),403);rejected(f.call(Map.of("datasourceId","source","nodeId","disabled-node")),409);
                f.scope.denied=true;rejected(f.call(Map.of("datasourceId","source","nodeId","node")),403);f.scope.denied=false;check(calls.get()==before+1,"denied no HTTP");
                f.jdbc.update("delete from push_node_t where tid!='node'");
                for(int i=1;i<count;i++)f.jdbc.update("insert into push_node_t(tid,tenant_id,node_name,node_code,network_code,base_url,enabled,is_default,is_del) values(?,'ga','test',?,'GAW',?,1,0,0)","n"+i,"n"+i,url);
                var page=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push"));check(((List<?>)page.get("list")).size()==count,"all nodes");check(f.reads.get()==2,"node list fixed reads "+f.reads);
                var health=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push","action","health","tid","node"));check(Boolean.TRUE.equals(health.get("success")),"real HTTP health");
                var checkedPage=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push"));
                check(f.reads.get()==2,"health display adds no SQL per node");
                var checked=((List<Map<String,Object>>)checkedPage.get("list")).stream().filter(n->n.get("tid").equals("node")).findFirst().orElseThrow();
                check("reachable".equals(checked.get("healthStatus")),"persisted green result");
                f.jdbc.update("update push_node_t set base_url='http://127.0.0.1:1' where tid='node'");
                var changedPage=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push"));
                var changed=((List<Map<String,Object>>)changedPage.get("list")).stream().filter(n->n.get("tid").equals("node")).findFirst().orElseThrow();
                check("unknown".equals(changed.get("healthStatus")),"new URL resets to untested");
                var down=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push","action","health","tid","node"));
                check(Boolean.FALSE.equals(down.get("success")),"unreachable test");
                var downPage=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push"));
                var downNode=((List<Map<String,Object>>)downPage.get("list")).stream().filter(n->n.get("tid").equals("node")).findFirst().orElseThrow();
                check("unreachable".equals(downNode.get("healthStatus")),"persisted red result");
                f.jdbc.update("update push_node_t set base_url=? where tid='node'",url);

                rejected(nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push","action","health","tid","foreign-node")),404);
                System.out.println(count+" items: list=2 SQL; preview=3 SQL/0 HTTP; sync=11 SQL/1 HTTP; tenant, role and disabled checks passed");
            }
            var f=new PushRegistrySyncMagicTest.Fixture(1);prepare(f,url);
            f.jdbc.update("update push_node_t set is_del=1");
            var empty=(Map<?,?>)nodeCall(f,"01.数据同步节点列表.ms",Map.of("nodeType","push"));check(((List<?>)empty.get("list")).isEmpty(),"empty node list is usable");
            f.jdbc.update("update push_node_t set is_del=0");
            Map<String,Object> form=new HashMap<>(Map.of("nodeType","push","networkCode","HLW","nodeName","Example","nodeCode","example","baseUrl",url,"enabled",false));
            var saved=(Map<?,?>)nodeCall(f,"02.保存数据同步节点.ms",form);String id=saved.get("tid").toString();check(f.jdbc.queryForObject("select is_default from push_node_t where tid=?",Integer.class,id)==1,"first network default");
            rejected(nodeCall(f,"02.保存数据同步节点.ms",form),409);form.put("tid","foreign-node");rejected(nodeCall(f,"02.保存数据同步节点.ms",form),404);
            form.remove("tid");form.put("nodeCode","bad-url");form.put("baseUrl","http://user:secret@localhost");rejected(nodeCall(f,"02.保存数据同步节点.ms",form),400);
            rejected(nodeCall(f,"03.删除数据同步节点.ms",Map.of("nodeType","push","tid",id)),400);
            rejected(nodeCall(f,"03.删除数据同步节点.ms",Map.of("nodeType","push","tid","foreign-node")),404);
            nodeCall(f,"03.删除数据同步节点.ms",Map.of("nodeType","push","tid","disabled-node"));check(f.jdbc.queryForObject("select is_del from push_node_t where tid='disabled-node'",Integer.class)==1,"scoped soft delete");
            responseStatus.set(503);rejected(f.call(Map.of("datasourceId","source","nodeId","node")),503);check("FAILED".equals(f.jdbc.queryForObject("select last_sync_status from push_node_t where tid='node'",String.class)),"failure status");
            System.out.println("Push node canonical CRUD, health and synchronization checks passed");
        } finally {server.close();}
    }
}


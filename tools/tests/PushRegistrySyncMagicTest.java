import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataservice.push.*;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;
import static org.mockito.Mockito.*;

/** Run from the repository root with the backend's test dependency classpath; no live database is changed. */
public class PushRegistrySyncMagicTest {
    public static class Scope {
        boolean denied;String hidden="";public String id(){return "ga";}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){check(resource.equals("MENU:2081000000000000002"),"scope resource");return denied?List.of():rows.stream().filter(r->!hidden.equals(r.get("tid"))).toList();}
    }
    public static class Relay {
        int calls;Map<String,Object> last;boolean fail;
        public void synchronize(String id,Map<String,Object> snapshot){calls++;if(fail)throw new IllegalStateException("test upstream failure");check(id.equals(snapshot.get("datasourceId")),"source identity");last=snapshot;}
    }
    public static class Log {public void warn(String text,Object id) {}}
    static class Fixture {
        final AtomicInteger reads=new AtomicInteger(),writes=new AtomicInteger();final JdbcTemplate jdbc;final SQLModule db;final Scope scope=new Scope();final Relay relay=new Relay();
        final DataPushRegistryProperties settings=new DataPushRegistryProperties();final ObjectMapper mapper=new ObjectMapper();final String script;
        Fixture(int count) throws Exception {
            JdbcDataSource raw=new JdbcDataSource();raw.setURL("jdbc:h2:mem:registry_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            DataSource source=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{
                Object result=invoke(method,raw,args);if(!method.getName().equals("getConnection"))return result;
                Connection connection=(Connection)result;
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
                    Object value=invoke(m,connection,a);if(!m.getName().equals("prepareStatement"))return value;String sql=(String)a[0];PreparedStatement statement=(PreparedStatement)value;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(pp,mm,aa)->{if(mm.getName().equals("executeQuery")&&sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT"))reads.incrementAndGet();if(mm.getName().equals("executeUpdate"))writes.incrementAndGet();return invoke(mm,statement,aa);});
                });
            });
            jdbc=new JdbcTemplate(source);
            jdbc.execute("create table push_node_t(tid varchar(64),tenant_id varchar(32),base_url varchar(255),enabled int,is_del int,last_sync_time timestamp,last_sync_status varchar(32),last_sync_source_id varchar(64),network_code varchar(64),node_name varchar(128),node_code varchar(64),is_default int,remark varchar(500),created_time timestamp,updated_time timestamp)");
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_name varchar(255),asset_status int,pool_cfg varchar(2000),is_del int,org_id varchar(64),created_by varchar(64),app_id varchar(64))");
            jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(32),app_name varchar(255),is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),table_name varchar(255),table_name_cn varchar(255),table_comment varchar(255),asset_status int,is_del int,field_governance_config clob)");
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(32),table_id varchar(64),column_name varchar(255),column_comment varchar(255),data_type varchar(64),column_type varchar(64),nullable int,primary_key int,ordinal_position int,is_del int)");
            jdbc.execute("create table data_push_schema_t(tid varchar(64),datasource_id varchar(64),table_id varchar(64),table_name varchar(255),schema_version bigint,schema_hash varchar(128),status varchar(32),delivery_config varchar(2000),tenant_id varchar(32),published_time timestamp,updated_time timestamp,is_del int)");
            jdbc.execute("create table data_push_schema_field_t(tid varchar(64),schema_id varchar(64),field_name varchar(255),data_type varchar(64),required_flag int,ordinal_position int,tenant_id varchar(32),is_del int)");
            jdbc.update("insert into db_datasource_t values('source','ga','DEMO',1,?,0,'org','user','app')","{\"accessMode\":\"receive\",\"password\":\"must-not-leak\"}");
            jdbc.update("insert into db_datasource_t values('foreign','ct','OTHER',2,'{\"accessMode\":\"receive\"}',0,'org','other','app')");
            jdbc.update("insert into sym_application_t values('app','ga','DEMO SYSTEM',0)");
            for(int i=0;i<count;i++){String id="table_"+i;jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values(?,'ga','source',?,'示例表','',2,0)",id,id);jdbc.update("insert into db_table_column_t values(?,'ga',?,'id','标识','int','int',0,1,1,0)","column_"+i,id);}
            var dynamic=new MagicDynamicDataSource();dynamic.setDefault(source);db=new SQLModule(dynamic);db.setDataSourceNode(dynamic.getDataSource());
            var dialects=new org.ssssssss.magicapi.modules.db.dialect.DialectAdapter();dialects.add(new org.ssssssss.magicapi.modules.db.dialect.MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);db.setResultProvider(new org.ssssssss.magicapi.core.interceptor.DefaultResultProvider("registry-test"));
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);db.setSqlInterceptors(List.of());db.setNamedTableInterceptors(List.of());
            TenantDataSourceRegistry tenants=mock(TenantDataSourceRegistry.class);when(tenants.dataSourceForTenant("ga")).thenReturn(source);
            var schemas=new DataPushSchemaService(tenants,mapper,mock(DataPushFjghccDeliveryService.class));settings.setRelayBaseUrl("http://127.0.0.1:18083");settings.setStandaloneSyncKey("test-only-service-key");
            var beans=new DefaultListableBeanFactory();beans.registerSingleton("mapper",mapper);beans.registerSingleton("tenantDataSourceRegistry",tenants);beans.registerSingleton("data-element.push-registry-com.linewell.dataelement.dataservice.push.DataPushRegistryProperties",settings);beans.registerSingleton("dataPushSchemaService",schemas);beans.registerSingleton("haitongStandaloneRegistryClient",relay);new SpringUtil().postProcessBeanFactory(beans);
            String file=Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/08.同步推送登记配置.ms"));script=file.substring(file.indexOf("================================")+32).replaceAll("(?m)^import (tenantRuntime|dataScope)\\r?\\n","");MagicScript.create(script,null).compile();reads.set(0);
        }
        Object call(Map<String,Object> body){reads.set(0);writes.set(0);return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",body,"db",db,"tenantRuntime",scope,"dataScope",scope,"log",new Log())));}
    }
    static class BatchUpstream implements AutoCloseable {
        final java.net.ServerSocket server;final Thread worker;volatile int calls;boolean corruptReceipt;Map<String,Object> last;
        BatchUpstream() throws Exception {
            server=new java.net.ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"));
            worker=Thread.ofPlatform().daemon().start(()->{
                while(!server.isClosed())try(var socket=server.accept()) {
                    var input=socket.getInputStream();var head=new java.io.ByteArrayOutputStream();int matched=0;
                    byte[] end={13,10,13,10};
                    while(matched<4){int ch=input.read();if(ch<0)break;head.write(ch);matched=ch==end[matched]?matched+1:0;}
                    String headers=head.toString(java.nio.charset.StandardCharsets.US_ASCII);int length=0;
                    for(String line:headers.split("\r\n"))if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                    var json=new ObjectMapper();last=json.readValue(input.readNBytes(length),Map.class);calls++;
                    var sources=(List<Map<String,Object>>)last.get("datasources");int tables=0,columns=0;
                    for(var source:sources)for(var table:(List<Map<String,Object>>)source.get("tables")){tables++;columns+=((List<?>)table.get("columns")).size();}
                    byte[] reply=json.writeValueAsBytes(Map.of("datasourceIds",corruptReceipt?Collections.nCopies(sources.size(),sources.getFirst().get("datasourceId")):sources.stream().map(r->r.get("datasourceId")).toList(),"tableCount",tables,"columnCount",columns));
                    var output=socket.getOutputStream();output.write(("HTTP/1.1 201 Created\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(reply);output.flush();
                }catch(Exception e){if(!server.isClosed())throw new RuntimeException(e);}
            });
        }
        String url(){return "http://127.0.0.1:"+server.getLocalPort();}
        public void close() throws Exception {server.close();worker.join(1000);}
    }
    static Object invoke(Method method,Object target,Object[] args) throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static void rejected(Object result,int status){check(result instanceof ExitValue,"expected rejection");check(((ExitValue)result).getValues()[0].toString().equals(Integer.toString(status)),"rejection status "+status);}
    public static void main(String[] args) throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);Map<?,?> dry=(Map<?,?>)f.call(Map.of("datasourceId","source","dryRun",true));check(dry.get("tableCount").equals(count),"dry count");check(f.reads.get()==2,"dry fixed reads");check(f.relay.calls==0,"preview transport");check(f.jdbc.queryForObject("select count(*) from data_push_schema_t",Integer.class)==0,"preview writes");
            Map<?,?> result=(Map<?,?>)f.call(Map.of("datasourceId","source"));check(Boolean.TRUE.equals(result.get("synced")),"synced");check(f.reads.get()==10,"fixed source+scope snapshot reads: "+f.reads);check(f.relay.calls==1,"one upstream request");check(((List<?>)f.relay.last.get("tables")).size()==count,"all tables");check(f.relay.last.get("sourceSystemName").equals("DEMO SYSTEM"),"system association");check(!f.mapper.writeValueAsString(f.relay.last).contains("must-not-leak"),"credentials excluded");
            f.scope.denied=true;rejected(f.call(Map.of("datasourceId","source")),403);check(f.relay.calls==1,"denied no transport");f.scope.denied=false;
            rejected(f.call(Map.of("datasourceId","foreign")),403);rejected(f.call(Map.of("datasourceId","source","tenantId","ct")),400);
            f.jdbc.update("update db_datasource_t set pool_cfg='{\"accessMode\":\"upload\"}' where tid='source'");rejected(f.call(Map.of("datasourceId","source")),422);
            System.out.println(count+" tables: preview=2 reads, sync=10 reads, upstream=1; role/tenant/injected metadata rejected");
        }
        try (var upstream = new BatchUpstream()) {
            for(int count:List.of(20,100)) {
                var f=new Fixture(0);
                f.jdbc.update("delete from db_datasource_t where tid='source'");
                for(int i=0;i<count;i++) {
                    String id="s"+i,table="t"+i;
                    f.jdbc.update("insert into db_datasource_t values(?,'ga',?,2,'{\"accessMode\":\"receive\"}',0,'org','user','app')",id,id);
                    f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_name_cn,table_comment,asset_status,is_del) values(?,'ga',?,?,'示例表','',2,0)",table,id,table);
                    f.jdbc.update("insert into db_table_column_t values(?,'ga',?,'id','标识','int','int',0,1,1,0)","c"+i,table);
                }
                f.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('node','ga',?,1,0)",upstream.url());
                f.jdbc.update("insert into push_node_t(tid,tenant_id,base_url,enabled,is_del) values('foreign-node','ct',?,1,0)",upstream.url());
                f.jdbc.update("update db_table_t set field_governance_config=? where tid='t0'",f.mapper.writeValueAsString(Map.of("attachmentFields",List.of(Map.of("columnName","id","maxFileBytes",20971520,"allowedExtensions",List.of("pdf"))))));
                Map<?,?> dry=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","dryRun",true));
                check(dry.get("datasourceCount").equals(count),"bulk preview count: "+dry);
                int previewReads=f.reads.get();
                int before=upstream.calls;
                Map<?,?> result=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node"));
                check(Boolean.TRUE.equals(result.get("synced")),"bulk synced "+result);
                check(f.reads.get()==previewReads+1,"bulk field identity prefetch adds exactly one read: "+f.reads+" vs "+previewReads);
                check(upstream.calls==before+1,"single batch transport");check(f.writes.get()==4,"bulk writes use four statements: "+f.writes);
                check(f.jdbc.queryForObject("select count(*) from data_push_schema_t",Integer.class)==count,"published all schemas");
                String hash=f.jdbc.queryForObject("select schema_hash from data_push_schema_t where datasource_id='s0'",String.class);
                f.call(Map.of("datasourceId","s0"));
                check(hash.equals(f.jdbc.queryForObject("select schema_hash from data_push_schema_t where datasource_id='s0'",String.class)),"batch and single publish canonical hash must agree");
                check(f.jdbc.queryForObject("select schema_version from data_push_schema_t where datasource_id='s0'",Long.class)==1L,"unchanged canonical version");
                check(f.jdbc.queryForObject("select delivery_config from data_push_schema_t where datasource_id='s0'",String.class).contains("20971520"),"attachment limits survive bulk and single synchronization");
                f.scope.hidden="s0";
                Map<?,?> scoped=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","dryRun",true));
                check(scoped.get("datasourceCount").equals(count-1),"per source role scope");
                check(((List<?>)upstream.last.get("datasources")).size()==count,"batch contains all authorized sources");
                upstream.corruptReceipt=true;rejected(f.call(Map.of("all",true,"nodeId","node")),503);upstream.corruptReceipt=false;
                check("FAILED".equals(f.jdbc.queryForObject("select last_sync_status from push_node_t where tid='node'",String.class)),"incomplete receipt fails whole result");
                rejected(f.call(Map.of("all",true,"nodeId","foreign-node")),403);
                rejected(f.call(Map.of("all",true,"nodeId","node","tenantId","ct")),400);
                f.jdbc.update("update db_datasource_t set asset_status=0 where tid='s1'");
                Map<?,?> skipped=(Map<?,?>)f.call(Map.of("all",true,"nodeId","node","dryRun",true));
                check(((List<?>)skipped.get("skipped")).size()==1,"incomplete source explained");
                System.out.println(count+" sources: bulk preview="+previewReads+", sync="+(previewReads+1)+" reads, upstream=1; per-source role and foreign node isolation verified");
            }
        }
        var tooMany=new Fixture(101);rejected(tooMany.call(Map.of("datasourceId","source")),413);check(tooMany.relay.calls==0,"oversize transport");
        var emptyFields=new Fixture(1);emptyFields.jdbc.update("delete from db_table_column_t");rejected(emptyFields.call(Map.of("datasourceId","source")),409);
        var unavailable=new Fixture(1);rejected(unavailable.call(Map.of("datasourceId","source","dryRun","true")),400);
        unavailable.settings.setStandaloneSyncKey("");rejected(unavailable.call(Map.of("datasourceId","source")),503);check(unavailable.relay.calls==0,"missing setting transport");
        unavailable.settings.setStandaloneSyncKey("test-only-service-key");unavailable.relay.fail=true;rejected(unavailable.call(Map.of("datasourceId","source")),503);
        System.out.println("Push registry canonical Magic contract checks passed");
    }
}

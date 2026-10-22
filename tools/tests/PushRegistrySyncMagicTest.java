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
        boolean denied;public String id(){return "ga";}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){check(resource.equals("MENU:2081000000000000002"),"scope resource");return denied?List.of():rows;}
    }
    public static class Relay {
        int calls;Map<String,Object> last;boolean fail;
        public void synchronize(String id,Map<String,Object> snapshot){calls++;if(fail)throw new IllegalStateException("test upstream failure");check(id.equals(snapshot.get("datasourceId")),"source identity");last=snapshot;}
    }
    public static class Log {public void warn(String text,Object id) {}}
    static class Fixture {
        final AtomicInteger reads=new AtomicInteger();final JdbcTemplate jdbc;final SQLModule db;final Scope scope=new Scope();final Relay relay=new Relay();
        final DataPushRegistryProperties settings=new DataPushRegistryProperties();final ObjectMapper mapper=new ObjectMapper();final String script;
        Fixture(int count) throws Exception {
            JdbcDataSource raw=new JdbcDataSource();raw.setURL("jdbc:h2:mem:registry_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            DataSource source=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{
                Object result=invoke(method,raw,args);if(!method.getName().equals("getConnection"))return result;
                Connection connection=(Connection)result;
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
                    Object value=invoke(m,connection,a);if(!m.getName().equals("prepareStatement"))return value;String sql=(String)a[0];PreparedStatement statement=(PreparedStatement)value;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(pp,mm,aa)->{if(mm.getName().equals("executeQuery")&&sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT"))reads.incrementAndGet();return invoke(mm,statement,aa);});
                });
            });
            jdbc=new JdbcTemplate(source);
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_name varchar(255),asset_status int,pool_cfg varchar(2000),is_del int,org_id varchar(64),created_by varchar(64),app_id varchar(64))");
            jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(32),app_name varchar(255),is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),table_name varchar(255),table_name_cn varchar(255),table_comment varchar(255),asset_status int,is_del int)");
            jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(32),table_id varchar(64),column_name varchar(255),column_comment varchar(255),data_type varchar(64),column_type varchar(64),nullable int,primary_key int,ordinal_position int,is_del int)");
            jdbc.execute("create table data_push_schema_t(tid varchar(64),datasource_id varchar(64),table_id varchar(64),table_name varchar(255),schema_version bigint,schema_hash varchar(128),status varchar(32),delivery_config varchar(2000),tenant_id varchar(32),published_time timestamp,updated_time timestamp,is_del int)");
            jdbc.execute("create table data_push_schema_field_t(tid varchar(64),schema_id varchar(64),field_name varchar(255),data_type varchar(64),required_flag int,ordinal_position int,tenant_id varchar(32),is_del int)");
            jdbc.update("insert into db_datasource_t values('source','ga','DEMO',1,?,0,'org','user','app')","{\"accessMode\":\"receive\",\"password\":\"must-not-leak\"}");
            jdbc.update("insert into db_datasource_t values('foreign','ct','OTHER',2,'{\"accessMode\":\"receive\"}',0,'org','other','app')");
            jdbc.update("insert into sym_application_t values('app','ga','DEMO SYSTEM',0)");
            for(int i=0;i<count;i++){String id="table_"+i;jdbc.update("insert into db_table_t values(?,'ga','source',?,'示例表','',2,0)",id,id);jdbc.update("insert into db_table_column_t values(?,'ga',?,'id','标识','int','int',0,1,1,0)","column_"+i,id);}
            var dynamic=new MagicDynamicDataSource();dynamic.setDefault(source);db=new SQLModule(dynamic);db.setDataSourceNode(dynamic.getDataSource());
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);db.setSqlInterceptors(List.of());db.setNamedTableInterceptors(List.of());
            TenantDataSourceRegistry tenants=mock(TenantDataSourceRegistry.class);when(tenants.dataSourceForTenant("ga")).thenReturn(source);
            var schemas=new DataPushSchemaService(tenants,mapper,mock(DataPushFjghccDeliveryService.class));settings.setRelayBaseUrl("http://127.0.0.1:18083");settings.setStandaloneSyncKey("test-only-service-key");
            var beans=new DefaultListableBeanFactory();beans.registerSingleton("mapper",mapper);beans.registerSingleton("data-element.push-registry-com.linewell.dataelement.dataservice.push.DataPushRegistryProperties",settings);beans.registerSingleton("dataPushSchemaService",schemas);beans.registerSingleton("haitongStandaloneRegistryClient",relay);new SpringUtil().postProcessBeanFactory(beans);
            String file=Files.readString(Path.of("magic/api/02.数据盘点/02.数据库表/08.同步推送登记配置.ms"));script=file.substring(file.indexOf("================================")+32).replaceAll("(?m)^import (tenantRuntime|dataScope)\\r?\\n","");MagicScript.create(script,null).compile();reads.set(0);
        }
        Object call(Map<String,Object> body){reads.set(0);return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",body,"db",db,"tenantRuntime",scope,"dataScope",scope,"log",new Log())));}
    }
    static Object invoke(Method method,Object target,Object[] args) throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static void rejected(Object result,int status){check(result instanceof ExitValue,"expected rejection");check(((ExitValue)result).getValues()[0].toString().equals(Integer.toString(status)),"rejection status "+status);}
    public static void main(String[] args) throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);Map<?,?> dry=(Map<?,?>)f.call(Map.of("datasourceId","source","dryRun",true));check(dry.get("tableCount").equals(count),"dry count");check(f.reads.get()==2,"dry fixed reads");check(f.relay.calls==0,"preview transport");check(f.jdbc.queryForObject("select count(*) from data_push_schema_t",Integer.class)==0,"preview writes");
            Map<?,?> result=(Map<?,?>)f.call(Map.of("datasourceId","source"));check(Boolean.TRUE.equals(result.get("synced")),"synced");check(f.reads.get()==9,"fixed source+scope snapshot reads: "+f.reads);check(f.relay.calls==1,"one upstream request");check(((List<?>)f.relay.last.get("tables")).size()==count,"all tables");check(f.relay.last.get("sourceSystemName").equals("DEMO SYSTEM"),"system association");check(!f.mapper.writeValueAsString(f.relay.last).contains("must-not-leak"),"credentials excluded");
            f.scope.denied=true;rejected(f.call(Map.of("datasourceId","source")),403);check(f.relay.calls==1,"denied no transport");f.scope.denied=false;
            rejected(f.call(Map.of("datasourceId","foreign")),403);rejected(f.call(Map.of("datasourceId","source","tenantId","ct")),400);
            f.jdbc.update("update db_datasource_t set pool_cfg='{\"accessMode\":\"upload\"}' where tid='source'");rejected(f.call(Map.of("datasourceId","source")),422);
            System.out.println(count+" tables: preview=2 reads, sync=9 reads, upstream=1; role/tenant/injected metadata rejected");
        }
        var tooMany=new Fixture(101);rejected(tooMany.call(Map.of("datasourceId","source")),413);check(tooMany.relay.calls==0,"oversize transport");
        var emptyFields=new Fixture(1);emptyFields.jdbc.update("delete from db_table_column_t");rejected(emptyFields.call(Map.of("datasourceId","source")),409);
        var unavailable=new Fixture(1);rejected(unavailable.call(Map.of("datasourceId","source","dryRun","true")),400);
        unavailable.settings.setStandaloneSyncKey("");rejected(unavailable.call(Map.of("datasourceId","source")),503);check(unavailable.relay.calls==0,"missing setting transport");
        unavailable.settings.setStandaloneSyncKey("test-only-service-key");unavailable.relay.fail=true;rejected(unavailable.call(Map.of("datasourceId","source")),503);
        System.out.println("Push registry canonical Magic contract checks passed");
    }
}

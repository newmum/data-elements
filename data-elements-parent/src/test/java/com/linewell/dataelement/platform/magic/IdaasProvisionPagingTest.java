package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Execute the actual list route, including SQL aggregation, pagination and app-scope decoys. */
public class IdaasProvisionPagingTest {
    public static String dateFormat(Timestamp value,String pattern) { return value==null?null:value.toLocalDateTime().toString(); }
    public static class Runtime {public Object control(MagicScriptLambdaFunction callback){return callback.apply(null,new Object[0]);}}
    public static class Request {
        final Map<String,String> query;
        Request(Map<String,String> query){this.query=query;}
        public List<String> getValues(String key){return query.containsKey(key)?List.of(query.get(key)):null;}
    }
    private static class Counter implements SQLInterceptor {int count;public void preHandle(BoundSql sql,RequestEntity request){count++;}}
    @Test void twentyAndHundredTasksHaveTwoSqlReadsAndGlobalStatisticsOrder() throws Exception {
        var fixture=new Fixture();
        var small=fixture.read(Map.of("paged","true","page","1","size","20","sortField","successCount","sortDir","desc"));assertEquals(2,fixture.counter.count);
        fixture.counter.count=0;var large=fixture.read(Map.of("paged","true","page","1","size","100","sortField","successCount","sortDir","desc"));assertEquals(2,fixture.counter.count);
        var rows=(List<Map<String,Object>>)large.get("list");assertEquals(100,rows.size());assertEquals(120,((Number)large.get("total")).intValue());
        assertEquals(((List<?>)small.get("list")),rows.subList(0,20));
        assertTrue(rows.stream().allMatch(r->"app-a".equals(r.get("appId")) && ((List<?>)r.get("items")).isEmpty()));
        for(int i=1;i<rows.size();i++)assertTrue(((Number)rows.get(i-1).get("successCount")).intValue()>=((Number)rows.get(i).get("successCount")).intValue());
        fixture.counter.count=0;var denied=fixture.read(Map.of("paged","true","appId","app-b","size","100"));
        assertEquals(0,((Number)denied.get("total")).intValue());assertEquals(List.of(),denied.get("list"));assertEquals(2,fixture.counter.count);
    }
    @Test void itemPageIsScopedAndUsesThreeReadsForEitherPageSize() throws Exception {
        var fixture=new Fixture();
        for(String size:List.of("20","100")) {
            fixture.counter.count=0;var page=fixture.read(Map.of("kind","items","id","task-000","page","1","size",size,"sortField","attempt","sortDir","desc"));
            assertEquals(3,fixture.counter.count);assertEquals(1,((Number)page.get("total")).intValue());
            var row=(Map<?,?>)((List<?>)page.get("list")).getFirst();assertEquals("Visible person",row.get("name"));
        }
        assertInstanceOf(org.ssssssss.script.runtime.ExitValue.class,fixture.readRaw(Map.of("kind","items","id","hidden-task","size","100")));
    }
    private static class Fixture {
        final Counter counter=new Counter(); final SQLModule sql;
        Fixture() {
            var source=new DriverManagerDataSource("jdbc:h2:mem:provision_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources=new MagicDynamicDataSource();sources.setDefault(source);sql=new SQLModule(sources);sql.setDataSourceNode(sources.getDataSource());
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("default");
            sql.setColumnMapperProvider(columns);sql.setSqlInterceptors(List.of(counter));sql.setNamedTableInterceptors(List.of());sql.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());sql.setRowMapColumnMapper(v->v);
            var db=new JdbcTemplate(source);
            db.execute("CREATE ALIAS DATE_FORMAT FOR 'com.linewell.dataelement.platform.magic.IdaasProvisionPagingTest.dateFormat'");
            db.execute("CREATE TABLE iam_sync_config_t(tid VARCHAR(64),app_id VARCHAR(64),display_name VARCHAR(100))");
            db.execute("CREATE TABLE iam_sync_task_t(tid VARCHAR(64),app_id VARCHAR(64),config_id VARCHAR(64),version INT,status VARCHAR(30),created_time TIMESTAMP)");
            db.execute("CREATE TABLE iam_sync_item_t(tid VARCHAR(64),task_id VARCHAR(64),object_id VARCHAR(64),object_type VARCHAR(30),source_version INT,attempt_count INT,status VARCHAR(30),sequence_no INT,result_json VARCHAR(1000))");
            db.execute("CREATE TABLE iam_subject_t(tid VARCHAR(64),name VARCHAR(100),identity_domain VARCHAR(30),is_del INT)");
            db.execute("CREATE TABLE iam_org_t(tid VARCHAR(64),name VARCHAR(100),identity_domain VARCHAR(30),is_del INT)");
            db.execute("CREATE TABLE iam_role_t(tid VARCHAR(64),name VARCHAR(100),identity_domain VARCHAR(30),app_id VARCHAR(64),is_del INT)");
            db.execute("CREATE TABLE iam_resource_t(tid VARCHAR(64),name VARCHAR(100),identity_domain VARCHAR(30),app_id VARCHAR(64),is_del INT)");
            db.update("INSERT INTO iam_sync_config_t VALUES('config-a','app-a','Visible configuration'),('config-b','app-b','Outside configuration')");
            db.update("INSERT INTO iam_subject_t VALUES('person','Visible person','workforce',0)");
            for(int i=0;i<120;i++) {
                String id="task-"+String.format("%03d",i);db.update("INSERT INTO iam_sync_task_t VALUES(?,'app-a','config-a',1,'SUCCEEDED',CURRENT_TIMESTAMP)",id);
                for(int j=0;j<=i%5;j++)db.update("INSERT INTO iam_sync_item_t VALUES(?,?,'person','SUBJECT',1,?,'APPLIED',?,NULL)",id+"-"+j,id,j,j);
            }
            db.update("INSERT INTO iam_sync_task_t VALUES('hidden-task','app-b','config-b',1,'PENDING',CURRENT_TIMESTAMP)");
            db.update("INSERT INTO iam_sync_item_t VALUES('hidden-item','hidden-task','person','SUBJECT',1,99,'FAILED',0,NULL)");
        }
        Map<String,Object> read(Map<String,String> query) throws Exception {
            return (Map<String,Object>)assertInstanceOf(Map.class,readRaw(query));
        }
        Object readRaw(Map<String,String> query) throws Exception {
            String source=CanonicalMagicSources.byId("d3584c2bbce4543ca25ffbc5654a8c46").replaceAll("(?m)^import [^\\r\\n]+;\\r?\\n","");
            String helpers="var apiCall=(callback)=>callback();var listContext=(permission,appId,domain)=>({pairs:[{codes:['sync:read'],scopes:[{identity_domain:'workforce',scope_kind:'APPLICATION',app_id:'app-a'},{identity_domain:'public',scope_kind:'ALL'}]}]});\n";
            var value=MagicScript.create(helpers+source,null).execute(new MagicScriptContext(Map.of("db",sql,"identityRuntime",new Runtime(),"request",new Request(query),"tableSort",new TableSortMagicModule())));
            return value;
        }
    }
}

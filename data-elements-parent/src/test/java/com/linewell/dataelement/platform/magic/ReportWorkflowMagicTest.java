package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import cn.hutool.json.JSONUtil;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.runtime.ExitValue;

class ReportWorkflowMagicTest {
    private static Map<String,Object> table(String key,String id,String name){
        var row=new LinkedHashMap<String,Object>();row.put("key",key);row.put("tid",id);row.put("fileId","file");row.put("fileName","validation.csv");
        row.put("tableName",name);row.put("tableNameCn","上报验证表");row.put("recordCount",2);row.put("businessType","日志表");row.put("status","success");
        row.put("sheetName","validation");row.put("startRow",1);row.put("endRow",3);row.put("startColumn",1);row.put("endColumn",1);
        row.put("fields",List.of(new LinkedHashMap<>(Map.of("columnName","occurred_at","columnComment","发生时间","columnType","varchar","columnLength",64,"primaryKey",false,"nullable",true,"isTimestamp",true,"standardField","DATETIME"))));
        return row;
    }
    private static Map<String,Object> body(List<Map<String,Object>> tables,List<String> keys){
        return new LinkedHashMap<>(Map.of("action","save","dbId","source","workflowVersion",2,"revision","","files",List.of(Map.of("fileId","file","fileName","validation.csv")),"tables",tables,"normalizedTableKeys",keys));
    }
    private static ReportRegistrationMagicTest.Fixture fixture(int count){
        var f=new ReportRegistrationMagicTest.Fixture(1);f.base.jdbc.execute("insert into file_upload_t values('file','police','validation-user',0)");
        for(int i=0;i<count;i++)f.base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'police','source',?,0)","registered-"+i,"registered_"+i);
        return f;
    }
    @Test void explicitApprovalUsesFourReadsForTwentyAndHundredTablesAndPreservesRegistrationContracts() throws Exception {
        for(int count:List.of(20,100)){
            var f=fixture(count);var rows=new ArrayList<Map<String,Object>>();var keys=new ArrayList<String>();
            for(int i=0;i<count;i++){rows.add(table("key-"+i,"registered-"+i,"registered_"+i));keys.add("key-"+i);}
            var saved=assertInstanceOf(Map.class,f.run(body(rows,keys),true));
            assertTrue(((List<?>)saved.get("tables")).stream().allMatch(row->Boolean.TRUE.equals(((Map<?,?>)row).get("normalized"))));
            assertEquals(4,f.base.reads);
            var imported=assertInstanceOf(Map.class,f.captured.get("payload"));
            assertEquals("日志表",((Map<?,?>)((List<?>)imported.get("tables")).getFirst()).get("businessType"));
            assertTrue(imported.get("fieldMap").toString().contains("DATETIME"));
            System.out.println("Report workflow approval: tables="+count+", SQL=4; normalization uses registered snapshot contract");
        }
    }
    @Test void clientCannotForgeApprovalAndSchemaEditsRevokeIt() throws Exception {
        var f=fixture(0);var row=table("key","table","registered_table");row.put("normalized",true);row.put("normalizationVersion",1);
        var request=body(new ArrayList<>(List.of(row)),List.of());
        var draft=(Map<?,?>)f.run(request);assertEquals(false,((Map<?,?>)((List<?>)draft.get("tables")).getFirst()).get("normalized"));
        request.put("revision",draft.get("revision"));request.put("normalizedTableKeys",List.of("key"));
        var ready=(Map<?,?>)f.run(request,true);assertEquals(true,((Map<?,?>)((List<?>)ready.get("tables")).getFirst()).get("normalized"));
        request.put("revision",ready.get("revision"));request.put("normalizedTableKeys",List.of());row.put("tableNameCn","修改后的中文名");
        var edited=(Map<?,?>)f.run(request);assertEquals(false,((Map<?,?>)((List<?>)edited.get("tables")).getFirst()).get("normalized"));
    }
    @Test void partialApprovalDoesNotRequireUnrelatedDraftsToPassAndRejectsForeignKeys() throws Exception {
        var f=fixture(0);var good=table("good","table","registered_table");var invalid=table("draft","","bad name");invalid.put("businessType","");
        var saved=(Map<?,?>)f.run(body(new ArrayList<>(List.of(good,invalid)),List.of("good")),true);
        var rows=(List<?>)saved.get("tables");assertEquals(true,((Map<?,?>)rows.get(0)).get("normalized"));assertEquals(false,((Map<?,?>)rows.get(1)).get("normalized"));
        var other=fixture(0);assertInstanceOf(ExitValue.class,other.run(body(List.of(good),List.of("foreign-key"))));
        good.put("tid","foreign");assertInstanceOf(ExitValue.class,other.run(body(List.of(good),List.of("good"))));
    }
    @Test void unreviewedSavedTablesCannotReachDelivery() throws Exception {
        var f=new ReportSubmitMagicTest.Fixture();
        var config=JSONUtil.parseObj(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class));
        config.getJSONObject("reportRegistration").getJSONArray("tables").getJSONObject(0).set("normalized",false);
        f.base.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",config.toString());
        assertInstanceOf(ExitValue.class,f.run("source","table","validation-request"));assertEquals(0,f.delivery.calls);
    }
    @Test void historicSuccessfulTablesRemainReadOnlyAcrossDraftSavesAndKeepSuccessVisible() throws Exception {
        var f=fixture(0);var row=table("historic","table","registered_table");
        var original=Map.of("revision","v1","files",List.of(Map.of("fileId","file","fileName","validation.csv")),"tables",List.of(row),"receipts",List.of(Map.of("tableId","table","fileId","file","status","success"),Map.of("tableId","table","fileId","file","status","failed")));
        f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",original)));
        var request=body(List.of(row),List.of());request.put("revision","v1");
        var saved=(Map<?,?>)f.run(request);assertEquals("success",((Map<?,?>)((Collection<?>)saved.get("receipts")).iterator().next()).get("status"));
        request.put("revision",saved.get("revision"));var second=(Map<?,?>)f.run(request);
        request.put("revision",second.get("revision"));row.put("tableNameCn","变更历史结构");
        assertInstanceOf(ExitValue.class,f.run(request));
    }
    @Test void invalidRegionsAndDictionaryDefinitionsCannotBeApproved() throws Exception {
        var f=fixture(0);var row=table("invalid","table","registered_table");
        row.remove("startRow");assertInstanceOf(ExitValue.class,f.run(body(List.of(row),List.of("invalid")),true));
        row.put("startRow",1);row.put("businessType","字典表");assertInstanceOf(ExitValue.class,f.run(body(List.of(row),List.of("invalid")),true));
        assertNull(f.captured.get("payload"));
    }
}

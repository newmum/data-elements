package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.runtime.ExitValue;

class ReportRegistrationPageMagicTest {
    @Test void pagesIncludeApprovedAndPendingTablesSortBeforePagingAndCheckEveryId() throws Exception {
        for (int count : List.of(20,100)) {
            var f=new ReportRegistrationMagicTest.Fixture(1);
            var tables=new ArrayList<Map<String,Object>>();var files=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++){
                String key="key-"+String.format("%03d",i),id="snapshot-"+i,file="file-"+i;
                f.base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'police','source',?,0)",id,key);
                f.base.jdbc.update("insert into file_upload_t values(?,'police','validation-user',0)",file);
                files.add(Map.of("fileId",file,"fileName",file+".csv"));
                tables.add(new LinkedHashMap<>(Map.of("key",key,"tid",id,"fileId",file,"fileName",file+".csv","tableName",key,"tableNameCn","登记表","fields",List.of(Map.of("columnName","id")),"normalized",i%2==0,"updatedAt",java.time.Instant.parse("2026-11-01T00:00:00Z").plusMillis(i).toString())));
            }
            var manifest=new LinkedHashMap<String,Object>(Map.of("revision","page-v1","files",files,"tables",tables));
            f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",manifest)));
            for(int size:List.of(20,100)){
                var result=(Map<?,?>)f.run(Map.of("action","table-page","dbId","source","revision","page-v1","pageNo",1,"pageSize",size));
                var rows=(List<?>)result.get("list");assertEquals(Math.min(size,count),rows.size());assertEquals(count,((Number)result.get("total")).intValue());
                assertEquals("key-"+String.format("%03d",count-1),((Map<?,?>)rows.getFirst()).get("key"));assertEquals(3,f.base.reads);
            }
            var last=(Map<?,?>)f.run(Map.of("action","table-page","dbId","source","revision","page-v1","pageNo",count/20,"pageSize",20));
            assertEquals("key-000",((Map<?,?>)((List<?>)last.get("list")).getLast()).get("key"));
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","table-page","dbId","source","revision","page-v1","sortField","updated_time desc;drop table")));
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","table-page","dbId","source","revision","stale")));
            tables.getLast().put("tid","foreign");
            f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",manifest)));
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","table-page","dbId","source","revision","page-v1","tenantId","other")));
            tables.getLast().put("tid","snapshot-"+(count-1));
            f.base.jdbc.update("update file_upload_t set tenant_id='other' where tid=?","file-"+(count-1));
            f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",manifest)));
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","table-page","dbId","source","revision","page-v1")));
            f.base.scope.denied=true;
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","table-page","dbId","source","revision","page-v1")));
            assertEquals(1,f.base.reads,"role denial precedes all batch metadata reads");
            System.out.println("Registration table page: tables="+count+", pageSize=20/100, SQL=3; foreign table/file rejected");
        }
    }
    @Test void filesKeepAllChildrenAndOnlyApprovedTablesEnterDeliveryWithFixedBatchReads() throws Exception {
        for(int count:List.of(20,100)){
            var f=new ReportRegistrationMagicTest.Fixture(1);
            var tables=new ArrayList<Map<String,Object>>();var files=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++){
                String key="key-"+String.format("%03d",i),id="snapshot-"+i,file="file-"+(i/2);
                f.base.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'police','source',?,0)",id,key);
                if(i%2==0){f.base.jdbc.update("insert into file_upload_t values(?,'police','validation-user',0)",file);files.add(Map.of("fileId",file,"fileName",file+".xlsx"));}
                tables.add(new LinkedHashMap<>(Map.of("key",key,"tid",id,"fileId",file,"fileName",file+".xlsx","tableName",key,"tableNameCn","业务数据表"+i,"fields",List.of(Map.of("columnName","id")),"normalized",i%2==0,"normalizationVersion",1)));
                tables.getLast().put("updatedAt",java.time.Instant.parse("2026-11-01T00:00:00Z").plusMillis(i).toString());
            }
            String emptyFile="file-empty";f.base.jdbc.update("insert into file_upload_t values(?,'police','validation-user',0)",emptyFile);files.add(Map.of("fileId",emptyFile,"fileName","validation_header.csv"));
            var failures=List.of(Map.of("key","parse-failed","fileId",emptyFile,"fileName","validation_header.csv","error","未识别到表头"));
            var receipts=List.of(Map.of("tableId","snapshot-"+(count-1),"fileId","file-"+((count-1)/2),"status","success"));
            var manifest=new LinkedHashMap<String,Object>(Map.of("revision","tree-v1","files",files,"tables",tables,"failedTables",failures,"receipts",receipts));
            java.util.function.Consumer<Void> store=ignored->f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",manifest)));
            store.accept(null);
            var page=(Map<?,?>)f.run(Map.of("action","file-page","dbId","source","revision","tree-v1","pageSize",100));
            assertEquals(count/2+1,((Number)page.get("total")).intValue());assertEquals(3,f.base.reads);
            var groups=(List<?>)page.get("list");var first=(Map<?,?>)groups.getFirst();
            assertEquals(2,((List<?>)first.get("tableKeys")).size());assertEquals("key-"+String.format("%03d",count-1),((List<?>)first.get("tableKeys")).getFirst());assertEquals(false,first.get("locked"));
            assertEquals(List.of("parse-failed"),((Map<?,?>)groups.getLast()).get("failedKeys"));
            var allFiles=(Map<?,?>)f.run(Map.of("action","file-page","dbId","source","revision","tree-v1","pageSize",1,"all",true));
            assertEquals(count/2+1,((List<?>)allFiles.get("list")).size());assertEquals(3,f.base.reads,"complete bounded file tree uses the same three batch reads");
            var searched=(Map<?,?>)f.run(Map.of("action","file-page","dbId","source","revision","tree-v1","search","key-000","pageSize",1));
            assertEquals(1,((Number)searched.get("total")).intValue());assertEquals(2,((List<?>)((Map<?,?>)((List<?>)searched.get("list")).getFirst()).get("tableKeys")).size());assertEquals(3,f.base.reads);
            var report=(Map<?,?>)f.run(Map.of("action","report-page","dbId","source","revision","tree-v1","pageSize",100));
            assertEquals(count/2+1,((Number)report.get("total")).intValue());assertEquals(3,f.base.reads);
            assertEquals(count/2,((Number)((Map<?,?>)report.get("summary")).get("pending")).intValue());
            var reported=(Map<?,?>)f.run(Map.of("action","report-page","dbId","source","revision","tree-v1","reportStatus","reported"));
            assertEquals(1,((Number)reported.get("total")).intValue());
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","report-page","dbId","source","revision","tree-v1","reportStatus","invalid")));
            assertInstanceOf(ExitValue.class,f.run(Map.of("action","file-page","dbId","source","revision","tree-v1","sortField","tableName")));
            for(String action:List.of("file-page","report-page")){
                tables.getLast().put("tid","foreign");store.accept(null);
                assertInstanceOf(ExitValue.class,f.run(Map.of("action",action,"dbId","source","revision","tree-v1","pageSize",1)));
                tables.getLast().put("tid","snapshot-"+(count-1));store.accept(null);
                f.base.jdbc.update("update file_upload_t set tenant_id='other' where tid=?",emptyFile);
                assertInstanceOf(ExitValue.class,f.run(Map.of("action",action,"dbId","source","revision","tree-v1","pageSize",1)));
                f.base.jdbc.update("update file_upload_t set tenant_id='police' where tid=?",emptyFile);
                f.base.scope.denied=true;
                assertInstanceOf(ExitValue.class,f.run(Map.of("action",action,"dbId","source","revision","tree-v1")));assertEquals(1,f.base.reads);
                f.base.scope.denied=false;
            }
            System.out.println("File/table workflow: tables="+count+", files="+(count/2+1)+", SQL=3 for each page; complete children, unparsed files and all off-page IDs validated");
        }
    }
    @Test void draftSaveCannotForgeAuditTimes() throws Exception {
        var f=new ReportRegistrationMagicTest.Fixture(1);
        var row=new LinkedHashMap<String,Object>(Map.of("key","legacy-key","tid","table","legacy",true,"tableName","registered_table","tableNameCn","登记数据表","recordCount",2,"fields",List.of(Map.of("columnName","id")),"createdAt","2026-10-01T00:00:00Z","updatedAt","2026-10-02T00:00:00Z"));
        var saved=Map.of("revision","v1","files",List.of(),"tables",List.of(row));
        f.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",cn.hutool.json.JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",saved)));
        row.put("createdAt","2099-01-01T00:00:00Z");row.put("updatedAt","2099-01-01T00:00:00Z");
        var result=(Map<?,?>)f.run(Map.of("action","save","dbId","source","revision","v1","files",List.of(),"tables",List.of(row),"workflowVersion",2));
        var actual=(Map<?,?>)((List<?>)result.get("tables")).getFirst();
        assertEquals("2026-10-01T00:00:00Z",actual.get("createdAt"));assertEquals("2026-10-02T00:00:00Z",actual.get("updatedAt"));
    }
}

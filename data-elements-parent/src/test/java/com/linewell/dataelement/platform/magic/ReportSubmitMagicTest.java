package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class ReportSubmitMagicTest {
    public static class Delivery {
        int calls;boolean fail;
        public Map<String,Object> upload(String source,String table,MultipartFile file){calls++;if(fail)throw new IllegalStateException("transfer failed");return Map.of("remoteFileName","receipt.csv","targetDatasourceName","验证存储");}
    }
    static class Fixture {
        final ReportRegistrationMagicTest.Fixture base=new ReportRegistrationMagicTest.Fixture(1);
        final Delivery delivery=new Delivery();
        Fixture(){base.base.jdbc.update("update db_table_t set table_name='people' where tid='table'");base.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",JSONUtil.toJsonStr(Map.of("accessMode","upload","reportRegistration",Map.of("revision","v1","files",List.of(Map.of("fileId","file","fileName","data.csv")),"tables",List.of(Map.of("tid","table","fileId","file","tableName","people","fileName","data.csv","recordCount",2,"normalized",true,"normalizationVersion",1))))));}
        Object run(String source,String table,String request) throws Exception {
            Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(!Files.isDirectory(root.resolve("magic")))root=root.getParent();
            String content=Files.readString(root.resolve("magic/api/02.数据盘点/02.数据库表/01.元数据管理/27.提交上报文件.ms"));
            String script=content.substring(content.indexOf("================================")+32).replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|cn\\.dev33\\.)[^\\r\\n]*\\r?\\n","");
            var bindings=new HashMap<String,Object>();bindings.put("datasourceId",source);bindings.put("tableId",table);bindings.put("fileId","file");bindings.put("requestId",request);
            bindings.put("file",new MockMultipartFile("file","people.csv","text/csv","id,name\n1,甲\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            bindings.put("db",base.base.db);bindings.put("tenantRuntime",base.base.scope);bindings.put("jsons",new TableDetailFieldSaveMagicTest.Jsons());bindings.put("reportUploadService",delivery);
            try(var login=mockStatic(StpUtil.class)){login.when(StpUtil::getLoginIdAsString).thenReturn("validation-user");return MagicScript.create(script,null).execute(new MagicScriptContext(bindings));}
        }
    }
    @Test void successIsRecordedOnlyAfterDeliveryAndRepeatedRequestDoesNotResend() throws Exception {
        Fixture f=new Fixture();Map<?,?> result=(Map<?,?>)f.run("source","table","request-1");assertEquals("success",result.get("status"));
        assertEquals("success",((Map<?,?>)f.run("source","table","request-1")).get("status"));assertEquals(1,f.delivery.calls);
        assertEquals("success",((Map<?,?>)f.run("source","table","request-2")).get("status"));assertEquals(1,f.delivery.calls);
        assertTrue(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class).contains("\"status\":\"success\""));
    }
    @Test void foreignSourceAndUnrelatedTableNeverReachDelivery() throws Exception {
        Fixture f=new Fixture();assertInstanceOf(ExitValue.class,f.run("foreign-source","table","request-1"));assertInstanceOf(ExitValue.class,f.run("source","foreign","request-1"));assertEquals(0,f.delivery.calls);
    }
    @Test void deliveryFailureIsPersistedAsFailure() throws Exception {
        Fixture f=new Fixture();f.delivery.fail=true;assertInstanceOf(ExitValue.class,f.run("source","table","request-1"));
        assertTrue(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class).contains("\"status\":\"failed\""));
    }
    @Test void concurrentNewRequestDoesNotStartAnotherDeliveryForSendingTable() throws Exception {
        Fixture f=new Fixture();var config=JSONUtil.parseObj(f.base.base.jdbc.queryForObject("select pool_cfg from db_datasource_t where tid='source'",String.class));
        config.getJSONObject("reportRegistration").set("receipts",List.of(Map.of("requestId","sending-1","tableId","table","fileId","file","status","sending")));
        f.base.base.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source'",config.toString());
        assertInstanceOf(ExitValue.class,f.run("source","table","sending-2"));assertEquals(0,f.delivery.calls);
    }
}

package com.linewell.dataelement.platform.magic;

import java.util.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.magicapi.modules.db.SQLModule;
import static org.junit.jupiter.api.Assertions.*;

class NodeHealthSnapshotMagicTest {
    static void columns(org.springframework.jdbc.core.JdbcTemplate jdbc,String table){
        jdbc.execute("alter table "+table+" add (last_health_status varchar(16) default 'unknown', last_health_at timestamp(6), last_health_message varchar(1000), last_health_latency_ms bigint, last_health_revision varchar(64), last_health_check_id varchar(32))");
    }
    static Object execute(SQLModule db,org.springframework.jdbc.core.JdbcTemplate jdbc,Object tenant,Object input){
        try{
            var source=CanonicalMagicSources.byPath("function/01.通用函数/81.节点连通检测快照.ms","node_health_snapshot_v1")
                .replaceAll("(?m)^import (?:tenantRuntime|tableWrite|org.springframework.transaction.support.TransactionTemplate as transactions)\\R","");
            try(var context=com.linewell.dataelement.platform.tenant.domain.TenantContext.use(String.valueOf(tenant.getClass().getMethod("id").invoke(tenant)))) { return MagicScript.create(source,null).execute(new MagicScriptContext(Map.of("db",db,"tenantRuntime",tenant,"input",input,"tableWrite",new com.linewell.dataelement.platform.magic.StructuredTableWriteMagicModule(jdbc),"transactions",new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()))))); }
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    @SuppressWarnings("unchecked") static Map<String,Object> result(Object value){return (Map<String,Object>)value;}
    @Test void snapshotPersistsWithoutProcessCacheAndOldOrChangedConfigurationCannotOverwriteIt(){
        var f=new NifiNodeListMagicTest.Fixture(1);
        var row=f.jdbc.queryForMap("select * from nifi_node_t where tid='own-0'");
        var attempt=result(execute(f.db,f.jdbc,f.scope,Map.of("action","begin")));
        var success=result(execute(f.db,f.jdbc,f.scope,Map.of("action","store","kind","sync","node",row,"attempt",attempt,"result",new HashMap<>(Map.of("success",true,"message","validation connected")))));
        assertEquals(true,success.get("persisted"));
        var rows=List.of(f.jdbc.queryForMap("select * from nifi_node_t where tid='own-0'"));
        var view=result(execute(f.db,f.jdbc,new NifiNodeListMagicTest.Scope(),Map.of("action","view","rows",rows)));
        assertEquals("reachable",result(view.get("own-0")).get("healthStatus"));
        var normalized = new HashMap<String,Object>(rows.getFirst());
        normalized.put("last_health_at",((Timestamp)normalized.get("last_health_at")).toLocalDateTime());
        assertEquals("reachable",result(result(execute(f.db,f.jdbc,f.scope,Map.of("action","view","rows",List.of(normalized)))).get("own-0")).get("healthStatus"));
        var newer=result(execute(f.db,f.jdbc,f.scope,Map.of("action","begin")));
        execute(f.db,f.jdbc,f.scope,Map.of("action","store","kind","sync","node",row,"attempt",newer,"result",new HashMap<>(Map.of("success",false,"message","validation failed"))));
        var late=result(execute(f.db,f.jdbc,f.scope,Map.of("action","store","kind","sync","node",row,"attempt",attempt,"result",new HashMap<>(Map.of("success",true,"message","obsolete success")))));
        assertEquals(true,late.get("superseded"));
        assertEquals("unreachable",f.jdbc.queryForObject("select last_health_status from nifi_node_t where tid='own-0'",String.class));
        f.jdbc.update("update nifi_node_t set updated_time=? where tid='own-0'",Timestamp.valueOf("2031-01-01 00:00:00.123456"));
        var changed=result(execute(f.db,f.jdbc,f.scope,Map.of("action","store","kind","sync","node",row,"attempt",newer,"result",new HashMap<>(Map.of("success",true,"message","obsolete configuration")))));
        assertEquals(false,changed.get("persisted"));
        var current=result(execute(f.db,f.jdbc,f.scope,Map.of("action","view","rows",List.of(f.jdbc.queryForMap("select * from nifi_node_t where tid='own-0'")))));
        assertEquals("unknown",result(current.get("own-0")).get("healthStatus"));
        assertEquals(true,result(current.get("own-0")).get("healthConfigChanged"));
    }
    @Test void snapshotsAreTenantBoundAndKnownPasswordsAreMasked(){
        var f=new NifiNodeListMagicTest.Fixture(1);
        var row=f.jdbc.queryForMap("select * from nifi_node_t where tid='own-0'");
        var attempt=result(execute(f.db,f.jdbc,f.scope,Map.of("action","begin")));
        var response=result(execute(f.db,f.jdbc,f.scope,Map.of("action","store","kind","sync","node",row,"attempt",attempt,"result",new HashMap<>(Map.of("success",false,"message","password=must-not-return")))));
        assertFalse(response.get("message").toString().contains("must-not-return"));
        f.scope.tenant="foreign";
        var forbidden=result(execute(f.db,f.jdbc,f.scope,Map.of("action","store","kind","sync","node",row,"attempt",attempt,"result",new HashMap<>(Map.of("success",true,"message","forbidden")))));
        assertEquals(false,forbidden.get("persisted"));
        assertEquals("unreachable",f.jdbc.queryForObject("select last_health_status from nifi_node_t where tid='own-0'",String.class));
    }
}

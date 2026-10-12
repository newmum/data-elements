package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;

class IdaasAuditTrendTest {
    public static class Database {
        int reads;
        public Database normal(){return this;}
        public List<Map<String,Object>> select(String query) {
            reads++;assertTrue(query.contains("identity_domain=#{domain}"));assertTrue(query.contains("GROUP BY DATE_FORMAT(event_time,'%Y-%m-%d')"));
            return List.of(Map.of("day",LocalDate.now(ZoneOffset.UTC).toString(),"count",12,"people",4,"failures",2));
        }
    }
    @Test void fourteenCalendarBinsUseOneAggregateReadAndStatisticsSortOnTheServer() throws Exception {
        String source=CanonicalMagicSources.byId("442547f994c85528842eaab529f84850").replaceAll("(?m)^import [^\\r\\n]+;\\r?\\n","");
        String helpers="import java.time.LocalDate;import java.time.ZoneOffset;var apiCall=(callback)=>callback();var guard=(permission,appId,domain)=>{if(domain!='public'){throw new java.lang.IllegalArgumentException('Wrong domain');}return {};};\n";
        var db=new Database();var query=Map.of("mode","trend","domain","public","days","14","sortField","count","sortDir","desc");
        var result=(Map<?,?>)MagicScript.create(helpers+source,null).execute(new MagicScriptContext(Map.of("db",db,"request",new IdaasProvisionPagingTest.Request(query),"identityRuntime",new IdaasProvisionPagingTest.Runtime(),"tableSort",new TableSortMagicModule())));
        var rows=(List<Map<String,Object>>)result.get("list");assertEquals(14,rows.size());assertEquals(1,db.reads);
        assertEquals(12,rows.getFirst().get("count"));assertEquals(4,rows.getFirst().get("people"));
        assertTrue(rows.subList(1,14).stream().allMatch(row->((Number)row.get("count")).intValue()==0));
    }
}

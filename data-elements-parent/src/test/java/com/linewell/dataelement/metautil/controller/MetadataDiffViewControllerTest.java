package com.linewell.dataelement.metautil.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetadataDiffViewControllerTest {
    @Test void fullExplorationIsSortedBeforePagingAndReadsStayConstantUnderTenantAndRoleChanges(){
        var jdbc=mock(JdbcTemplate.class);var scopes=mock(DataScopeAuthorizationService.class);
        when(jdbc.queryForList(anyString(),eq("a"),eq("source"))).thenReturn(List.of(Map.of("tid","source","tenant_id","a")));
        when(scopes.canReadDataSource(anyMap())).thenReturn(true);
        var service=new MetadataDiffViewController(jdbc,scopes);
        var rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<7177;i++)rows.add(Map.of("tableName",String.format("Table %04d",i),"fieldCount",i,"recordCount",i,"tid","id"+i));
        try(var tenant=TenantContext.use("a");var auth=mockStatic(StpUtil.class)){
            auth.when(StpUtil::getLoginIdAsString).thenReturn("owner");
            var created=service.store("source",Map.of("added",List.of(),"modified",List.of(),"deleted",List.of(),"unchanged",rows,"summary",Map.of()));
            Map<String,Object> smallBody=new HashMap<>(Map.of("viewId",created.get("viewId"),"pageSize",20,"sortField","fieldCount","sortDir","desc"));
            clearInvocations(jdbc);var small=(Map<String,Object>)service.page(smallBody).get("data");verify(jdbc,times(1)).queryForList(anyString(),eq("a"),eq("source"));
            smallBody.put("pageSize",100);clearInvocations(jdbc);var large=(Map<String,Object>)service.page(smallBody).get("data");verify(jdbc,times(1)).queryForList(anyString(),eq("a"),eq("source"));
            var list=(List<Map<String,Object>>)large.get("list");assertEquals(7177,large.get("total"));assertEquals("Table 7176",list.getFirst().get("tableName"));assertEquals(small.get("list"),list.subList(0,20));
            auth.when(StpUtil::getLoginIdAsString).thenReturn("another");assertThrows(IllegalArgumentException.class,()->service.page(smallBody));
            auth.when(StpUtil::getLoginIdAsString).thenReturn("owner");when(scopes.canReadDataSource(anyMap())).thenReturn(false);assertDoesNotThrow(()->service.page(smallBody));
            verifyNoInteractions(scopes);
            when(scopes.canReadDataSource(anyMap())).thenReturn(true);
            try(var other=TenantContext.use("b")){assertThrows(IllegalArgumentException.class,()->service.page(smallBody));}
            smallBody.put("overrides",Map.of("Other table",Map.of("businessType","业务表")));assertThrows(IllegalArgumentException.class,()->service.page(smallBody));
        }
    }
    @Test void editsAreAppliedBeforeFilteringWithoutChangingServerRowsAndNullsStayLast(){
        var rows=List.<Map<String,Object>>of(new LinkedHashMap<>(Map.of("tableName","b","fieldCount",2,"businessType","业务表","changeStatus","新增")),new LinkedHashMap<>(Map.of("tableName","a","businessType","业务表","changeStatus","新增")));
        var result=MetadataDiffViewController.ordered(rows,Map.of("sortField","fieldCount","sortDir","desc"));
        assertEquals("b",((List<Map<String,Object>>)result.get("list")).getFirst().get("tableName"));
        result=MetadataDiffViewController.ordered(rows,Map.of("businessType","日志表","overrides",Map.of("b",Map.of("businessType","日志表"))));
        assertEquals(1,result.get("total"));assertEquals("业务表",rows.getFirst().get("businessType"));
    }
    @Test void expiredAndEvictedExplorationsRequireARefresh(){
        var jdbc=mock(JdbcTemplate.class);var scopes=mock(DataScopeAuthorizationService.class);
        when(jdbc.queryForList(anyString(),eq("a"),anyString())).thenReturn(List.of(Map.of("tid","source","tenant_id","a")));
        when(scopes.canReadDataSource(anyMap())).thenReturn(true);
        var clock=mock(Clock.class);when(clock.millis()).thenReturn(0L);
        var service=new MetadataDiffViewController(jdbc,scopes,clock);
        var result=Map.<String,Object>of("added",List.of(),"modified",List.of(),"deleted",List.of(),"unchanged",List.of(Map.of("tableName","A")),"summary",Map.of());
        try(var tenant=TenantContext.use("a");var auth=mockStatic(StpUtil.class)){
            auth.when(StpUtil::getLoginIdAsString).thenReturn("owner");
            var first=service.store("first",result);
            for(int i=0;i<4;i++)service.store("source"+i,result);
            assertThrows(IllegalArgumentException.class,()->service.page(Map.of("viewId",first.get("viewId"))));
            var fresh=service.store("new",result);when(clock.millis()).thenReturn(600001L);
            assertThrows(IllegalArgumentException.class,()->service.page(Map.of("viewId",fresh.get("viewId"))));
        }
    }
}

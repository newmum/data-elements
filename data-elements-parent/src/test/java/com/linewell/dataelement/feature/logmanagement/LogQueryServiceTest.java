package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.audit.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LogQueryServiceTest {
    private final AuditStore store=mock(AuditStore.class);
    private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    private final LogQueryService queries=new LogQueryService(store,new AuditSanitizer(new ObjectMapper()));
    private final LogScope owner=new LogScope("validation-tenant","validation-user","validation-app",null,null,"OWNER",List.of(),"operations");
    @Test void twentyAndHundredRowsEachUseExactlyCountAndPageWithSameAuthorizedScope() {
        when(store.named("validation-tenant")).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(),anyMap(),eq(Long.class))).thenReturn(200L);
        for(int size:List.of(20,100)) {
            List<Map<String,Object>> rows=new ArrayList<>();
            for(int n=0;n<size;n++)rows.add(Map.of("tid","validation-"+n,"actor_name","验证人员","actor_org_name","验证部门"));
            when(jdbc.queryForList(anyString(),anyMap())).thenReturn(rows);
            var result=queries.page(owner,"operations",Map.of("page",1,"size",size,"tenantId","forbidden-tenant"));
            assertEquals(size,((List<?>)result.get("list")).size());
        }
        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc,times(2)).queryForObject(sql.capture(),anyMap(),eq(Long.class));
        verify(jdbc,times(2)).queryForList(sql.capture(),anyMap());
        assertTrue(sql.getAllValues().stream().allMatch(value->value.contains("tenant_id=:tenant")&&value.contains("user_id=:owner")));
        verifyNoMoreInteractions(jdbc);
    }
    @Test void mixedUnauthorizedIdBatchIsRejectedAsAWholeInOneRead() {
        when(store.named("validation-tenant")).thenReturn(jdbc);
        when(jdbc.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("tid","allowed")));
        assertThrows(SecurityException.class,()->queries.authorizedEvents(owner,"operations",List.of("allowed","other-tenant-id"),false));
        verify(jdbc,times(1)).queryForList(contains("tenant_id=:tenant"),anyMap());verifyNoMoreInteractions(jdbc);
    }
    @Test void storedUserAgentAndExistingDurationAreReturnedWithoutAdditionalReads() {
        when(store.named("validation-tenant")).thenReturn(jdbc);
        String userAgent="Mozilla/5.0 Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0";
        when(jdbc.queryForObject(anyString(),anyMap(),eq(Long.class))).thenReturn(1L);
        when(jdbc.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("tid","validation-event","user_agent",userAgent,"duration_ms",0L)));
        for(String kind:List.of("auth","operations","requests","security")) {
            var result=queries.page(owner,kind,Map.of());
            var row=(Map<?,?>)((List<?>)result.get("list")).getFirst();
            assertEquals(userAgent,row.get("user_agent"));assertEquals(0L,row.get("duration_ms"));
        }
        var platform=new LogScope(null,"validation-user",null,"workforce",null,"ALL",List.of(),"auth");
        assertTrue(queries.selection(platform,"requests",Map.of("keyword","validation-route")).where().contains("request_path like :keyword"));
        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc,times(4)).queryForObject(anyString(),anyMap(),eq(Long.class));
        verify(jdbc,times(4)).queryForList(sql.capture(),anyMap());
        assertTrue(sql.getAllValues().stream().allMatch(value->value.contains("ip,user_agent,duration_ms")));
        verifyNoMoreInteractions(jdbc);
    }
    @Test void menuFunctionInterfaceNameAndRoutesAreSearchableWithinOwnerAndTenantScope() {
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:validation-log-search;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var db=new org.springframework.jdbc.core.JdbcTemplate(ds);
        db.execute("create table log_system_t(tid varchar(32),tenant_id varchar(64),user_id varchar(64),org_id varchar(32),category varchar(32),event_time timestamp,created_time timestamp,user_account varchar(128),user_name varchar(128),org_name varchar(128),action_code varchar(128),target_name varchar(128),trace_id varchar(64),menu_path varchar(256),operation_name varchar(128),function_code varchar(128),page_route varchar(128),route_path varchar(128))");
        String insert="insert into log_system_t(tid,tenant_id,user_id,category,event_time,menu_path,operation_name,function_code,page_route,route_path) values(?,?,?,'OPERATION',timestamp '2026-11-01 01:00:00',?,?,?,?,?)";
        for(var identity:List.of(List.of("allowed","validation-tenant","validation-user"),List.of("other-owner","validation-tenant","another-user"),List.of("other-tenant","other-tenant","validation-user"))) {
            db.update(insert,identity.get(0),identity.get(1),identity.get(2),"后台管理 → 数据源管理","保存数据源","validation_source_save","/register/source","/dst/database/save_100%");
        }
        var named=new NamedParameterJdbcTemplate(ds);
        for(String keyword:List.of("数据源管理","保存数据源","validation_source_save","/register/source","/dst/database/save_100%")) {
            var selection=queries.selection(owner,"operations",Map.of("keyword",keyword,"startTime","2026-11-01T00:00:00Z","endTime","2026-11-02T00:00:00Z","tenantId","other-tenant"));
            assertEquals(List.of("allowed"),named.queryForList("select tid from log_system_t"+selection.where(),selection.params(),String.class));
        }
        var literal=queries.selection(owner,"operations",Map.of("keyword","save_100%","startTime","2026-11-01T00:00:00Z","endTime","2026-11-02T00:00:00Z"));
        assertEquals("%save!_100!%%",literal.params().get("keyword"));
    }
    @Test void organizationScopeCannotBeBroadenedByClientFiltersOrIds() {
        var org=new LogScope("validation-tenant","validation-user","validation-app",null,null,"ORG",List.of("org-1"),"operations");
        var selection=queries.selection(org,"operations",Map.of("orgId","org-2"));
        assertTrue(selection.where().contains("org_id in (:orgs)"));assertTrue(selection.where().contains("org_id=:orgId"));
        assertEquals(List.of("org-1"),selection.params().get("orgs"));assertEquals("validation-tenant",selection.params().get("tenant"));
    }
    @Test void sortAndBatchLimitsAreEnforced() {
        assertThrows(IllegalArgumentException.class,()->queries.order(Map.of("sortField","tid;delete from log_system_t")));
        assertThrows(IllegalArgumentException.class,()->queries.authorizedEvents(owner,"operations",Collections.nCopies(101,"id"),false));
        assertThrows(IllegalArgumentException.class,()->queries.page(owner,"operations",Map.of("size",101)));
    }
    @Test void legacyAndNewTimesUseOneTimelineBeforePagination() throws Exception {
        var dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:validation-log-time;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var data=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        data.execute("create table validation_events(tid varchar(32),event_time timestamp,created_time timestamp)");
        data.execute("insert into validation_events values('legacy-old',null,timestamp '2026-10-01 09:00:00'),('new',timestamp '2026-10-01 02:00:00',timestamp '2026-10-01 02:00:00'),('legacy-recent',null,timestamp '2026-10-01 11:00:00')");
        var rows=data.queryForList("select tid from validation_events order by coalesce(event_time,created_time - interval '8' hour) desc,tid asc limit 2",String.class);
        assertEquals(List.of("legacy-recent","new"),rows);
        when(store.named("validation-tenant")).thenReturn(jdbc);
        when(jdbc.queryForList(anyString(),anyMap())).thenReturn(List.of());
        queries.read(owner,queries.selection(owner,"auth",Map.of()),Map.of("size",20,"offset",0),"event_time desc,tid asc",false);
        verify(jdbc).queryForList(contains("order by coalesce(event_time,created_time - interval '8' hour) desc,tid asc limit :size"),anyMap());
    }
}

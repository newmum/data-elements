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

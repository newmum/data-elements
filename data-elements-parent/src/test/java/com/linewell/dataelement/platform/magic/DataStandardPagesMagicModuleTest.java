package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DataStandardPagesMagicModuleTest {
    @AfterEach void clear(){TenantContext.clear();}
    @Test void twentyAndHundredUseTwoReadsEachAndOnlyTheSessionTenant(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate named=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(false);
        when(named.queryForObject(anyString(),anyMap(),eq(Number.class))).thenReturn(500L);
        when(named.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("tid","standard","dictionary_count",9)));
        var module=new DataStandardPagesMagicModule(jdbc,named);
        TenantContext.run("tenant-a",()->{
            for(int size:List.of(20,100)) {
                Map<String,Object> result=module.elements(Map.of("tenantId","tenant-b","pageSize",size,"sortField","dictionaryCount","sortDir","desc"));
                assertThat(result.get("total")).isEqualTo(500L);
                assertThat((List<?>)result.get("list")).hasSize(1);
            }
        });
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String,?>> args=ArgumentCaptor.forClass(Map.class);
        verify(named,times(2)).queryForObject(anyString(),anyMap(),eq(Number.class));
        verify(named,times(2)).queryForList(sql.capture(),args.capture());
        assertThat(sql.getAllValues()).allSatisfy(s->{assertThat(s).contains("original.tenant_id=:tenantId","c.tenant_id=:tenantId","ORDER BY CASE WHEN coalesce(counts.dictionary_count,0)","desc, m.tid ASC LIMIT :size OFFSET :offset");});
        assertThat(args.getAllValues()).allSatisfy(a->assertThat(a.get("tenantId")).isEqualTo("tenant-a"));
        assertThat(args.getAllValues().stream().map(a->Integer.parseInt(a.get("size").toString()))).containsExactly(20,100);
    }
    @Test void missingContextAndSqlFragmentsAreRejectedBeforeAnyRead(){
        var jdbc=mock(JdbcTemplate.class);var named=mock(NamedParameterJdbcTemplate.class);var module=new DataStandardPagesMagicModule(jdbc,named);
        assertThatThrownBy(()->module.elements(Map.of())).hasMessageContaining("租户");
        TenantContext.run("tenant-a",()->assertThatThrownBy(()->module.elements(Map.of("sortField","meta_name desc;drop table x"))).isInstanceOf(IllegalArgumentException.class));
        verifyNoInteractions(jdbc,named);
    }
    @Test void codeStatisticsAreAggregatedBeforePaginationWithTwoReadsForTwentyAndHundred(){
        var jdbc=mock(JdbcTemplate.class);var named=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(false);
        when(named.queryForObject(anyString(),anyMap(),eq(Number.class))).thenReturn(150L);
        when(named.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("tid","code-a","item_count",45,"reference_count",80)));
        var module=new DataStandardPagesMagicModule(jdbc,named);
        TenantContext.run("tenant-a",()->{
            for(int size:List.of(20,100)) {
                var result=module.codes(Map.of("tenantId","tenant-b","pageSize",size,"sortField","referenceCount","sortDir","desc"));
                assertThat(result.get("total")).isEqualTo(150L);
                assertThat((List<?>)result.get("list")).hasSize(1);
            }
        });
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);ArgumentCaptor<Map<String,?>> args=ArgumentCaptor.forClass(Map.class);
        verify(named,times(2)).queryForObject(anyString(),anyMap(),eq(Number.class));
        verify(named,times(2)).queryForList(sql.capture(),args.capture());
        assertThat(sql.getAllValues()).allSatisfy(s->assertThat(s).contains("c.tenant_id=:tenantId","original.tenant_id=:tenantId","codes.tenant_id=:tenantId","ranked._rank=1","ORDER BY CASE WHEN coalesce(refs.reference_count,0)","desc, c.tid ASC LIMIT :size OFFSET :offset"));
        assertThat(args.getAllValues()).allSatisfy(a->assertThat(a.get("tenantId")).isEqualTo("tenant-a"));
        assertThat(args.getAllValues().stream().map(a->Integer.parseInt(a.get("size").toString()))).containsExactly(20,100);
    }
    @Test void encodingFilterIsAppliedToCountAndRowsBeforePagination(){
        var jdbc=mock(JdbcTemplate.class);var named=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(false);
        when(named.queryForObject(anyString(),anyMap(),eq(Number.class))).thenReturn(0L);
        when(named.queryForList(anyString(),anyMap())).thenReturn(List.of());
        var module=new DataStandardPagesMagicModule(jdbc,named);
        TenantContext.run("tenant-a",()->module.elements(Map.of("encodingOnly",true,"pageSize",100)));
        ArgumentCaptor<String> count=ArgumentCaptor.forClass(String.class),rows=ArgumentCaptor.forClass(String.class);
        verify(named).queryForObject(count.capture(),anyMap(),eq(Number.class));verify(named).queryForList(rows.capture(),anyMap());
        assertThat(count.getValue()).contains("m.format_pattern IS NOT NULL","TRIM(m.format_pattern)<>''");
        assertThat(rows.getValue()).contains("m.format_pattern IS NOT NULL","TRIM(m.format_pattern)<>'' ORDER BY");
        TenantContext.run("tenant-a",()->assertThatThrownBy(()->module.codes(Map.of("sortField","dict_name;delete from x"))).isInstanceOf(IllegalArgumentException.class));
    }
}

package com.linewell.dataelement.feature.surveillance.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceControlItem;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SurveillanceControlItemPagingTest {
    @Test void sqlOrdersAllVisibleRowsBeforePagingAndKeepsTwoReadsForTwentyAndOneHundred() {
        var jdbc=spy(new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:control_pages;DB_CLOSE_DELAY=-1")));
        jdbc.execute("CREATE TABLE surveillance_control_item_t(tid VARCHAR,tenant_id VARCHAR,engine_code VARCHAR,channel_code VARCHAR,control_item_id VARCHAR,rule_code VARCHAR,subject_type VARCHAR,identifier_type VARCHAR,identifier_value VARCHAR,attributes_json VARCHAR,control_reason VARCHAR,effective_from TIMESTAMP,effective_to TIMESTAMP,status VARCHAR,snapshot_version BIGINT,is_del INT)");
        for(int i=0;i<120;i++)jdbc.update("INSERT INTO surveillance_control_item_t VALUES(?,'a','engine','person',?,'rule','PERSON','ID','value','{}','reason',?,NULL,'ACTIVE',1,0)","id-"+i,"item-"+i,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i)));
        jdbc.update("INSERT INTO surveillance_control_item_t VALUES('hidden','b','engine','person','private','rule','PERSON','ID','value','{}','reason',CURRENT_TIMESTAMP,NULL,'ACTIVE',1,0)");
        var repository=new SurveillanceControlItemRepository(jdbc,new ObjectMapper());
        clearInvocations(jdbc);var small=repository.page("a","engine",null,null,1,20,"effectiveAt","desc");
        verify(jdbc,times(1)).queryForMap(anyString(),any(Object[].class));
        verify(jdbc,times(1)).query(anyString(),any(RowMapper.class),any(Object[].class));
        clearInvocations(jdbc);var large=repository.page("a","engine",null,null,1,100,"effectiveAt","desc");
        verify(jdbc,times(1)).queryForMap(anyString(),any(Object[].class));
        verify(jdbc,times(1)).query(anyString(),any(RowMapper.class),any(Object[].class));
        var rows=(List<SurveillanceControlItem>)large.get("list");
        assertEquals(120L,large.get("total"));assertEquals(100,rows.size());assertEquals(small.get("list"),rows.subList(0,20));
        assertTrue(rows.stream().allMatch(r->r.tenantId().equals("a")));
        assertEquals(1L,repository.page("b","engine",null,null,1,20,null,null).get("total"));
        assertEquals(0L,repository.page("a","other-engine",null,null,1,20,null,null).get("total"));
        assertThrows(IllegalArgumentException.class,()->repository.page("a","engine",null,null,1,20,"effective_from;--","desc"));
    }
}

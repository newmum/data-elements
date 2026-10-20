package com.linewell.dataelement.feature.surveillance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SurveillanceControlItemBatchQueryTest {
    @Test
    void splitsIdentifiersIntoFiveHundredParameterTenantScopedQueries() {
        CountingJdbcTemplate jdbc = new CountingJdbcTemplate();
        jdbc.execute("create table surveillance_control_item_t(tenant_id varchar(40),engine_code varchar(40),channel_code varchar(40),identifier_type varchar(40),identifier_value varchar(40),status varchar(20),effective_from timestamp,effective_to timestamp,is_del int,control_item_id varchar(40),rule_code varchar(40),subject_type varchar(40),snapshot_version bigint,attributes_json varchar(200),source_rule_no varchar(40),control_reason varchar(40))");
        jdbc.update("insert into surveillance_control_item_t(tenant_id,engine_code,channel_code,identifier_type,identifier_value,status,is_del,control_item_id,rule_code,subject_type,snapshot_version) values ('tenant-a','engine','person','ID_CARD_NO','id-0','ACTIVE',0,'item-0','rule','PERSON',1),('tenant-a','engine','person','ID_CARD_NO','id-500','ACTIVE',0,'item-500','rule','PERSON',1),('tenant-b','engine','person','ID_CARD_NO','id-0','ACTIVE',0,'private','rule','PERSON',1)");
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < 501; i++) ids.add("id-" + i);

        Map<String,List<SurveillanceControlItemRepository.ControlItemMatch>> matches =
                new SurveillanceControlItemRepository(jdbc,new ObjectMapper()).findActiveMatches("tenant-a","engine","person",ids);

        assertThat(matches).containsOnlyKeys("id-0","id-500");
        assertThat(matches.get("id-0")).extracting(SurveillanceControlItemRepository.ControlItemMatch::controlItemId).containsExactly("item-0");
        assertThat(jdbc.matchQueries).isEqualTo(2);
        assertThat(jdbc.maximumParameters).isEqualTo(504);
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        int matchQueries;
        int maximumParameters;
        CountingJdbcTemplate() { super(new DriverManagerDataSource("jdbc:h2:mem:surveillance_batch_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1","sa","")); }
        @Override public <T> List<T> query(String sql,RowMapper<T> rowMapper,Object... args) {
            if(sql.contains("FROM surveillance_control_item_t")){matchQueries++;maximumParameters=Math.max(maximumParameters,args.length);}
            return super.query(sql,rowMapper,args);
        }
    }
}

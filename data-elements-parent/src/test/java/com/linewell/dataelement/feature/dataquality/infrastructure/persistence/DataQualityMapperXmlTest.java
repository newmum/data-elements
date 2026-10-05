package com.linewell.dataelement.feature.dataquality.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DataQualityMapperXmlTest {

    @Test
    void selectsMysqlMetricUpsert() throws Exception {
        String sql = metricSql(configuration("mysql"));
        assertThat(sql).contains("on duplicate key update");
    }

    @Test
    void selectsDamengMetricMerge() throws Exception {
        String sql = metricSql(configuration("dm"));
        assertThat(sql).startsWith("merge into dq_quality_metric_t target");
        assertThat(sql).contains("from dual");
    }

    @Test
    void exposesControlAndWorkerStatements() throws Exception {
        Configuration configuration = configuration("mysql");
        assertThat(configuration.hasStatement(id("selectTaskPage"))).isTrue();
        assertThat(configuration.hasStatement(id("selectCandidateShards"))).isTrue();
        assertThat(configuration.hasStatement(id("insertIssue"))).isTrue();
        for(String vendor : new String[]{"mysql","dm","oracle","postgresql","kingbase"}) {
            Configuration routed=configuration(vendor);
            for(String statement:new String[]{"addMetric","insertMetric","lockRun"}) {
                String sql=routed.getMappedStatement(id(statement)).getBoundSql(Map.of()).getSql().toLowerCase();
                assertThat(sql).doesNotContain("on duplicate","merge into","on conflict");
                assertThat(sql).contains("dq_quality_");
            }
        }
    }

    @Test
    void uncheckedHistoricalScoresDoNotBecomeLatestOrAverageQuality() throws Exception {
        for (String vendor : new String[]{"mysql", "dm", "oracle", "postgresql", "kingbase"}) {
            Configuration config = configuration(vendor);
            String latest = config.getMappedStatement(id("selectTaskPage"))
                    .getBoundSql(Map.of("tenantId", "tenant-1"))
                    .getSql().replaceAll("\\s+", " ").trim().toLowerCase();
            assertThat(latest).contains("case when r.checked_count>0 then r.quality_score else null end as latest_score");
            String summary = config.getMappedStatement(id("selectSummary"))
                    .getBoundSql(Map.of("tenantId", "tenant-1", "since", LocalDateTime.now().minusDays(30)))
                    .getSql().replaceAll("\\s+", " ").trim().toLowerCase();
            assertThat(summary).contains("and r.checked_count>0 and r.status in");
        }
    }

    @Test
    void historicalRunDetailSurvivesMissingMetadataWithoutWeakeningTenantOwnership() throws Exception {
        for (String vendor : new String[]{"mysql", "dm", "oracle", "postgresql", "kingbase"}) {
            String sql = configuration(vendor).getMappedStatement(id("selectRun"))
                    .getBoundSql(Map.of("tenantId", "tenant-1", "runId", "run-1"))
                    .getSql().replaceAll("\\s+", " ").trim().toLowerCase();
            assertThat(sql).contains("left join db_datasource_t d", "left join db_table_t t");
            assertThat(sql).contains("q.tenant_id = ?", "d.tenant_id = ?", "t.tenant_id = ?");
            assertThat(sql).contains("where r.tenant_id = ? and r.tid = ? and r.is_del = 0");
        }
    }

    private Configuration configuration(String databaseId) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(databaseId);
        String resource = "mapper/feature/dataquality/DataQualityMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String metricSql(Configuration configuration) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("tid", "metric-1"); values.put("tenantId", "tenant-1");
        values.put("runId", "run-1"); values.put("ruleId", "rule-1");
        values.put("ruleName", "name"); values.put("ruleType", "NOT_NULL");
        values.put("dimension", "COMPLETENESS"); values.put("columnName", "person_name");
        values.put("severity", "HIGH"); values.put("weight", 10);
        values.put("checked", 10L); values.put("violations", 1L);
        values.put("passRate", 90); values.put("score", 90); values.put("now", LocalDateTime.now());
        BoundSql sql = configuration.getMappedStatement(id("upsertMetric")).getBoundSql(values);
        return sql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private String id(String statement) { return DataQualityMapper.class.getName() + "." + statement; }
}


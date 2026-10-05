package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineTMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PipelineRepositoryDeploymentTest {
    @Test
    void undeployReallyClearsNullableDeploymentColumnsInDatabase() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:pipeline-null-clearing;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(datasource);
        jdbc.execute("""
                create table nifi_pipeline_t (
                  id varchar(64) primary key, name varchar(200), description varchar(1000), status varchar(32),
                  dsl_json clob, dsl_hash varchar(64), dsl_version bigint, nifi_process_group_id varchar(128),
                  last_deployed_hash varchar(64), last_deployed_at bigint, last_stopped_at bigint,
                  node_mapping_json clob, last_bulletin_id bigint, created_at bigint, updated_at bigint, is_del int)
                """);
        var config = new MybatisConfiguration();
        config.addMapper(NifiPipelineTMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(config);
        try (var tenant = TenantContext.use("test-tenant"); var session = factory.getObject().openSession(true)) {
            var repo = new PipelineRepository(new ObjectMapper(), session.getMapper(NifiPipelineTMapper.class));
            var saved = repo.save(new Pipeline("p1", "测试", null, null, null,
                    new Pipeline.Dsl(1, List.of(), List.of()), "old-group", PipelineStatus.STOPPED,
                    "old-hash", 10L, null, new NifiNodeMapping(java.util.Map.of(), java.util.Map.of(), java.util.Map.of()), 8L));
            repo.update(saved.id(), p -> new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(),
                    p.dsl(), null, PipelineStatus.SAVED, null, null, p.lastStoppedAt(), null, null));
            session.clearCache();
            Pipeline reloaded = repo.findById("p1").orElseThrow();
            assertThat(reloaded.nifiProcessGroupId()).isNull();
            assertThat(reloaded.lastDeployedHash()).isNull();
            assertThat(reloaded.nodeMapping()).isNull();
            assertThat(jdbc.queryForMap("select nifi_process_group_id, last_deployed_hash, last_deployed_at, node_mapping_json, last_bulletin_id from nifi_pipeline_t"))
                    .allSatisfy((key, value) -> assertThat(value).isNull());
        }
    }
}

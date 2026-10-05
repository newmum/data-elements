package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineTMapper;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.Test;

class NifiPipelineTableSchemaTest {
    @Test
    void existingTaskPipelineQueriesUseTheCurrentTenantDatabase() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(NifiPipelineTMapper.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(NifiPipelineTMapper.class.getName());

        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, NifiPipelineT.class);
        MappedStatement listStatement = configuration.getMappedStatement(
                NifiPipelineTMapper.class.getName() + ".selectList");
        MappedStatement detailStatement = configuration.getMappedStatement(
                NifiPipelineTMapper.class.getName() + ".selectById");
        LambdaQueryWrapper<NifiPipelineT> wrapper = new LambdaQueryWrapper<NifiPipelineT>()
                .eq(NifiPipelineT::getIsDel, 0);
        BoundSql listSql = listStatement.getBoundSql(Map.of("ew", wrapper, "param1", wrapper));
        BoundSql detailSql = detailStatement.getBoundSql("2099049046473453569");

        assertThat(tableInfo.getTableName()).isEqualTo("nifi_pipeline_t");
        assertThat(listSql.getSql()).contains("nifi_pipeline_t").doesNotContain("baseline_ga.");
        assertThat(detailSql.getSql()).contains("nifi_pipeline_t").doesNotContain("baseline_ga.");
    }
}

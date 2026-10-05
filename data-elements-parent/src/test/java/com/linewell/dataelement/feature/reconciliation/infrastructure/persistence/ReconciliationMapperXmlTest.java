package com.linewell.dataelement.feature.reconciliation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper.ReconciliationControlMapper;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper.ReconciliationWorkerMapper;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ReconciliationMapperXmlTest {

    @Test
    void loadsMysqlStatementsAndSelectsMysqlDiffWriter() throws Exception {
        Configuration configuration = configuration("mysql");

        BoundSql sql = diffBatchSql(configuration);

        assertThat(normalize(sql.getSql()))
                .startsWith("insert ignore into data_reconcile_diff_t");
        assertThat(configuration.hasStatement(
                ReconciliationControlMapper.class.getName() + ".selectPolicyPage"
        )).isTrue();
    }

    @Test
    void loadsDamengStatementsAndSelectsMergeDiffWriter() throws Exception {
        Configuration configuration = configuration("dm");

        BoundSql sql = diffBatchSql(configuration);

        assertThat(normalize(sql.getSql()))
                .startsWith("merge into data_reconcile_diff_t target");
        assertThat(normalize(sql.getSql())).contains("from dual");
    }

    @Test
    void loadsOracleStatementsAndSelectsMergeDiffWriter() throws Exception {
        Configuration configuration = configuration("oracle");

        BoundSql sql = diffBatchSql(configuration);

        assertThat(normalize(sql.getSql()))
                .startsWith("merge into data_reconcile_diff_t target");
        assertThat(normalize(sql.getSql())).contains("from dual");
    }

    @Test
    void loadsPostgresqlStatementsAndSelectsConflictSafeWriter() throws Exception {
        Configuration configuration = configuration("postgresql");

        BoundSql sql = diffBatchSql(configuration);

        assertThat(normalize(sql.getSql()))
                .startsWith("insert into data_reconcile_diff_t");
        assertThat(normalize(sql.getSql())).endsWith("on conflict do nothing");
    }

    @Test
    void loadsKingbaseStatementsAndSelectsConflictSafeWriter() throws Exception {
        Configuration configuration = configuration("kingbase");

        BoundSql sql = diffBatchSql(configuration);

        assertThat(normalize(sql.getSql())).endsWith("on conflict do nothing");
    }

    private Configuration configuration(String databaseId) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(databaseId);
        parse(configuration, "mapper/feature/reconciliation/ReconciliationControlMapper.xml");
        parse(configuration, "mapper/feature/reconciliation/ReconciliationWorkerMapper.xml");
        return configuration;
    }

    private void parse(Configuration configuration, String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            new XMLMapperBuilder(
                    input,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }
    }

    private BoundSql diffBatchSql(Configuration configuration) {
        String statementId =
                ReconciliationWorkerMapper.class.getName() + ".insertDiffBatch";
        MappedStatement statement = configuration.getMappedStatement(statementId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tid", "diff-1");
        row.put("tenantId", "tenant-1");
        row.put("runId", "run-1");
        row.put("bucketId", "bucket-1");
        row.put("diffHash", "hash");
        row.put("businessKey", "key");
        row.put("diffType", "VALUE_MISMATCH");
        row.put("fieldName", "name");
        row.put("createdTime", LocalDateTime.now());
        return statement.getBoundSql(Map.of("rows", List.of(row)));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}

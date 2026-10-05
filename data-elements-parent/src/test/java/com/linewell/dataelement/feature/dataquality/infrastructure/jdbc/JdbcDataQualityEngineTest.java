package com.linewell.dataelement.feature.dataquality.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Evaluation;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Plan;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Rule;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Shard;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcDataQualityEngineTest {

    private static final String URL =
            "jdbc:h2:mem:dq_unique;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private JdbcDataQualityEngine engine;
    private Plan plan;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS person_source");
            statement.execute("CREATE TABLE person_source (id BIGINT PRIMARY KEY, id_card VARCHAR(32))");
            statement.execute("INSERT INTO person_source VALUES (1, 'A'), (2, 'B'), (3, 'C'), (4, 'A')");
        }

        RegisteredJdbcDataSourceResolver resolver = new RegisteredJdbcDataSourceResolver(
                null,
                null
        );
        engine = new JdbcDataQualityEngine(
                resolver,
                new DataQualityProperties(),
                new ObjectMapper()
        );
        RegisteredJdbcEndpoint endpoint = new RegisteredJdbcEndpoint(
                "source", "source", "table", "person_source", URL, "sa", "", "org.h2.Driver"
        );
        Rule unique = new Rule(
                "rule-1", "ID unique", "UNIQUE", "UNIQUENESS", "id_card", null,
                Map.of(), "HIGH", 100
        );
        plan = new Plan("tenant", "run", "task", "id", 2, 10, endpoint, List.of(unique));
    }

    @Test
    void evaluatesUniqueRuleGloballyOnlyOnceAcrossShards() {
        Evaluation first = engine.evaluate(plan, new Shard("s1", 0, "1", "3", false));
        Evaluation second = engine.evaluate(plan, new Shard("s2", 1, "3", "4", true));

        assertThat(first.rowCount() + second.rowCount()).isEqualTo(4);
        assertThat(first.metrics()).singleElement().satisfies(metric -> {
            assertThat(metric.checkedCount()).isEqualTo(4);
            assertThat(metric.violationCount()).isEqualTo(1);
        });
        assertThat(first.samples()).singleElement().satisfies(sample ->
                assertThat(sample.rowKey()).isEqualTo("A")
        );
        assertThat(second.metrics()).isEmpty();
        assertThat(second.samples()).isEmpty();
    }

    private Evaluation evaluate(List<Rule> rules) {
        return engine.evaluate(new Plan("tenant","run","task",null,1,0,plan.endpoint(),rules,15),new Shard("one",0,null,null,true));
    }
    private Rule rule(String type,String column,Map<String,Object> parameters) {
        return new Rule(type,type,type,"TEST",column,null,parameters,"HIGH",10);
    }
    @Test void compositeUniqueUsesTheCompleteTuple() {
        assertThat(evaluate(List.of(rule("UNIQUE","id_card",Map.of("columns",List.of("id_card","id"))))).metrics().getFirst().violationCount()).isZero();
    }
    @Test void actualProfileStatisticsAreNotQualityScores() throws Exception {
        try(Connection c=DriverManager.getConnection(URL,"sa","");var s=c.createStatement()){s.executeUpdate("UPDATE person_source SET id_card=NULL WHERE id=2");s.executeUpdate("UPDATE person_source SET id_card=' ' WHERE id=3");}
        var result=evaluate(List.of(rule("PROFILE","id_card",Map.of("scanMode","FULL","scanLimit",0))));
        var metric=result.metrics().getFirst();assertThat(metric.checkedCount()).isZero();assertThat(metric.score()).isNull();assertThat(result.samples()).isEmpty();
        assertThat(metric.profile()).containsEntry("scannedRows",4L).containsEntry("nullCount",1L).containsEntry("blankCount",1L).containsEntry("distinctCount",2L);
    }
    @Test void sampledProfileRespectsTheActualBudget() {
        var result=evaluate(List.of(rule("PROFILE","id_card",Map.of("scanMode","SAMPLE","scanLimit",2))));assertThat(result.rowCount()).isEqualTo(2);assertThat(result.metrics().getFirst().profile()).containsEntry("scanMode","SAMPLE").containsEntry("scanLimit",2);
    }
    @Test void emptyProfileReportsUnknownRates() throws Exception {
        try(Connection c=DriverManager.getConnection(URL,"sa","");var s=c.createStatement()){s.executeUpdate("DELETE FROM person_source");}
        assertThat(evaluate(List.of(rule("PROFILE","id_card",Map.of("scanMode","FULL")))).metrics().getFirst().profile()).containsEntry("nullRate",null).containsEntry("distinctCount",0L);
    }
    @Test void referenceUsesCorrelatedKeysAndTheConfiguredNullPolicy() throws Exception {
        try(Connection c=DriverManager.getConnection(URL,"sa","");var s=c.createStatement()){s.execute("DROP TABLE IF EXISTS target_fixture");s.execute("CREATE TABLE target_fixture (code VARCHAR(32))");s.execute("INSERT INTO target_fixture VALUES ('A'),('C')");s.executeUpdate("UPDATE person_source SET id_card=NULL WHERE id=3");}
        var p=new java.util.LinkedHashMap<String,Object>(Map.of("resolvedTargetTable","target_fixture","sourceColumns",List.of("id_card"),"targetColumns",List.of("code"),"allowNull",false));
        assertThat(evaluate(List.of(rule("REFERENCE","id_card",p))).metrics().getFirst().violationCount()).isEqualTo(2);
        p.put("allowNull",true);assertThat(evaluate(List.of(rule("REFERENCE","id_card",p))).metrics().getFirst().violationCount()).isEqualTo(1);
    }
    @Test void timelinessHasStablePastFutureAndNullBoundaries() throws Exception {
        try(Connection c=DriverManager.getConnection(URL,"sa","");var s=c.createStatement()){s.execute("ALTER TABLE person_source ADD COLUMN IF NOT EXISTS event_time TIMESTAMP");s.execute("UPDATE person_source SET event_time=TIMESTAMP '2026-09-29 09:59:00' WHERE id=1");s.execute("UPDATE person_source SET event_time=TIMESTAMP '2026-09-28 09:59:00' WHERE id=2");s.execute("UPDATE person_source SET event_time=TIMESTAMP '2026-09-29 10:10:00' WHERE id=3");}
        assertThat(evaluate(List.of(rule("TIMELINESS","event_time",Map.of("evaluationTime","2026-09-29T10:00:00","maxAgeMinutes",60,"futureToleranceMinutes",0)))).metrics().getFirst().violationCount()).isEqualTo(3);
    }
}

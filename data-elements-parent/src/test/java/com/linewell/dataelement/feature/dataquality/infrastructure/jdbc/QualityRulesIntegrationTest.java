package com.linewell.dataelement.feature.dataquality.infrastructure.jdbc;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
class QualityRulesIntegrationTest {
 static final String URL="jdbc:h2:mem:dq_rules;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
 JdbcDataQualityEngine engine;
 @BeforeEach void setup() throws Exception {
  try(var c=DriverManager.getConnection(URL,"sa","");var s=c.createStatement()){s.execute("DROP TABLE IF EXISTS fixture");s.execute("CREATE TABLE fixture(id BIGINT PRIMARY KEY,name VARCHAR(20),age INT,code VARCHAR(20))");s.execute("INSERT INTO fixture VALUES(1,'Alice',20,'A'),(2,'',200,'A'),(3,NULL,NULL,'bad'),(4,'Bob',30,'B')");}
  engine=new JdbcDataQualityEngine(new RegisteredJdbcDataSourceResolver(null,null),new DataQualityProperties(),new ObjectMapper());
 }
 Rule rule(String type,String col,Map<String,Object> params){return new Rule(type,type,type,"VALIDITY",col,null,params,"HIGH",10);}
 Plan plan(List<Rule> rules,int samples){return new Plan("tenant","run","task","id",1,samples,new RegisteredJdbcEndpoint("s","s","t","fixture",URL,"sa","","org.h2.Driver"),rules,60);}
 @Test void allSevenRulesProduceRealMetricsAndBoundedSamples(){
  var rules=List.of(rule("NOT_NULL","name",Map.of()),rule("NOT_EMPTY","name",Map.of()),rule("UNIQUE","code",Map.of()),rule("RANGE","age",Map.of("min",0,"max",100)),rule("ENUM","code",Map.of("values",List.of("A","B"))),rule("LENGTH","name",Map.of("minLength",1,"maxLength",10)),rule("REGEX","code",Map.of("pattern","^[AB]$")));
  assertThat(engine.precheck(plan(rules,5)).get("success")).isEqualTo(true);
  var result=engine.evaluate(plan(rules,5),new Shard("s",0,null,null,true));
  assertThat(result.rowCount()).isEqualTo(4);assertThat(result.checkedCount()).isEqualTo(28);assertThat(result.metrics()).hasSize(7);assertThat(result.violationCount()).isEqualTo(10);assertThat(result.samples().size()).isLessThanOrEqualTo(5);
 }
 @Test void invalidParametersFailAtPrecheckInsteadOfOnlyAfterQueueing(){
  for(var invalid:List.of(rule("ENUM","code",Map.of()),rule("REGEX","code",Map.of("pattern","[")),rule("RANGE","age",Map.of("min",100,"max",1)),rule("LENGTH","name",Map.of("minLength",-1)))) assertThat(engine.precheck(plan(List.of(invalid),0)).get("success")).isEqualTo(false);
 }
 @Test void missingFieldsFailAndZeroSampleLimitIsRespected(){assertThat(engine.precheck(plan(List.of(rule("NOT_NULL","missing",Map.of())),0)).get("success")).isEqualTo(false);assertThat(engine.evaluate(plan(List.of(rule("NOT_EMPTY","name",Map.of())),0),new Shard("s",0,null,null,true)).samples()).isEmpty();}
 @Test void emptyTableHasNoFabricatedPerfectScore() throws Exception {
  try(var c=DriverManager.getConnection(URL,"sa","");var s=c.createStatement()){s.execute("DELETE FROM fixture");}
  var p=plan(List.of(rule("NOT_NULL","name",Map.of())),0);
  var multi=new Plan(p.tenantId(),p.runId(),p.taskId(),"id",2,0,p.endpoint(),p.rules(),60);
  assertThat(engine.precheck(multi).get("success")).isEqualTo(true);
  var wrongKey=new Plan(p.tenantId(),p.runId(),p.taskId(),"code",2,0,p.endpoint(),p.rules(),60);
  assertThat(engine.precheck(wrongKey).get("success")).isEqualTo(false);
  assertThat(engine.numericRange(p)).isNull();var result=engine.evaluate(p,new Shard("s",0,null,null,true));
  assertThat(result.rowCount()).isZero();assertThat(result.checkedCount()).isZero();assertThat(result.metrics()).singleElement().satisfies(metric->{assertThat(metric.score()).isNull();assertThat(metric.passRate()).isNull();});
 }
}

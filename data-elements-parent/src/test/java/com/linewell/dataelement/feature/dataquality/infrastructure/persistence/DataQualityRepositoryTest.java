package com.linewell.dataelement.feature.dataquality.infrastructure.persistence;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
class DataQualityRepositoryTest {
 @Test void zeroCheckMetricsReturnUnknownInsteadOfStoredNeutralScore() {
  var mapper=mock(DataQualityMapper.class);when(mapper.selectRun("tenant","run")).thenReturn(new java.util.LinkedHashMap<>(Map.of("tid","run")));
  when(mapper.selectRunMetrics("tenant","run")).thenReturn(java.util.List.of(new java.util.LinkedHashMap<>(Map.of("checked_count",0,"pass_rate",0,"score",0))));
  when(mapper.selectRunShards("tenant","run")).thenReturn(java.util.List.of());
  var repository=new DataQualityRepository(mapper,null,new DataQualityProperties(),new ObjectMapper());
  var metrics=(java.util.List<Map<String,Object>>)repository.runDetail("tenant","run").get("metrics");
  assertThat(metrics.get(0)).containsEntry("pass_rate",null).containsEntry("score",null);
 }
 @Test void cancelledEvaluationDoesNotWriteMetricsOrSamples() {
  var mapper=mock(DataQualityMapper.class);when(mapper.lockRun("tenant","run")).thenReturn(new java.util.LinkedHashMap<>(Map.of("status","CANCELLED","cancel_requested",1)));
  var repository=new DataQualityRepository(mapper,null,new DataQualityProperties(),new ObjectMapper());repository.saveEvaluation("tenant","run","shard",null);
  verify(mapper,never()).upsertMetric(any());verify(mapper,never()).addMetric(any());verify(mapper,never()).insertIssue(any());
 }
 @Test void completedShardIsNotCountedTwiceOnRetry() {
  var mapper=mock(DataQualityMapper.class);when(mapper.lockRun("tenant","run")).thenReturn(new java.util.LinkedHashMap<>(Map.of("status","RUNNING","cancel_requested",0)));when(mapper.selectShard("tenant","run","shard")).thenReturn(new java.util.LinkedHashMap<>(Map.of("status","COMPLETED")));
  var repository=new DataQualityRepository(mapper,null,new DataQualityProperties(),new ObjectMapper());repository.saveEvaluation("tenant","run","shard",null);verify(mapper,never()).addMetric(any());verify(mapper,never()).insertMetric(any());
 }
 @Test void runUsesImmutableSnapshotRatherThanCurrentEditedRules() throws Exception {
  var mapper=mock(DataQualityMapper.class);var resolver=mock(RegisteredJdbcDataSourceResolver.class);var json=new ObjectMapper();
  var snapshot=Map.of("datasource_id","original-source","table_id","original-table","shard_count",2,"sample_limit",0,"timeout_minutes",5,"rules",java.util.List.of(Map.of("tid","original-rule","rule_name","名称非空","rule_type","NOT_NULL","quality_dimension","COMPLETENESS","column_name","name","parameters_json","{}","severity","HIGH","weight_value",10)));
  when(mapper.selectRun("tenant","run")).thenReturn(new java.util.LinkedHashMap<>(Map.of("task_id","task","task_snapshot",json.writeValueAsString(snapshot))));
  when(resolver.resolve("tenant","original-source","original-table")).thenReturn(new RegisteredJdbcEndpoint("s","s","t","physical","jdbc:h2:mem:quality","sa","","org.h2.Driver"));
  var repository=new DataQualityRepository(mapper,resolver,new DataQualityProperties(),json);var plan=repository.loadRunPlan("tenant","run");
  assertThat(plan.rules()).singleElement().satisfies(rule->assertThat(rule.id()).isEqualTo("original-rule"));assertThat(plan.sampleLimit()).isZero();assertThat(plan.timeoutSeconds()).isEqualTo(300);verify(mapper,never()).selectTaskRules(anyString(),anyString());
 }
 @Test void activeTaskCannotBeEditedOrDeleted() {
  var mapper=mock(DataQualityMapper.class);when(mapper.lockTask("tenant","task")).thenReturn(new java.util.LinkedHashMap<>(Map.of("tid","task")));when(mapper.activeRuns("tenant","task")).thenReturn(1);
  var repository=new DataQualityRepository(mapper,mock(RegisteredJdbcDataSourceResolver.class),new DataQualityProperties(),new ObjectMapper());
  assertThatThrownBy(()->repository.deleteTask("tenant","task","user")).hasMessageContaining("运行中");verify(mapper,never()).deleteTask(any());
 }
 @Test void cancellationFinalizesPendingRunsAndRetainsHistory() {
  var mapper=mock(DataQualityMapper.class);when(mapper.requestCancel(any())).thenReturn(1);var repository=new DataQualityRepository(mapper,null,new DataQualityProperties(),new ObjectMapper());repository.cancel("tenant","run");verify(mapper).cancelRun(argThat(row->row.get("tenantId").equals("tenant")&&row.get("runId").equals("run")));verify(mapper).cancelPendingShards(any());
 }
 @Test void batchColumnOptionsGroupsTenantScopedRowsAndKeepsEmptyTables() {
  var mapper=mock(DataQualityMapper.class);
  when(mapper.selectColumnOptionsBatch("tenant",List.of("table-a","table-b"))).thenReturn(List.of(new LinkedHashMap<>(Map.of("table_id","table-a","column_name","id"))));
  var repository=new DataQualityRepository(mapper,null,new DataQualityProperties(),new ObjectMapper());
  assertThat(repository.columnOptionsBatch("tenant",List.of("table-a","table-b","table-a")))
    .containsOnlyKeys("table-a","table-b").satisfies(result->{assertThat(result.get("table-a")).hasSize(1);assertThat(result.get("table-b")).isEmpty();});
  verify(mapper).selectColumnOptionsBatch("tenant",List.of("table-a","table-b"));
  assertThatThrownBy(()->repository.columnOptionsBatch("tenant",java.util.Collections.nCopies(51,"table-a")))
    .isInstanceOf(IllegalArgumentException.class);
 }
 @Test void referenceRulesShareOneTargetOwnershipAndColumnLookup() {
  var mapper=mock(DataQualityMapper.class);var resolver=mock(RegisteredJdbcDataSourceResolver.class);
  when(mapper.selectValidTableIds("tenant","source",List.of("target"))).thenReturn(List.of("target"));
  when(mapper.selectColumnOptionsBatch("tenant",List.of("main","target"))).thenReturn(List.of(
    new LinkedHashMap<>(Map.of("table_id","main","column_name","id")),
    new LinkedHashMap<>(Map.of("table_id","main","column_name","name")),
    new LinkedHashMap<>(Map.of("table_id","target","column_name","ref_id"))));
  var rule1=new LinkedHashMap<String,Object>(Map.of("ruleName","关联1","ruleType","REFERENCE","columnName","id","parameters",Map.of("targetTableId","target","sourceColumns",List.of("id"),"targetColumns",List.of("ref_id"))));
  var rule2=new LinkedHashMap<String,Object>(Map.of("ruleName","关联2","ruleType","REFERENCE","columnName","name","parameters",Map.of("targetTableId","target","sourceColumns",List.of("name"),"targetColumns",List.of("ref_id"))));
  var repository=new DataQualityRepository(mapper,resolver,new DataQualityProperties(),new ObjectMapper());
  assertThat(repository.saveTask("tenant","operator",Map.of("taskName","质检","datasourceId","source","tableId","main","rules",List.of(rule1,rule2)))).isNotBlank();
  verify(resolver).resolve("tenant","source","main");verify(resolver,never()).resolve("tenant","source","target");
  verify(mapper).selectColumnOptionsBatch("tenant",List.of("main","target"));
  verify(mapper,never()).selectColumnOptions(any(),any());
 }
}

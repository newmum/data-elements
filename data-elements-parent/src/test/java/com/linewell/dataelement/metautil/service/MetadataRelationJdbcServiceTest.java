package com.linewell.dataelement.metautil.service;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;import java.sql.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class MetadataRelationJdbcServiceTest {
 @Test void numericKeysAndBinaryValuesNormalizeWithoutLoss(){assertThat(MetadataRelationJdbcService.normalizeKey(12)).isEqualTo(MetadataRelationJdbcService.normalizeKey(new BigDecimal("12.00")));assertThat(MetadataRelationJdbcService.normalizeKey(new byte[]{1,15})).isEqualTo("010f");}
 @Test void arbitrarySqlCannotBeAnIdentifier(){assertThatThrownBy(()->MetadataRelationJdbcService.identifier("id); DROP TABLE users" )).isInstanceOf(IllegalArgumentException.class);assertThat(MetadataRelationJdbcService.identifier("业务编号")).isEqualTo("业务编号");}
 @Test void conditionalOrCrossSourceRelationsCannotCreatePhysicalForeignKeys(){var d=definition();d.put("targetId","other");assertThatThrownBy(()->MetadataRelationJdbcService.requirePhysical(d)).hasMessageContaining("同一个");d.put("targetId","s");d.put("conditions",List.of(Map.of("side","source")));assertThatThrownBy(()->MetadataRelationJdbcService.requirePhysical(d)).hasMessageContaining("带条件");}
 @Test void joinAndViewsCannotCreatePhysicalForeignKeys(){var d=definition();d.put("relationType","JOIN");assertThatThrownBy(()->MetadataRelationJdbcService.requirePhysical(d)).hasMessageContaining("引用");d.put("relationType","REFERENCE");d.put("targetKind","VIEW");assertThatThrownBy(()->MetadataRelationJdbcService.requirePhysical(d)).hasMessageContaining("视图");}
 @Test void validationComparesTheEntireCompositeTupleAndRequiredCardinality()throws Exception {
  var resolver=mock(DataSourceConnectionPropertyResolver.class);when(resolver.resolve(eq("tenant"),anyString())).thenReturn(Map.of("dbType","mysql","jdbcUrl","jdbc:mysql://localhost/test"));
  Connection a=sampleConnection(List.of(Arrays.asList(1,2),Arrays.asList(1,3),Arrays.asList(null,3))),b=sampleConnection(List.of(Arrays.asList(new BigDecimal("1.00"),2)));
  try(var scope=TenantContext.use("tenant");var driver=mockStatic(DriverManager.class)){
   driver.when(()->DriverManager.getConnection(anyString(),any(Properties.class))).thenReturn(a,b);
   var d=definition();d.put("targetsPerSource",Map.of("min","1","max","1"));var r=new MetadataRelationJdbcService(resolver).validate(d,20);assertThat(r.get("simulated")).isEqualTo(false);assertThat(r.get("validationStatus")).isEqualTo("violations_found");Map<?,?> m=(Map<?,?>)r.get("metrics");assertThat(m.get("matchedDistinctTuples")).isEqualTo("1");assertThat(m.get("orphanRows")).isEqualTo("1");assertThat(m.get("sourceNullRows")).isEqualTo("1");
  }
 }
 @Test void aTruncatedTargetCannotProduceAFakeOrphanConclusion()throws Exception {
  var resolver=mock(DataSourceConnectionPropertyResolver.class);when(resolver.resolve(eq("tenant"),anyString())).thenReturn(Map.of("dbType","mysql","jdbcUrl","jdbc:mysql://localhost/test"));
  var target=new ArrayList<List<Object>>();for(int i=0;i<20001;i++)target.add(List.of(i,1));Connection a=sampleConnection(List.of(List.of(99999,1))),b=sampleConnection(target);
  try(var scope=TenantContext.use("tenant");var driver=mockStatic(DriverManager.class)){driver.when(()->DriverManager.getConnection(anyString(),any(Properties.class))).thenReturn(a,b);var r=new MetadataRelationJdbcService(resolver).validate(definition(),20);assertThat(r.get("validationStatus")).isEqualTo("inconclusive");assertThat(((Map<?,?>)r.get("metrics")).get("orphanRows")).isNull();assertThat(((Map<?,?>)r.get("metrics")).get("containmentRatio")).isNull();}
 }
 @Test void frozenFusionLoadsOneOutputAndRejectsDuplicateReloadOrJoinExplosion()throws Exception {
  String url="jdbc:h2:mem:model_fusion_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  try(Connection c=DriverManager.getConnection(url,"sa","")){try(Statement st=c.createStatement()){
   st.execute("CREATE TABLE fusion_a (id INT PRIMARY KEY, full_name VARCHAR(50))");
   st.execute("CREATE TABLE fusion_b (a_id INT, category VARCHAR(30))");
   st.execute("CREATE TABLE fusion_d (id INT, display_name VARCHAR(50), category VARCHAR(30))");
   st.execute("INSERT INTO fusion_a VALUES (1,' Alice '),(2,'Bob')");
   st.execute("INSERT INTO fusion_b VALUES (1,'gold'),(2,'silver')");
  }}
  var resolver=mock(DataSourceConnectionPropertyResolver.class);
  when(resolver.resolve("tenant","source")).thenReturn(Map.of("dbType","mysql","jdbcUrl",url,"username","sa"));
  var service=new MetadataRelationJdbcService(resolver);
  var plan=Map.<String,Object>of("targetSourceId","source","targetTable","fusion_d","baseEntityId","a",
   "sources",List.of(Map.of("entityId","a","sourceId","source","tableName","fusion_a"),Map.of("entityId","b","sourceId","source","tableName","fusion_b")),
   "joins",List.of(Map.of("leftEntityId","a","rightEntityId","b","joinType","LEFT","rightCardinality","ONE","dedupe","NONE","pairs",List.of(Map.of("leftName","id","rightName","a_id")))),
   "mappings",List.of(Map.of("sourceEntityId","a","sourceName","id","targetName","id","transform","DIRECT"),Map.of("sourceEntityId","a","sourceName","full_name","targetName","display_name","transform","TRIM"),Map.of("sourceEntityId","b","sourceName","category","targetName","category","transform","DIRECT")));
  try(var scope=TenantContext.use("tenant")){
   assertThat(service.loadModelFusion(plan).get("insertedRows")).isEqualTo(2);
   assertThatThrownBy(()->service.loadModelFusion(plan)).hasMessageContaining("已有数据");
  }
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement();ResultSet rows=st.executeQuery("SELECT display_name,category FROM fusion_d ORDER BY id")){
   assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Alice");assertThat(rows.getString(2)).isEqualTo("gold");
   assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Bob");assertThat(rows.getString(2)).isEqualTo("silver");
   assertThat(rows.next()).isFalse();
  }
  try(Connection c=DriverManager.getConnection(url,"sa","")){try(Statement st=c.createStatement()){
   st.execute("DELETE FROM fusion_d");st.execute("INSERT INTO fusion_b VALUES (2,'duplicate')");
  }}
  try(var scope=TenantContext.use("tenant")){assertThatThrownBy(()->service.loadModelFusion(plan)).hasMessageContaining("不唯一");}
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement();ResultSet rows=st.executeQuery("SELECT COUNT(*) FROM fusion_d")){
   assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();
  }
 }
 @Test void crossSourceFusionJoinsRowsAndRollsBackAmbiguousLatestValues()throws Exception {
  String options=";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  String aUrl="jdbc:h2:mem:fusion_cross_a"+options,bUrl="jdbc:h2:mem:fusion_cross_b"+options,dUrl="jdbc:h2:mem:fusion_cross_d"+options;
  try(Connection a=DriverManager.getConnection(aUrl,"sa","");Statement st=a.createStatement()){
   st.execute("CREATE TABLE fusion_a (id INT PRIMARY KEY, full_name VARCHAR(50))");st.execute("INSERT INTO fusion_a VALUES (1,' Alice '),(2,'Bob')");
  }
  try(Connection b=DriverManager.getConnection(bUrl,"sa","");Statement st=b.createStatement()){
   st.execute("CREATE TABLE fusion_b (a_id INT, category VARCHAR(30), updated_at INT)");st.execute("INSERT INTO fusion_b VALUES (1,'old',1),(1,'gold',2)");
  }
  try(Connection d=DriverManager.getConnection(dUrl,"sa","");Statement st=d.createStatement()){
   st.execute("CREATE TABLE fusion_d (id INT, display_name VARCHAR(50), category VARCHAR(30))");
  }
  var resolver=mock(DataSourceConnectionPropertyResolver.class);
  when(resolver.resolve(eq("tenant"),anyString())).thenAnswer(call->{String id=call.getArgument(1);return Map.of("dbType","mysql","jdbcUrl",switch(id){case "a"->aUrl;case "b"->bUrl;default->dUrl;},"username","sa");});
  var service=new MetadataRelationJdbcService(resolver);
  var plan=Map.<String,Object>of("targetSourceId","d","targetTable","fusion_d","baseEntityId","a",
   "sources",List.of(Map.of("entityId","a","sourceId","a","tableName","fusion_a"),Map.of("entityId","b","sourceId","b","tableName","fusion_b")),
   "joins",List.of(Map.of("leftEntityId","a","rightEntityId","b","joinType","LEFT","rightCardinality","MANY","dedupe","LATEST","orderName","updated_at","pairs",List.of(Map.of("leftName","id","rightName","a_id")))),
   "mappings",List.of(Map.of("sourceEntityId","a","sourceName","id","targetName","id","transform","DIRECT"),Map.of("sourceEntityId","a","sourceName","full_name","targetName","display_name","transform","TRIM"),Map.of("sourceEntityId","b","sourceName","category","targetName","category","transform","UPPER")));
  try(var scope=TenantContext.use("tenant")){assertThat(service.loadModelFusion(plan).get("insertedRows")).isEqualTo(2);}
  try(Connection d=DriverManager.getConnection(dUrl,"sa","");Statement st=d.createStatement();ResultSet rows=st.executeQuery("SELECT display_name,category FROM fusion_d ORDER BY id")){
   assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Alice");assertThat(rows.getString(2)).isEqualTo("GOLD");
   assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Bob");assertThat(rows.getString(2)).isNull();
  }
  try(Connection d=DriverManager.getConnection(dUrl,"sa","");Statement st=d.createStatement()){st.execute("DELETE FROM fusion_d");}
  try(Connection b=DriverManager.getConnection(bUrl,"sa","");Statement st=b.createStatement()){st.execute("INSERT INTO fusion_b VALUES (1,'tie',2)");}
  try(var scope=TenantContext.use("tenant")){assertThatThrownBy(()->service.loadModelFusion(plan)).hasMessageContaining("并列");}
  try(Connection d=DriverManager.getConnection(dUrl,"sa","");Statement st=d.createStatement();ResultSet rows=st.executeQuery("SELECT COUNT(*) FROM fusion_d")){
   assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();
  }
 }
 @Test void sameSourceLatestSelectsOneRecordAndRejectsTiedMaximum()throws Exception {
  String url="jdbc:h2:mem:fusion_latest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement()){
   st.execute("CREATE TABLE latest_a (id INT PRIMARY KEY)");st.execute("CREATE TABLE latest_b (a_id INT, label VARCHAR(20), updated_at INT)");st.execute("CREATE TABLE latest_d (id INT, label VARCHAR(20))");
   st.execute("INSERT INTO latest_a VALUES (1)");st.execute("INSERT INTO latest_b VALUES (1,'old',1),(1,'new',2)");
  }
  var resolver=mock(DataSourceConnectionPropertyResolver.class);
  when(resolver.resolve("tenant","source")).thenReturn(Map.of("dbType","mysql","jdbcUrl",url,"username","sa"));
  var plan=Map.<String,Object>of("targetSourceId","source","targetTable","latest_d","baseEntityId","a",
   "sources",List.of(Map.of("entityId","a","sourceId","source","tableName","latest_a"),Map.of("entityId","b","sourceId","source","tableName","latest_b")),
   "joins",List.of(Map.of("leftEntityId","a","rightEntityId","b","joinType","LEFT","rightCardinality","MANY","dedupe","LATEST","orderName","updated_at","pairs",List.of(Map.of("leftName","id","rightName","a_id")))),
   "mappings",List.of(Map.of("sourceEntityId","a","sourceName","id","targetName","id","transform","DIRECT"),Map.of("sourceEntityId","b","sourceName","label","targetName","label","transform","DIRECT")));
  var service=new MetadataRelationJdbcService(resolver);
  try(var scope=TenantContext.use("tenant")){assertThat(service.loadModelFusion(plan).get("insertedRows")).isEqualTo(1);}
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement();ResultSet rows=st.executeQuery("SELECT label FROM latest_d")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("new");}
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement()){st.execute("DELETE FROM latest_d");st.execute("INSERT INTO latest_b VALUES (1,'tie',2)");}
  try(var scope=TenantContext.use("tenant")){assertThatThrownBy(()->service.loadModelFusion(plan)).hasMessageContaining("并列");}
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement();ResultSet rows=st.executeQuery("SELECT COUNT(*) FROM latest_d")){assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();}
 }
 private static Map<String,Object> definition(){var d=new LinkedHashMap<String,Object>();d.put("sourceId","s");d.put("targetId","s");d.put("sourceTable","orders");d.put("targetTable","customers");d.put("sourceKind","TABLE");d.put("targetKind","TABLE");d.put("relationType","REFERENCE");d.put("mappings",List.of(Map.of("sourceName","a","targetName","a"),Map.of("sourceName","b","targetName","b")));d.put("conditions",List.of());return d;}
 private static Connection sampleConnection(List<? extends List<?>> rows)throws Exception {var c=mock(Connection.class);var m=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(m);when(m.getIdentifierQuoteString()).thenReturn("`");var s=mock(PreparedStatement.class);when(c.prepareStatement(anyString())).thenReturn(s);var r=mock(ResultSet.class);when(s.executeQuery()).thenReturn(r);var i=new AtomicInteger(-1);when(r.next()).thenAnswer(v->i.incrementAndGet()<rows.size());when(r.getObject(anyInt())).thenAnswer(v->rows.get(i.get()).get((int)v.getArgument(0)-1));return c;}
}

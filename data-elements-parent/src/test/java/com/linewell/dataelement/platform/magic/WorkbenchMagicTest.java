package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.exception.MagicExitException;
import org.ssssssss.script.exception.MagicScriptException;
import org.ssssssss.script.runtime.ExitValue;


class WorkbenchMagicTest {
 public static class Scope {
  String value="ALL";
  List<String> organizations=List.of("org-a");
  public String id(){return "police";}
  public Map<String,Object> resource(String code){return Map.of("userId","user","roleIds",List.of("role"),"organizationIds",organizations,"scope",value);}
  public Map<String,Object> session(){return Map.of("appId","app");}
 }
 static class Fixture {
  final JdbcTemplate jdbc;final SQLModule db;final StructuredTableReadMagicModule tableRead;final Scope scope=new Scope();
  int reads;
  final List<String> statements=new ArrayList<>();
  Fixture(int count) throws Exception {
   var source = new DriverManagerDataSource("jdbc:h2:mem:workbench_" + UUID.randomUUID()
       + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1") {
    @Override
    public Connection getConnection() throws SQLException {
     var connection = super.getConnection();
     return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
         (proxy, method, args) -> {
          if (method.getName().equals("prepareStatement")) {
           var sql = (String) args[0];
           if (sql.stripLeading().toLowerCase(Locale.ROOT).matches("(?s)(select|with).*")) {
            reads++;
            statements.add(sql);
           }
          }
          if (method.getName().equals("getMetaData")) {
           var metadata = connection.getMetaData();
           return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
               (ignored, operation, parameters) -> {
                if (operation.getName().equals("getURL")) return "jdbc:mysql://localhost/workbench_validation";
                try {
                 return operation.invoke(metadata, parameters);
                } catch (InvocationTargetException error) {
                 throw error.getCause();
                }
               });
          }
          try {
           return method.invoke(connection, args);
          } catch (InvocationTargetException error) {
           throw error.getCause();
          }
         });
    }
   };
   jdbc=new JdbcTemplate(source);
   tableRead=new StructuredTableReadMagicModule(jdbc);
   String ddl=java.nio.file.Files.readString(java.nio.file.Path.of("../db/baseline_ga/init.sql"));
   for(String table:List.of("db_datasource_t","db_table_t","metadata_table_collection_job_t","data_access_agg_task_t","nifi_pipeline_t","dwm_center_layer_source_t","sym_dict_t","rm_role_t","sym_application_t","rm_org_t","rm_user_t")) {
    String section=ddl.split("CREATE TABLE `"+table+"` \\(",2)[1].split("\\) ENGINE=",2)[0];var columns=new ArrayList<String>();
    for(String line:section.split("\\r?\\n")) {
     var matcher=java.util.regex.Pattern.compile("^\\s*`([^`]+)`\\s+(\\w+)(?:\\(([^)]+)\\))?.*").matcher(line);
     if(matcher.matches()) {
      String type=matcher.group(2).toLowerCase();String mapped=List.of("tinyint","int","bigint","smallint").contains(type)?"bigint":type.equals("datetime")?"timestamp":type.equals("decimal")?"decimal(20,4)":type.equals("longtext")?"clob":"varchar(4000)";
      columns.add("`"+matcher.group(1)+"` "+mapped);
     }
    }
    jdbc.execute("create table `"+table+"`("+String.join(",",columns)+")");
   }
   jdbc.execute("insert into rm_role_t(id,tenant_id,app_id,code_num,deleted,status) values('role','police','app','admin',0,0)");
   jdbc.execute("insert into db_datasource_t(tid,tenant_id,db_name,created_by,org_id,asset_status,is_del) values('owned','police','本部门库','user','org-a',2,0),('other','police','其他部门库','another','org-b',1,0),('foreign','foreign','其他租户库','user','org-a',2,0)");
   for(int i=0;i<count;i++)jdbc.update("insert into db_table_t(tid,datasource_id,tenant_id,table_name,table_name_cn,business_type,annotated,asset_status,field_count,field_governance_config,is_del) values(?,?,'police',?,'业务表','业务表',?,?,1,?,0)","table-"+i,i%2==0?"owned":"other","t_"+i,i%2,i%3==0?2:1,"{\"registrationSavedPhase\":\"business\",\"timestampField\":\"id\",\"fields\":[{\"columnName\":\"id\",\"standardField\":\"DATE\",\"dictionaryRelation\":{\"enabled\":true,\"sql\":\"select code\"}}]}");
   jdbc.execute("insert into db_table_t(tid,datasource_id,tenant_id,table_name,business_type,annotated,asset_status,is_del) values('secret','owned','foreign','secret','业务表',1,2,0)");
   var sources=new MagicDynamicDataSource();sources.setDefault(source);db=new SQLModule(sources);db.setDataSourceNode(sources.getDataSource());db.setResultProvider(new DefaultResultProvider("workbench-test"));
   db.setDialectAdapter(new DialectAdapter());
   var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");db.setColumnMapperProvider(mapper);db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);db.setNamedTableInterceptors(List.of());
   db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity req){// H2 does not retain bound values when it inlines a multiply referenced CTE.
    try {net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql.getSql());} catch(Exception error) {throw new AssertionError("SQL must also pass the runtime tenant parser",error);}
    // Render only fixture parameters here; production Magic always uses JDBC bindings.
    if(sql.getSql().contains("WITH accessible_sources")) {
     String statement=sql.getSql();for(Object value:sql.getParameters()) {assertNotNull(value,"missing named SQL binding");int index=statement.indexOf('?');String literal=value instanceof Number?value.toString():"'"+value.toString().replace("'","''")+"'";statement=statement.substring(0,index)+literal+statement.substring(index+1);}
     sql.setSql(statement);sql.setParameters(List.of());
    }
   }}));
  }
  Object script(String key,Map<String,Object> input,Map<String,Object> extra) throws Exception {
   var paths=Map.of("context","function/01.通用函数/17.工作台数据范围.ms","changes","function/01.通用函数/18.工作台变更查询.ms","overview","api/02.数据盘点/04.盘点统计/01.工作台统计/01.工作台概览.ms","items","api/02.数据盘点/04.盘点统计/01.工作台统计/02.工作台事项分页.ms","export","api/02.数据盘点/04.盘点统计/01.工作台统计/03.工作台导出.ms");
   String source=java.nio.file.Files.readString(java.nio.file.Path.of("../magic/"+paths.get(key))).split("================================",2)[1].replaceAll("(?m)^import[^\\n]*\\n","");
   var vars=new HashMap<String,Object>();vars.put("payload",input);vars.put("body",input);vars.put("tenantRuntime",scope);vars.put("tenantAccounts",scope);vars.put("dataScope",scope);vars.put("db",db);vars.put("tableSort",new TableSortMagicModule());
   vars.put("workbenchData", (java.util.function.Function<Object[], Object>) args -> {
    Object result;
    try {
     result = script("context", StructuredValues.objectMap(args[0]), Map.of());
    } catch (Exception error) {
     throw new IllegalStateException(error);
    }
    // The Magic API runtime propagates an exit from an imported function to its caller.
    if (result instanceof ExitValue exit) throw new MagicExitException(exit);
    return result;
   });
   vars.put("workbenchChanges",(java.util.function.Function<Object[],Object>)args->Map.of("available",true,"list",List.of(),"counts",Map.of("added",0,"modified",0,"deleted",0)));
   vars.put("tableRead",tableRead);
   vars.putAll(extra);
   var compiled=MagicScript.create("import java.util.Map\nimport java.util.List\nimport cn.hutool.json.JSONUtil\n"+source,null);
   return TenantContext.call(scope.id(), () -> compiled.execute(new MagicScriptContext(vars)));
  }

  void table(String id, String source, String name, String label, String comment, Integer marked,
      Integer columns, Timestamp updated, Integer status, int deleted) {
   jdbc.update("""
       insert into db_table_t(tid,datasource_id,tenant_id,table_name,table_name_cn,table_comment,
          annotated,field_count,updated_time,asset_status,is_del,business_type,business_type_reason)
       values (?,?,'police',?,?,?,?,?,?,?,?,'业务表','已核对')
       """, id, source, name, label, comment, marked, columns, updated, status, deleted);
  }

  Map<String, Object> items(Map<String, Object> body) throws Exception {
   return StructuredValues.objectMap(script("items", body, Map.of()));
  }

  Map<String, Object> sources(Map<String, Object> parameters) throws Exception {
   var body = new HashMap<>(parameters);
   body.put("kind", "sources");
   var response = script("items", body, Map.of());
   if (response instanceof ExitValue exit) fail("Source page returned " + Arrays.toString(exit.getValues()));
   return StructuredValues.objectMap(response);
  }

  void source(String id, String name, String creator, String organization, Integer assetStatus, int deleted) {
   jdbc.update("""
       insert into db_datasource_t(tid,tenant_id,db_name,created_by,org_id,asset_status,is_del)
       values (?,'police',?,?,?,?,?)
       """, id, name, creator, organization, assetStatus, deleted);
  }

  void collectionJob(String id, String source, String tenant, String status, Timestamp created, int deleted) {
   jdbc.update("""
       insert into metadata_table_collection_job_t(tid,tenant_id,datasource_id,status,created_time,is_del)
       values (?,?,?,?,?,?)
       """, id, tenant, source, status, created, deleted);
  }

  void governanceConfig(String id, String source, String tenant, String name, int annotated, int deleted,
      String config) {
   jdbc.update("""
       insert into db_table_t(tid,datasource_id,tenant_id,table_name,annotated,field_count,is_del,
          field_governance_config) values(?,?,?,?,?,1,?,?)
       """, id, source, tenant, name, annotated, deleted, config);
  }

  Map<String, Object> governance(String view) throws Exception {
   reads = 0;
   statements.clear();
   var response = StructuredValues.objectMap(script("overview",
       Map.of("section", "governance", "scope", view, "tenantId", "foreign"), Map.of()));
   assertEquals(2, reads, "governance should perform one role read and one bounded read");
   return StructuredValues.objectMap(response.get("governance"));
  }
 }

 @Test void tablePagesRankBeforeFilteringAndKeepLabelsAndStableWindows() throws Exception {
  var f = new Fixture(0);
  var old = Timestamp.valueOf("2026-06-01 10:10:10");
  var recent = Timestamp.valueOf("2026-06-01 10:10:11");
  var comment = "长文本".repeat(1500);
  f.table("raw-unmarked", "owned", "physical", "仅旧行可搜", null, 0, 80, recent, 2, 0);
  f.table("raw-smaller", "owned", "Physical", "仅旧行可搜", null, 1, 1, recent, 2, 0);
  f.table("raw-old", "owned", "PHYSICAL", "仅旧行可搜", null, 1, 2, old, 2, 0);
  f.table("raw-null", "owned", "physical", "仅旧行可搜", null, 1, 2, null, 2, 0);
  f.table("raw-a", "owned", "physical", "仅旧行可搜", null, 1, 2, recent, 2, 0);
  f.table("raw-z", "owned", "physical", "", comment, 1, 2, recent, 1, 0);
  f.table("other-source", "other", "physical", "另一来源", null, 0, 3, recent, 2, 0);
  f.table("literal", "owned", "wild_card", "", "", null, null, null, null, 0);
  f.table("wildcard", "owned", "wildXcard", null, null, null, 0, null, null, 0);

  var sorted = Map.<String, Object>of("scope", "all", "sortField", "updatedTime", "sortDir", "asc");
  assertEquals(List.of("other-source", "raw-z", "literal", "wildcard"), tableIds(f.items(sorted)));
  for (int page = 1; page <= 2; page++) {
   var window = new HashMap<>(sorted);
   window.put("pageNo", page);
   window.put("pageSize", 2);
   assertEquals(tableIds(f.items(sorted)).subList((page - 1) * 2, page * 2), tableIds(f.items(window)));
  }
  var selected = tableRows(f.items(Map.of("ids", List.of("raw-z")))).getFirst();
  assertEquals(Set.of("id", "datasourceId", "tableName", "tableNameCn", "tableComment", "tableLabel", "source",
      "businessType", "annotated", "assetStatus", "columnCount", "businessTypeReason", "createdTime", "updatedTime"),
      selected.keySet());
  assertEquals(comment, selected.get("tableLabel"));
  assertEquals(comment, selected.get("tableComment"));
  assertEquals("本部门库", selected.get("source"));
  assertEquals("已核对", selected.get("businessTypeReason"));
  assertEquals(recent, selected.get("updatedTime"));
  assertEquals("", tableRows(f.items(Map.of("ids", List.of("literal")))).getFirst().get("tableLabel"));
  assertEquals("wildXcard", tableRows(f.items(Map.of("ids", List.of("wildcard")))).getFirst().get("tableLabel"));

  assertEquals(Set.of("literal", "wildcard"), Set.copyOf(tableIds(f.items(Map.of("keyword", "wild_card")))));
  assertEquals(List.of("literal"), tableIds(f.items(Map.of("keyword", "wild\\_card"))));
  assertEquals(Set.of("raw-z", "literal", "wildcard"), Set.copyOf(tableIds(f.items(Map.of("keyword", "本部门库")))));
  assertEquals(List.of("other-source"), tableIds(f.items(Map.of("status", "registered"))));
  assertEquals(Set.of("literal", "wildcard", "other-source"), Set.copyOf(tableIds(f.items(Map.of("status", "pending")))));
  assertEquals(Set.of("raw-z", "literal", "wildcard"), Set.copyOf(tableIds(f.items(Map.of("status", "unregistered")))));
  var hiddenBySearch = f.items(Map.of("keyword", "仅旧行可搜"));
  assertEquals(0, ((Number) hiddenBySearch.get("total")).intValue());
  assertTrue(hiddenBySearch.containsKey("list"));
  assertNull(hiddenBySearch.get("list"));
  var filteredSelection = f.items(Map.of("ids", List.of("raw-z"), "status", "registered"));
  assertEquals(0, ((Number) filteredSelection.get("total")).intValue());
  assertNull(filteredSelection.get("list"));
  var latePage = f.items(Map.of("pageNo", 2, "pageSize", 4));
  assertEquals(4, ((Number) latePage.get("total")).intValue());
  assertEquals(List.of(), latePage.get("list"));
 }

 @Test void selectedTablesRequireEveryIdToBeAnAuthorizedRepresentative() throws Exception {
  var f = new Fixture(0);
  f.jdbc.execute("""
      insert into db_datasource_t(tid,tenant_id,db_name,created_by,org_id,is_del) values
        ('peer','police','本部门其他人','another','org-a',0),('retired','police','已删除','user','org-a',1)
      """);
  f.table("own-a", "owned", "one", null, null, 0, 0, null, 1, 0);
  f.table("own-b", "owned", "one", null, null, 1, 0, null, 1, 0);
  f.table("org-peer", "peer", "two", null, null, 1, 0, null, 1, 0);
  f.table("org-other", "other", "three", null, null, 1, 0, null, 1, 0);
  f.table("deleted-table", "owned", "four", null, null, 1, 0, null, 1, 1);
  f.table("deleted-source", "retired", "five", null, null, 1, 0, null, 1, 0);
  f.table("foreign-source", "foreign", "six", null, null, 1, 0, null, 1, 0);

  assertEquals(List.of("own-b"), tableIds(f.items(Map.of("scope", "mine", "tenantId", "foreign"))));
  for (String denied : List.of("own-a", "deleted-table", "deleted-source", "foreign-source", "secret", "missing")) {
   var rejected = f.script("items", Map.of("scope", "all", "ids", List.of("own-b", denied),
       "keyword", "would hide denied", "pageSize", 1, "pageNo", 2), Map.of());
   assertEquals(403, assertInstanceOf(ExitValue.class, rejected, denied).getValues()[0]);
  }
  for (Object invalid : List.of("own-b", List.of(), List.of("own-b", "own-b"),
      java.util.stream.IntStream.range(0, 101).mapToObj(i -> "id-" + i).toList())) {
   var rejected = f.script("items", Map.of("scope", "all", "ids", invalid), Map.of());
   assertEquals(400, assertInstanceOf(ExitValue.class, rejected).getValues()[0]);
  }
  f.jdbc.execute("update rm_role_t set code_num='employee'");
  f.scope.value = "ORG";
  var withinOrg = f.items(Map.of("scope", "mine", "ids", List.of("own-b", "org-peer")));
  assertEquals(Set.of("own-b", "org-peer"), Set.copyOf(tableIds(withinOrg)));
  assertDeniedSelection(f, "org-other");
  f.scope.organizations = List.of();
  assertDeniedSelection(f, "org-peer");
  var emptyOrg = f.items(Map.of("scope", "mine"));
  assertEquals(0, ((Number) emptyOrg.get("total")).intValue());
  assertNull(emptyOrg.get("list"));
  f.scope.value = "OWNER";
  assertDeniedSelection(f, "org-peer");
  assertEquals(List.of("own-b"), tableIds(f.items(Map.of("scope", "auto"))));
 }

 @Test void selectionRequiresCanonicalIdsEvenWhenDatabaseCollationMatchesAliases() throws Exception {
  var f = new Fixture(0);
  f.jdbc.execute("alter table db_table_t alter column tid varchar_ignorecase(4000)");
  f.table("mixed-case", "owned", "case_table", null, null, 1, 0, null, 1, 0);
  f.table("123", "owned", "numeric_table", null, null, 1, 0, null, 1, 0);
  assertEquals(Integer.valueOf(1), f.jdbc.queryForObject(
      "select count(*) from db_table_t where tid=?", Integer.class, "MIXED-CASE"));
  assertEquals(Set.of("mixed-case", "123"), Set.copyOf(tableIds(f.items(Map.of("ids", List.of("mixed-case", 123L))))));
  for (List<?> ids : List.of(List.of("MIXED-CASE", 123L), List.of("123", 123L))) {
   f.reads = 0;
   var rejected = f.script("items", Map.of("ids", ids), Map.of());
   assertEquals(403, assertInstanceOf(ExitValue.class, rejected).getValues()[0]);
   assertEquals(2, f.reads);
  }
 }

 @Test void sourceStagesUseAuthorizedRepresentativesAndLatestActiveCollectionJob() throws Exception {
  var fixture = new Fixture(0);
  var earlier = Timestamp.valueOf("2026-06-01 10:00:00");
  var latest = Timestamp.valueOf("2026-06-01 11:00:00");
  var later = Timestamp.valueOf("2026-06-01 12:00:00");
  fixture.source("pending-business", "日志待登记", "user", "org-a", null, 0);
  fixture.source("unmarked", "未标注", "user", "org-a", 2, 0);
  fixture.source("collected", "空库采集成功", "user", "org-a", 1, 0);
  fixture.source("failed", "最后一次失败", "user", "org-a", 1, 0);
  fixture.source("waiting", "未采集", "user", "org-a", 1, 0);
  fixture.source("retired", "已删除来源", "user", "org-a", 1, 1);
  fixture.table("dict-representative", "owned", "Dict", null, null, 1, 1, earlier, 1, 0);
  fixture.table("dict-discarded", "owned", "dict", null, null, 0, 100, latest, 1, 0);
  fixture.table("biz-completed", "owned", "biz", null, null, 1, 1, earlier, 2, 0);
  fixture.table("dict-pending", "other", "dict", null, null, 1, 1, earlier, 1, 0);
  fixture.table("log-pending", "pending-business", "events", null, null, 1, 1, earlier, 1, 0);
  fixture.table("not-marked", "unmarked", "data", null, null, 0, 1, earlier, 2, 0);
  fixture.table("table-deleted", "waiting", "data", null, null, 1, 1, earlier, 2, 1);
  fixture.governanceConfig("table-foreign", "waiting", "foreign", "data", 1, 0, "{}");

  fixture.jdbc.update("update db_table_t set business_type='字典表' where tid in (?,?,?)",
      "dict-representative", "dict-discarded", "dict-pending");
  fixture.jdbc.update("update db_table_t set business_type='日志表' where tid=?", "log-pending");
  // Historical stage matching strips literal spaces and accepts a matching marker in malformed text.
  fixture.jdbc.update("update db_table_t set field_governance_config=? where tid=?",
      "legacy text { \"registrationSavedPhase\" : \"dictionary\"", "dict-representative");
  fixture.jdbc.update("update db_table_t set field_governance_config=? where tid=?",
      "{\"registrationSavedPhase\"\t:\"dictionary\"}", "dict-pending");
  fixture.jdbc.update("update db_table_t set field_governance_config=? where tid=?",
      "{\"registrationSavedPhase\":\"dictionary\"}", "log-pending");
  fixture.jdbc.update("update db_datasource_t set connection_status='CONNECTED',created_time=?,updated_time=? where tid='owned'",
      earlier, latest);

  fixture.collectionJob("owned-job", "owned", "police", "RUNNING", latest, 0);
  fixture.collectionJob("collection-a", "collected", "police", "FAILED", latest, 0);
  fixture.collectionJob("collection-z", "collected", "police", "SUCCEEDED", latest, 0);
  fixture.collectionJob("failure-old", "failed", "police", "SUCCEEDED", earlier, 0);
  fixture.collectionJob("failure-current", "failed", "police", "FAILED", latest, 0);
  fixture.collectionJob("failure-deleted", "failed", "police", "SUCCEEDED", later, 1);
  fixture.collectionJob("failure-foreign", "failed", "foreign", "SUCCEEDED", later, 0);

  var rows = tableRows(fixture.sources(Map.of("scope", "all")));
  assertEquals(Set.of("owned", "other", "pending-business", "unmarked", "collected", "failed", "waiting"),
      Set.copyOf(rows.stream().map(row -> row.get("id")).toList()));
  var owned = rows.stream().filter(row -> "owned".equals(row.get("id"))).findFirst().orElseThrow();
  assertEquals(Set.of("id", "name", "source", "assetStatus", "connectionStatus", "createdTime", "updatedTime",
      "tableCount", "markedCount", "collectionStatus"), owned.keySet());
  assertEquals("本部门库", owned.get("name"));
  assertEquals(owned.get("name"), owned.get("source"));
  assertEquals(2, ((Number) owned.get("assetStatus")).intValue());
  assertEquals("CONNECTED", owned.get("connectionStatus"));
  assertEquals(earlier, owned.get("createdTime"));
  assertEquals(latest, owned.get("updatedTime"));
  assertEquals(2, ((Number) owned.get("tableCount")).intValue());
  assertEquals(2, ((Number) owned.get("markedCount")).intValue());
  assertEquals("RUNNING", owned.get("collectionStatus"));

  assertEquals(Set.of("failed", "waiting"), Set.copyOf(tableIds(fixture.sources(Map.of("stage", "explore")))));
  assertEquals(Set.of("other", "unmarked", "collected", "failed", "waiting"),
      Set.copyOf(tableIds(fixture.sources(Map.of("stage", "dictionary")))));
  assertEquals(Set.of("pending-business", "unmarked", "collected", "failed", "waiting"),
      Set.copyOf(tableIds(fixture.sources(Map.of("stage", "business")))));
  assertEquals(Set.of("other", "pending-business", "collected", "failed", "waiting"),
      Set.copyOf(tableIds(fixture.sources(Map.of("stage", "review")))));
  var emptySources = tableRows(fixture.sources(Map.of("stage", "explore")));
  for (var source : emptySources) {
   assertEquals(0, ((Number) source.get("tableCount")).intValue());
   assertEquals(0, ((Number) source.get("markedCount")).intValue());
   assertEquals("failed".equals(source.get("id")) ? "FAILED" : null, source.get("collectionStatus"));
  }
  var invalid = fixture.script("items", Map.of("kind", "sources", "stage", "invalid"), Map.of());
  assertEquals(400, assertInstanceOf(ExitValue.class, invalid).getValues()[0]);
 }

 @Test void sourcePagesHonorRoleScopeAndSortNullsAfterCountOrTimeValues() throws Exception {
  var fixture = new Fixture(0);
  var earlier = Timestamp.valueOf("2026-06-01 10:00:00");
  var latest = Timestamp.valueOf("2026-06-01 11:00:00");
  fixture.source("peer", "srcxc", "another", "org-a", 1, 0);
  fixture.source("spare", "srcxd", "another", "org-b", 1, 0);
  fixture.jdbc.update("update db_datasource_t set db_name='src_a' where tid='owned'");
  fixture.jdbc.update("update db_datasource_t set db_name='srcxb',updated_time=? where tid='other'", earlier);
  fixture.jdbc.update("update db_datasource_t set updated_time=? where tid in ('peer','spare')", latest);
  fixture.table("zero-marked", "owned", "one", null, null, null, 1, earlier, 1, 0);
  fixture.table("marked", "other", "one", null, null, 1, 1, earlier, 1, 0);
  fixture.table("unmarked", "other", "two", null, null, 0, 1, earlier, 1, 0);

  for (String field : List.of("tableCount", "markedCount")) {
   assertEquals(List.of("owned", "other", "peer", "spare"),
       tableIds(fixture.sources(Map.of("sortField", field, "sortDir", "asc"))));
   assertEquals(List.of("other", "owned", "peer", "spare"),
       tableIds(fixture.sources(Map.of("sortField", field, "sortDir", "desc"))));
   assertEquals(List.of("peer", "spare"),
       tableIds(fixture.sources(Map.of("sortField", field, "sortDir", "asc", "pageNo", 2, "pageSize", 2))));
  }
  assertEquals(List.of("other", "peer", "spare", "owned"),
      tableIds(fixture.sources(Map.of("sortField", "updatedTime", "sortDir", "asc"))));
  assertEquals(List.of("peer", "spare", "other", "owned"),
      tableIds(fixture.sources(Map.of())));
  var invalidField = assertThrows(MagicScriptException.class,
      () -> fixture.sources(Map.of("sortField", "nonexistent")));
  assertTrue(invalidField.getMessage().contains("不支持的排序字段：nonexistent"));
  var invalidDirection = assertThrows(MagicScriptException.class,
      () -> fixture.sources(Map.of("sortDir", "sideways")));
  assertTrue(invalidDirection.getMessage().contains("排序方向只能是 asc 或 desc"));
  assertEquals(List.of("spare", "peer", "other", "owned"),
      tableIds(fixture.sources(Map.of("keyword", "  src_  "))));
  assertEquals(List.of("owned"), tableIds(fixture.sources(Map.of("keyword", "src\\_"))));

  var missing = fixture.sources(Map.of("keyword", "missing"));
  assertEquals(0, ((Number) missing.get("total")).intValue());
  assertTrue(missing.containsKey("list"));
  assertNull(missing.get("list"));
  var beyondLastPage = fixture.sources(Map.of("pageNo", 3, "pageSize", 2));
  assertEquals(4, ((Number) beyondLastPage.get("total")).intValue());
  assertEquals(List.of(), beyondLastPage.get("list"));

  var mine = fixture.sources(Map.of("scope", "mine", "tenantId", "foreign", "userId", "another"));
  assertEquals(List.of("owned"), tableIds(mine));
  assertEquals("ALL", mine.get("scope"));
  assertEquals("mine", mine.get("mode"));
  fixture.jdbc.execute("update rm_role_t set code_num='employee'");
  fixture.scope.value = "ORG";
  var organization = fixture.sources(Map.of("scope", "auto", "tenantId", "foreign", "orgId", "org-b"));
  assertEquals(List.of("peer", "owned"), tableIds(organization));
  assertEquals("ORG", organization.get("scope"));
  assertEquals("mine", organization.get("mode"));
  fixture.scope.organizations = List.of();
  var emptyOrganization = fixture.sources(Map.of("scope", "mine"));
  assertEquals(0, ((Number) emptyOrganization.get("total")).intValue());
  assertNull(emptyOrganization.get("list"));
  fixture.scope.value = "OWNER";
  assertEquals(List.of("owned"), tableIds(fixture.sources(Map.of("scope", "auto", "userId", "another"))));
  fixture.reads = 0;
  var denied = fixture.script("items", Map.of("kind", "sources", "scope", "all"), Map.of());
  assertEquals(403, assertInstanceOf(ExitValue.class, denied).getValues()[0]);
  assertEquals(1, fixture.reads, "a denied global view should stop after the current application role lookup");
 }

 @Test void sourcePagesUseThreeReadsForTwentyAndHundredRows() throws Exception {
  for (int count : List.of(20, 100)) {
   var fixture = new Fixture(0);
   fixture.jdbc.update("""
       insert into db_datasource_t(tid,tenant_id,db_name,created_by,org_id,asset_status,is_del)
       select concat('source-',n),'police',concat('来源-',n),'user','org-a',1,0 from system_range(1,?) generated(n)
       """, count);
   fixture.jdbc.update("""
       insert into db_table_t(tid,datasource_id,tenant_id,table_name,business_type,annotated,is_del)
       select concat('table-',n),concat('source-',n),'police','dictionary','字典表',1,0
       from system_range(1,?) generated(n)
       """, count);
   fixture.jdbc.update("""
       insert into metadata_table_collection_job_t(tid,tenant_id,datasource_id,status,created_time,is_del)
       select concat('job-',n),'police',concat('source-',n),'SUCCEEDED',CURRENT_TIMESTAMP,0
       from system_range(1,?) generated(n)
       """, count);
   fixture.reads = 0;
   fixture.statements.clear();
   var response = fixture.sources(Map.of("scope", "all", "stage", "dictionary", "keyword", "来源-", "pageSize", count));
   assertEquals(count, ((Number) response.get("total")).intValue());
   var rows = tableRows(response);
   assertEquals(count, rows.size());
   assertTrue(rows.stream().allMatch(row -> ((Number) row.get("markedCount")).intValue() == 1
       && "SUCCEEDED".equals(row.get("collectionStatus"))));
   assertEquals(3, fixture.reads, "one role read, one source count and one source page");
   assertTrue(fixture.statements.stream().noneMatch(sql -> sql.contains("WITH accessible_sources")),
       "source paging should avoid the shared legacy query for unrelated workbench sections");
   System.out.println("WORKBENCH sources " + count + ": SQL=" + fixture.reads);
  }
 }

 private static void assertDeniedSelection(Fixture fixture, String deniedId) throws Exception {
  var rejected = fixture.script("items", Map.of("scope", "mine", "ids", List.of("own-b", deniedId)), Map.of());
  assertEquals(403, assertInstanceOf(ExitValue.class, rejected, deniedId).getValues()[0]);
 }

 private static List<Map<String, Object>> tableRows(Map<String, Object> result) {
  return ((List<?>) result.get("list")).stream().map(StructuredValues::objectMap).toList();
 }

 private static List<String> tableIds(Map<String, Object> result) {
  return tableRows(result).stream().map(row -> (String) row.get("id")).toList();
 }
 @Test void collectionChangesUseFixedBatchReadsForTwentyAndHundred() throws Exception {
  for(int count:List.of(20,100)) {
   var f=new Fixture(count);
   for(int parity=0;parity<2;parity++) {
    var details=new ArrayList<Map<String,String>>();for(int i=parity;i<count;i+=2)details.add(Map.of("tid","table-"+i));
    f.jdbc.update("insert into metadata_table_collection_job_t(tid,tenant_id,datasource_id,status,added_count,deleted_detail,is_del) values(?,'police',?,'SUCCEEDED',0,?,0)","job-"+parity,parity==0?"owned":"other",cn.hutool.json.JSONUtil.toJsonStr(details));
   }
   var context=f.script("context",Map.of("scope","all"),Map.of());f.reads=0;var extra=Map.<String,Object>of("context",context);
   var result=(Map<?,?>)f.script("changes",Map.of("pageSize",count,"sortField","name","sortDir","asc"),extra);
   assertEquals(true,result.get("available"));assertEquals(false,result.get("modifiedAvailable"));assertEquals(count,((List<?>)result.get("list")).size());assertEquals(3,f.reads);
   var descending=(Map<?,?>)f.script("changes",Map.of("pageSize",count,"sortField","name","sortDir","desc"),extra);
   var sorted=(List<?>)descending.get("list");for(int i=1;i<sorted.size();i++)assertTrue(((Map<?,?>)sorted.get(i-1)).get("tableName").toString().compareToIgnoreCase(((Map<?,?>)sorted.get(i)).get("tableName").toString())>=0);
   f.jdbc.execute("update metadata_table_collection_job_t set deleted_detail='invalid'");var failure=(Map<?,?>)f.script("changes",Map.of(),extra);assertEquals(false,failure.get("available"));assertTrue(failure.get("reason").toString().contains("差异记录"));
   System.out.println("WORKBENCH changes "+count+": SQL=3, no ES or per-row requests; malformed diff is explicit");
  }
 }
 @Test void collectionDiffsValidateEveryIdAndItsAuthorizedSource() throws Exception {
  var f=new Fixture(20);
  f.jdbc.update("insert into metadata_table_collection_job_t(tid,tenant_id,datasource_id,status,added_count,deleted_detail,is_del) values('job','police','owned','SUCCEEDED',3,?,0)","[{\"tid\":\"table-0\"},{\"tid\":\"secret\"},{\"tid\":\"table-1\"}]");
  var context=f.script("context",Map.of("scope","all"),Map.of());var extra=Map.<String,Object>of("context",context);
  var result=(Map<?,?>)f.script("changes",Map.of(),extra);var counts=(Map<?,?>)result.get("counts");
  assertEquals(true,result.get("available"));assertEquals(3,((Number)counts.get("added")).intValue());assertEquals(1,((Number)counts.get("deleted")).intValue());assertNull(counts.get("modified"));assertEquals(2,((List<?>)result.get("list")).size());
  var empty=(Map<?,?>)f.script("changes",Map.of("keyword","没有匹配项"),extra);assertEquals(0,((Number)empty.get("total")).intValue());
 }

 @Test void governanceRanksAuthorizedTablesBeforeFilteringConfigurations() throws Exception {
  var fixture = new Fixture(0);
  fixture.jdbc.execute("""
      insert into db_datasource_t(tid,tenant_id,db_name,created_by,org_id,is_del) values
        ('peer','police','本部门其他人','another','org-a',0),('retired','police','已删除','user','org-a',1)
      """);
  fixture.governanceConfig("mine", "owned", "police", "mine", 1, 0, """
      {registrationSavedPhase:'business', timestampField:'clock', fields:[
        {standardField:' DATE ',dictionaryRelation:{enabled:true,sql:'select code'}},
        {standardField:'   ',dictionaryRelation:{enabled:true,sql:'   '}},
        {standardField:'STRING',dictionaryRelation:{enabled:false,sql:'select code'}}
      ]}
      """);
  fixture.governanceConfig("org-peer", "peer", "police", "peer", 1, 0, """
      {"registrationSavedPhase":"business","timestampField":"  ","fields":[{"standardField":"DATE"}]}
      """);
  fixture.governanceConfig("other-org", "other", "police", "other", 1, 0, """
      {"registrationSavedPhase":"dictionary","timestampField":"unused","fields":[
        {"dictionaryRelation":{"enabled":true,"sql":"select code"}}
      ]}
      """);

  String malformed = "this is not a JSON object";
  fixture.governanceConfig("winner", "owned", "police", "DuP", 1, 0, "");
  fixture.governanceConfig("hidden-older", "owned", "police", "dup", 0, 0, malformed);
  fixture.governanceConfig("deleted-table", "owned", "police", "deleted", 1, 1, malformed);
  fixture.governanceConfig("deleted-source", "retired", "police", "retired", 1, 0, malformed);
  fixture.governanceConfig("foreign-source", "foreign", "police", "other-tenant-source", 1, 0, malformed);
  fixture.governanceConfig("foreign-table", "owned", "foreign", "other-tenant-table", 1, 0, malformed);

  assertGovernance(fixture.governance("all"), 3, 2, 50);
  assertGovernance(fixture.governance("mine"), 2, 1, 100);
  fixture.jdbc.execute("update rm_role_t set code_num='employee'");
  fixture.scope.value = "ORG";
  assertGovernance(fixture.governance("auto"), 3, 1, 50);
  fixture.scope.organizations = List.of();
  assertGovernance(fixture.governance("mine"), 0, 0, 0);
  fixture.scope.value = "OWNER";
  assertGovernance(fixture.governance("auto"), 2, 1, 100);

  fixture.governanceConfig("malformed-current", "owned", "police", "current", 1, 0, malformed);
  var unavailable = fixture.governance("auto");
  assertEquals(false, unavailable.get("available"));
  assertEquals("部分历史治理配置格式异常，暂不能准确统计", unavailable.get("reason"));
 }

 @Test void governanceAllowsTheCountLimitAndRejectsAnOverflowBeforeParsing() throws Exception {
  var fixture = new Fixture(0);
  fixture.jdbc.execute("""
      insert into db_table_t(tid,datasource_id,tenant_id,table_name,annotated,field_count,is_del,
         field_governance_config)
      select concat('cfg-',n),'owned','police',concat('table-',n),1,1,0,'{}'
      from system_range(1,20000) generated(n)
      """);
  assertGovernance(fixture.governance("all"), 0, 0, 0);

  fixture.governanceConfig("overflow", "owned", "police", "overflow", 1, 0, "this is not a JSON object");
  var unavailable = fixture.governance("all");
  assertEquals(false, unavailable.get("available"));
  assertEquals("治理配置超过单次安全统计范围，保留静态参考值", unavailable.get("reason"));
  assertEquals(0, ((Number) unavailable.get("standardFields")).intValue());
  assertEquals(0, ((Number) unavailable.get("dictionaryFields")).intValue());
  assertEquals(0, ((Number) unavailable.get("timestampRate")).intValue());
 }

 private static void assertGovernance(Map<String, Object> governance, int standardFields, int dictionaryFields,
     int timestampRate) {
  assertEquals(true, governance.get("available"));
  assertEquals(standardFields, ((Number) governance.get("standardFields")).intValue());
  assertEquals(dictionaryFields, ((Number) governance.get("dictionaryFields")).intValue());
  assertEquals(timestampRate, ((Number) governance.get("timestampRate")).intValue());
  assertFalse(governance.containsKey("reason"));
 }

 @Test void twentyAndHundredUseFixedReadsAndSameAuthorizedScope() throws Exception {
  Integer overviewReads=null,itemReads=null;
  for(int count:List.of(20,100)) {
   var f=new Fixture(count);f.reads=0;var all=(Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of());var totals=(Map<?,?>)all.get("counts");assertEquals(count,((Number)totals.get("tables")).intValue());assertEquals(true,all.get("canViewAll"));
   int reads=f.reads;if(overviewReads==null)overviewReads=reads;else assertEquals(overviewReads.intValue(),reads);
   var gov=(Map<?,?>)all.get("governance");assertEquals(true,gov.get("available"));assertEquals(count,((Number)gov.get("standardFields")).intValue());
   f.reads=0;var page=(Map<?,?>)f.script("items",Map.of("scope","all","kind","tables","pageSize",count),Map.of());assertEquals(count,((Number)page.get("total")).intValue());assertEquals(count,((List<?>)page.get("list")).size());
   if(itemReads==null)itemReads=f.reads;else assertEquals(itemReads.intValue(),f.reads);
   assertEquals(3,f.reads);
   var ids=java.util.stream.IntStream.range(0,count).mapToObj(i->"table-"+i).toList();f.reads=0;
   var selected=(Map<?,?>)f.script("items",Map.of("scope","all","ids",ids,"pageSize",count),Map.of());assertEquals(count,((List<?>)selected.get("list")).size());assertEquals(4,f.reads);
   f.reads=0;var report=(Map<?,?>)f.script("export",Map.of("scope","all","pageSize",count),Map.of());assertEquals(4,f.reads);assertEquals(count,((Number)((Map<?,?>)report.get("summary")).get("directoryCount")).intValue());
   var mine=(Map<?,?>)f.script("overview",Map.of("scope","mine"),Map.of());assertEquals(count/2,((Number)((Map<?,?>)mine.get("counts")).get("tables")).intValue());
   f.jdbc.execute("update rm_role_t set code_num='ywjz'");f.scope.value="ORG";
   var police=(Map<?,?>)f.script("overview",Map.of("scope","auto"),Map.of());assertEquals("mine",police.get("mode"));assertEquals(false,police.get("canViewAll"));assertEquals(count/2,((Number)((Map<?,?>)police.get("counts")).get("tables")).intValue());
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","all"),Map.of()));
   assertInstanceOf(ExitValue.class,f.script("items",Map.of("scope","mine","ids",List.of("table-0","table-1")),Map.of()));
   assertInstanceOf(ExitValue.class,f.script("items",Map.of("scope","mine","ids",List.of("table-0","secret")),Map.of()));
   f.scope.value="OWNER";var owner=(Map<?,?>)f.script("items",Map.of("scope","mine","kind","tables"),Map.of());assertEquals(count/2,((Number)owner.get("total")).intValue());
   f.jdbc.execute("update rm_role_t set app_id='other-app',code_num='admin'");
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","all"),Map.of()));
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","mine"),Map.of()));
   System.out.println("WORKBENCH "+count+": overview SQL="+overviewReads+", page SQL="+itemReads+", admin/ORG/OWNER/foreign IDs checked");
  }
 }
 @Test void pipelineStateWinsOverHistoricalTaskFailure() throws Exception {
  var f=new Fixture(20);
  f.jdbc.execute("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,pipeline_id,task_status,is_del) values('task','police','table-0','table-2','flow',2,0)");
  f.jdbc.execute("insert into nifi_pipeline_t(id,tenant_id,status,is_del) values('flow','police','RUNNING',0)");
  var running=(Map<?,?>)((Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of())).get("counts");
  assertEquals(1,((Number)running.get("accessRunningTasks")).intValue());assertEquals(0,((Number)running.get("accessExceptionTasks")).intValue());
  var taskItems=tableRows(f.items(Map.of("kind", "tasks", "scope", "all")));
  assertEquals(1, taskItems.size());
  assertEquals("task", taskItems.getFirst().get("id"));
  assertEquals("healthy", taskItems.getFirst().get("runtimeState"));
  assertEquals(Set.of("owned", "other"), Set.copyOf(tableIds(f.items(Map.of("kind", "sources", "scope", "all")))));
  f.jdbc.execute("update nifi_pipeline_t set status='STOPPED'");
  var stopped=(Map<?,?>)((Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of())).get("counts");
  assertEquals(0,((Number)stopped.get("accessRunningTasks")).intValue());assertEquals(0,((Number)stopped.get("accessExceptionTasks")).intValue());
 }
 @Test void independentSectionsKeepFixedQueriesAndSameAuthorizedResults() throws Exception {
  var queryCounts=Map.of("registration",3,"governance",2,"health",2,"tasks",2,"changes",1);
  for(int count:List.of(20,100)) {
   var f=new Fixture(count);
   var full=(Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of());
   for(String section:List.of("registration","governance","health","tasks","changes")) {
    f.reads=0;f.statements.clear();
    var result=(Map<?,?>)f.script("overview",Map.of("scope","all","section",section),Map.of());
    assertEquals(queryCounts.get(section).intValue(),f.reads,section);
    assertEquals(section,result.get("section"));assertEquals("all",result.get("mode"));
    assertEquals(true,result.get("canViewAll"));
    if(section.equals("registration")) {
     assertEquals(((Map<?,?>)full.get("counts")).get("tables"),((Map<?,?>)result.get("counts")).get("tables"));
     assertEquals(full.get("types"),result.get("types"));
    } else {
     if (section.equals("governance")) {
      assertEquals(full.get("governance"), result.get("governance"));
      assertTrue(f.statements.stream().noneMatch(sql -> sql.contains("WITH accessible_sources")),
          "governance should not build the legacy combined query");
     } else {
      assertTrue(f.statements.stream().noneMatch(sql->sql.contains("t.field_governance_config")),section+" must not parse registration JSON");
     }
     assertFalse(((Map<?,?>)result.get("counts")).containsKey("tables"));
    }
   }
   f.jdbc.execute("update rm_role_t set code_num='ywjz'");f.scope.value="ORG";
   var mine=(Map<?,?>)f.script("overview",Map.of("scope","auto","section","registration","tenantId","foreign"),Map.of());
   assertEquals(count/2,((Number)((Map<?,?>)mine.get("counts")).get("tables")).intValue());
   for(String section:queryCounts.keySet()) {
    var scoped=(Map<?,?>)f.script("overview",Map.of("scope","auto","section",section),Map.of());
    assertEquals("mine",scoped.get("mode"));assertEquals(false,scoped.get("canViewAll"));
    if(section.equals("health"))assertTrue(((List<?>)scoped.get("health")).stream().allMatch(row->"owned".equals(((Map<?,?>)row).get("id"))));
   }
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","all"),Map.of()));
   f.reads=0;assertInstanceOf(ExitValue.class,f.script("overview",Map.of("section","unknown"),Map.of()));assertEquals(0,f.reads);
   System.out.println("WORKBENCH sections "+count+": fixed reads="+queryCounts+", same scope, lightweight non-registration SQL");
  }
 }
}

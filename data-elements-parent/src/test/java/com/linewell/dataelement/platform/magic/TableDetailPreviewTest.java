package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
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
import org.ssssssss.script.runtime.ExitValue;

/** Executes the canonical detail-preview script, SQL scope checks and the Hive query boundary. */
public class TableDetailPreviewTest {
    private static class PreviewRejected extends RuntimeException {
        final int code;
        PreviewRejected(ExitValue exit){super(String.valueOf(exit.getValues()[1]));code=((Number)exit.getValues()[0]).intValue();}
    }
    public static class Tenant { public String id(){return "tenant-a";} }
    public static class Scope {
        boolean denied;
        public List<?> visibleDataSourcesFor(String permission, List<Map<String,Object>> sources) {
            assertEquals("MENU:2081000000000000002",permission);
            assertEquals("tenant-a",sources.getFirst().get("tenantId"));
            return denied ? List.of() : sources;
        }
    }
    public static class Connections {
        public Map<String,Object> read(String id) {
            return id.equals("source") ? Map.of("metadataAccessMode","server-managed-mrs","database","preview") : Map.of();
        }
    }
    public static String hiveText(String hex) {return new String(HexFormat.of().parseHex(hex),StandardCharsets.UTF_8);}
    public static class Hive {
        final List<String> queries=new ArrayList<>();final JdbcTemplate db;String failure;String failureSqlPrefix;
        boolean legacySyntaxOnly;
        Hive(JdbcTemplate db){this.db=db;}
        public List<Map<String,Object>> queryTable(String sql) {
            queries.add(sql);
            if(failure!=null && (failureSqlPrefix==null || sql.startsWith(failureSqlPrefix)))throw new IllegalStateException(failure);
            // HiveLexer.g 的 Identifier 不允许下划线开头；H2 接受该写法，须在 Hive 边界补充检查。
            // https://github.com/apache/hive/blob/rel/release-3.1.3/ql/src/java/org/apache/hadoop/hive/ql/parse/HiveLexer.g
            if(sql.matches("(?is).*\\bAS\\s+_[A-Za-z0-9_]+\\b.*"))
                throw new IllegalArgumentException("Hive ParseException: unquoted alias cannot start with underscore");
            if(legacySyntaxOnly && (sql.contains("NULLS LAST") || sql.matches("(?s).*LIMIT \\d+,.*")))
                throw new IllegalArgumentException("Hive 1.x does not support NULLS LAST or LIMIT offset, rows");
            if(sql.startsWith("DESCRIBE "))return List.of(
                Map.of("col_name","id","data_type","int","comment","物理编号"),
                Map.of("col_name","gender_code","data_type","string","comment","性别代码"),
                Map.of("col_name","# Partition Information","data_type","","comment",""),
                Map.of("col_name","id","data_type","int","comment","重复分区列"));
            // H2 executes SELECT/COUNT/order/limit. Adapt Hive's UTF-8 decoder and
            // STRING cast to their equivalent H2 expressions; no live Hive is implied.
            String executable=sql.replaceAll("decode\\(unhex\\('([a-f0-9]*)'\\),'UTF-8'\\)","HIVE_TEXT('$1')").replace(" AS STRING)"," AS VARCHAR)");
            return db.queryForList(executable).stream().map(row->{
                Map<String,Object> result=new LinkedHashMap<>();row.forEach((key,value)->result.put("preview.demo."+key,value));return result;
            }).toList();
        }
    }
    private static class Counter implements SQLInterceptor {
        int count; public void preHandle(BoundSql sql,RequestEntity request){count++;}
    }
    private static class Fixture {
        final JdbcTemplate db;final SQLModule module;final Counter counter=new Counter();
        final Scope scope=new Scope();final Hive hive;final String script;
        Fixture() throws Exception {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:detail_preview_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources=new MagicDynamicDataSource();sources.setDefault(ds);module=new SQLModule(sources);module.setDataSourceNode(sources.getDataSource());
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
            module.setDialectAdapter(dialects);module.setResultProvider(new DefaultResultProvider("detail-preview-test"));
            var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");
            module.setColumnMapperProvider(mapper);module.setSqlInterceptors(List.of(counter));module.setNamedTableInterceptors(List.of());module.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());module.setRowMapColumnMapper(v->v);
            db=new JdbcTemplate(ds);hive=new Hive(db);
            db.execute("CREATE TABLE db_datasource_t(tid VARCHAR,tenant_id VARCHAR,db_type VARCHAR,db_name VARCHAR,created_by VARCHAR,org_id VARCHAR,is_del INT)");
            db.execute("CREATE TABLE db_table_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,table_name VARCHAR,is_del INT)");
            db.execute("CREATE TABLE db_table_column_t(tid VARCHAR,tenant_id VARCHAR,table_id VARCHAR,column_name VARCHAR,column_comment VARCHAR,primary_key INT,is_del INT)");
            db.update("INSERT INTO db_datasource_t VALUES('source','tenant-a','hive','展示名称','owner','org',0),('foreign-source','tenant-b','hive','other','owner','org',0)");
            db.update("INSERT INTO db_table_t VALUES('table','tenant-a','source','preview.demo',0),('foreign-table','tenant-b','foreign-source','other.secret',0)");
            db.update("INSERT INTO db_table_column_t VALUES('a','tenant-a','table','id','编号',1,0),('foreign','tenant-b','table','gender_code','其他租户注释',0,0)");
            db.execute("CREATE SCHEMA preview");db.execute("CREATE TABLE preview.demo(id INT,gender_code VARCHAR)");
            db.execute("CREATE ALIAS HIVE_TEXT FOR 'com.linewell.dataelement.platform.magic.TableDetailPreviewTest.hiveText'");
            for(int i=1;i<=240;i++)db.update("INSERT INTO preview.demo VALUES(?,?)",i,i%2==0?"F":"M");
            script=CanonicalMagicSources.byId("bc30a3edb1f04bceaeee373ed5952d4a")
                .replaceAll("(?m)^import ('@/common/datasourceConnectionConfig'|jsons|tenantRuntime|dataScope|tableSort|hwhive|log|com.linewell.dataelement.elasticsearch.EsCommonService|com.linewell.dataelement.metautil.service.MetadataDataQueryService)[^\\r\\n]*\\r?\\n","");
        }
        Map<String,Object> run(Map<String,Object> body) {
            var context=new HashMap<String,Object>();context.put("db",module);context.put("tenantRuntime",new Tenant());context.put("dataScope",scope);context.put("hwhive",hive);context.put("connections",new Connections());context.put("tableSort",new TableSortMagicModule());context.put("body",body);
            context.put("log",org.slf4j.LoggerFactory.getLogger(TableDetailPreviewTest.class));
            var result=MagicScript.create("var datasourceConnectionConfig=(a,b,c,d)=>connections.read(b)\n"+script,null).execute(new MagicScriptContext(context));
            if(result instanceof ExitValue exit)throw new PreviewRejected(exit);
            return (Map<String,Object>)result;
        }
    }
    @Test void twentyAndOneHundredRowsUseFixedQueriesAndPreserveChineseLabels() throws Exception {
        var f=new Fixture();
        for(int size:List.of(20,100)) {
            f.counter.count=0;f.hive.queries.clear();
            var result=f.run(Map.of("tableId","table","pageNo",2,"pageSize",size,"sortField","id","sortDir","desc"));
            assertEquals(5,f.counter.count);assertEquals(3,f.hive.queries.size());
            var rows=(List<Map<String,Object>>)result.get("rows");
            assertEquals(size,rows.size());assertEquals(240-size,rows.getFirst().get("id"));
            assertEquals(240L,((Number)result.get("total")).longValue());
            assertEquals(Map.of("id","编号","gender_code","性别代码"),result.get("columnLabels"));
            assertTrue(f.hive.queries.getLast().endsWith("LIMIT "+size));
            assertEquals(List.of("id","gender_code"),result.get("columns"));
        }
        f.db.update("DELETE FROM preview.demo");assertEquals(List.of(),f.run(Map.of("tableId","table")).get("rows"));
    }
    @Test void filtersAreWhitelistedAndValuesCannotChangeSqlStructure() throws Exception {
        var f=new Fixture();
        var result=f.run(Map.of("tableId","table","columns",List.of("ID"),"conditions",List.of(Map.of("field","gender_code","op","eq","value","F"))));
        assertEquals(120L,((Number)result.get("total")).longValue());assertEquals(List.of("id"),result.get("columns"));
        result=f.run(Map.of("tableId","table","conditions",Map.of("gender_code","F")));
        assertEquals(120L,((Number)result.get("total")).longValue());
        String injection="中文'\\ OR 1=1 --\n";
        result=f.run(Map.of("tableId","table","conditions",List.of(Map.of("field","gender_code","op","eq","value",injection))));
        assertEquals(0L,((Number)result.get("total")).longValue());
        assertFalse(f.hive.queries.getLast().contains(injection));
        assertTrue(f.hive.queries.getLast().contains(HexFormat.of().formatHex(injection.getBytes(StandardCharsets.UTF_8))));
        for(var body:List.of(
            Map.<String,Object>of("tableId","table","sortField","id; DROP TABLE demo","sortDir","asc"),
            Map.<String,Object>of("tableId","table","columns",List.of("other.secret")),
            Map.<String,Object>of("tableId","table","conditions",List.of(Map.of("field","id","op","raw","value","1"))),
            Map.<String,Object>of("tableId","table","pageSize",101)))assertEquals(400,assertThrows(PreviewRejected.class,()->f.run(body)).code);
    }
    @Test void countUsesHiveCompatibleAliasInsteadOfH2OnlyUnderscoreAlias() throws Exception {
        var f = new Fixture();
        // 复现旧查询在 Hive 的词法错误，不能只用 H2 成功执行作为 Hive 语法验证。
        assertThrows(IllegalArgumentException.class,
                () -> f.hive.queryTable("SELECT COUNT(*) AS __preview_total FROM `preview`.`demo`"));
        for (int size : List.of(20, 100)) {
            f.hive.queries.clear(); f.counter.count = 0;
            var result = f.run(Map.of("tableId", "table", "pageSize", size));
            assertEquals(240L, ((Number) result.get("total")).longValue());
            assertEquals(size, ((List<?>) result.get("rows")).size());
            assertEquals(3, f.hive.queries.size()); assertEquals(5, f.counter.count);
        }
    }
    @Test void legacyHiveSupportsFirstPageAndOffsetPagesWithoutVersionSpecificSyntax() throws Exception {
        var f = new Fixture(); f.hive.legacySyntaxOnly = true;
        var first = f.run(Map.of("tableId","table","pageSize",20));
        assertEquals(20, ((List<?>)first.get("rows")).size());
        var next = f.run(Map.of("tableId","table","pageNo",2,"pageSize",100));
        assertEquals(101, ((List<Map<String,Object>>)next.get("rows")).getFirst().get("id"));
        assertEquals(240L, ((Number)next.get("total")).longValue());
        assertTrue(f.hive.queries.getLast().contains("ROW_NUMBER() OVER"));
    }
    @Test void missingRegisteredFieldsStillUsesOnePhysicalDescribe() throws Exception {
        var f = new Fixture(); f.db.update("DELETE FROM db_table_column_t");
        var result = f.run(Map.of("tableId","table","pageSize",20));
        assertEquals(20, ((List<?>)result.get("rows")).size());
        assertEquals(Map.of("id","物理编号","gender_code","性别代码"), result.get("columnLabels"));
        assertEquals(3, f.hive.queries.size());
    }
    @Test void scopeAndTenantRejectBeforeHiveAndCrossDatabaseNamesCannotEscape() throws Exception {
        var f=new Fixture();
        assertEquals(500,assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","foreign-table"))).code);assertEquals(0,f.hive.queries.size());
        f.scope.denied=true;assertEquals(403,assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","table"))).code);assertEquals(0,f.hive.queries.size());
        f.scope.denied=false;
        f.db.update("UPDATE db_table_t SET table_name='other.demo' WHERE tid='table'");
        assertEquals(400,assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","table"))).code);assertEquals(0,f.hive.queries.size());
    }
    @Test void groupedDictionaryReadsKeepTheExistingValueNameCountContract() throws Exception {
        var f=new Fixture();
        var result=f.run(Map.of("tableId","table","groupByColumn","gender_code"));
        var values=(List<Map<String,Object>>)result.get("groupedValues");
        assertEquals(2,values.size());assertEquals("F",values.getFirst().get("value"));assertNull(values.getFirst().get("name"));assertEquals(120L,((Number)values.getFirst().get("count")).longValue());
        assertEquals(2,f.hive.queries.size());
    }
    @Test void equalAndNullSortValuesHaveStableCrossPageOrder() throws Exception {
        var f=new Fixture();f.db.update("UPDATE preview.demo SET gender_code=NULL WHERE id=1");
        var small=f.run(Map.of("tableId","table","pageSize",20,"sortField","gender_code","sortDir","asc"));
        var large=f.run(Map.of("tableId","table","pageSize",100,"sortField","gender_code","sortDir","asc"));
        var rows=(List<Map<String,Object>>)large.get("rows");assertEquals(small.get("rows"),rows.subList(0,20));assertEquals(2,rows.getFirst().get("id"));
        var next=f.run(Map.of("tableId","table","pageNo",2,"pageSize",20,"sortField","gender_code","sortDir","asc"));assertEquals(rows.subList(20,40),next.get("rows"));
        var end=f.run(Map.of("tableId","table","pageNo",3,"pageSize",100,"sortField","gender_code","sortDir","asc"));
        assertEquals(1,((List<Map<String,Object>>)end.get("rows")).getLast().get("id"));
    }
    @Test void missingServerProfileHasAnActionableErrorWithoutLeakingItsPath() throws Exception {
        var f=new Fixture();f.hive.failure="Hive conf file not found: hiveclient.properties (dir=/private/hive)";
        var failure=assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","table")));assertEquals(503,failure.code);
        assertTrue(failure.getMessage().contains("hiveclient.properties"),failure.getMessage());assertFalse(failure.getMessage().contains("/private/hive"));
    }
    @Test void hiveFailureReportsTheStageWithoutReturningVendorDetails() throws Exception {
        var f = new Fixture(); f.hive.failure = "private vendor connection detail";
        var failure = assertThrows(PreviewRejected.class, () -> f.run(Map.of("tableId","table")));
        assertEquals(502, failure.code);
        assertTrue(failure.getMessage().contains("字段读取"));
        assertFalse(failure.getMessage().contains("private vendor"));
    }
    @Test void countFailureIsReportedWithoutInventingAZeroTotalOrExecutingDataQuery() throws Exception {
        var f = new Fixture(); f.hive.failure = "private count query failure"; f.hive.failureSqlPrefix = "SELECT COUNT(*)";
        var failure = assertThrows(PreviewRejected.class, () -> f.run(Map.of("tableId", "table")));
        assertEquals(502, failure.code); assertTrue(failure.getMessage().contains("总数统计"));
        assertFalse(failure.getMessage().contains("private count")); assertEquals(2, f.hive.queries.size());
    }
    @Test void detailPreviewDoesNotDependOnCountAndUsesBoundedLookaheadForTwentyAndHundredRows() throws Exception {
        var f=new Fixture();f.hive.legacySyntaxOnly=true;
        f.hive.failure="COUNT is unavailable on this Hive cluster";f.hive.failureSqlPrefix="SELECT COUNT(*)";
        for(int size:List.of(20,100)){
            f.hive.queries.clear();f.counter.count=0;
            var first=f.run(Map.of("tableId","table","pageSize",size,"countMode","none"));
            assertNull(first.get("total"));assertEquals(false,first.get("totalKnown"));assertEquals(true,first.get("hasMore"));
            assertEquals(size,((List<?>)first.get("rows")).size());assertEquals(2,f.hive.queries.size());assertEquals(5,f.counter.count);
            assertTrue(f.hive.queries.getLast().endsWith("LIMIT "+(size+1)));
            var next=f.run(Map.of("tableId","table","pageNo",2,"pageSize",size,"countMode","none"));
            assertEquals(size+1,((List<Map<String,Object>>)next.get("rows")).getFirst().get("id"));
            assertTrue(f.hive.queries.stream().noneMatch(sql->sql.startsWith("SELECT COUNT(*)")));
        }
        var last=f.run(Map.of("tableId","table","pageNo",12,"pageSize",20,"countMode","none"));
        assertEquals(20,((List<?>)last.get("rows")).size());assertEquals(false,last.get("hasMore"));assertNull(last.get("total"));
        var empty=f.run(Map.of("tableId","table","pageNo",13,"pageSize",20,"countMode","none"));
        assertEquals(List.of(),empty.get("rows"));assertEquals(false,empty.get("hasMore"));assertNull(empty.get("total"));
    }
    @Test void countFreePreviewKeepsFiltersSortAndScopeValidation() throws Exception {
        var f=new Fixture();
        var response=f.run(Map.of("tableId","table","countMode","none","sortField","id","sortDir","desc","conditions",Map.of("gender_code","F")));
        assertEquals(240,((List<Map<String,Object>>)response.get("rows")).getFirst().get("id"));
        assertEquals(List.of("id","gender_code"),response.get("columns"));
        f.hive.queries.clear();f.scope.denied=true;
        assertEquals(403,assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","table","countMode","none"))).code);
        assertEquals(0,f.hive.queries.size());f.scope.denied=false;
        assertEquals(500,assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","foreign-table","countMode","none"))).code);
        assertEquals(0,f.hive.queries.size());
        assertEquals(400,assertThrows(PreviewRejected.class,()->f.run(Map.of("tableId","table","countMode","unbounded"))).code);
        assertEquals(0,f.hive.queries.size());
    }
}

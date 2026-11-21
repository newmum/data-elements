package com.linewell.dataelement.feature.metadatagovernance.application;

import com.linewell.dataelement.utils.StructuredValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.ssssssss.magicapi.core.model.Parameter;
import org.ssssssss.magicapi.function.model.FunctionInfo;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MetadataCatalogPageTest {
    private JdbcTemplate jdbc;
    private MagicReads service;
    private int reads;
    private DataScopeAuthorizationService scopes;

    @BeforeEach
    void setUp() {
        setUpDatabase("MySQL", "jdbc:mysql://metadata-catalog-validation");
    }

    private void setUpDatabase(String mode, String dialectUrl) {
        var database = new DriverManagerDataSource("jdbc:h2:mem:metadata_catalog_page_" + mode
                + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(new DelegatingDataSource(database) {
            @Override
            public Connection getConnection() throws SQLException {
                var original = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (self, method, arguments) -> {
                    if ("getMetaData".equals(method.getName())) {
                        var metadata = original.getMetaData();
                        return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                (proxy, metaMethod, metaArguments) -> metaMethod.getName().equals("getURL")
                                        ? dialectUrl : metaMethod.invoke(metadata, metaArguments));
                    }
                    if ("prepareStatement".equals(method.getName()) && arguments[0] instanceof String sql && sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                        reads++;
                    }
                    try {
                        return method.invoke(original, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
            }
        });
        jdbc.execute("drop all objects");
        jdbc.execute("create table db_datasource_t (tid varchar(64), tenant_id varchar(64), is_del int, db_name varchar(100))");
        jdbc.execute("create table db_table_t (tid varchar(64), tenant_id varchar(64), datasource_id varchar(64), is_del int, table_name varchar(100), table_name_cn varchar(100), table_comment varchar(100), table_type varchar(30), field_count int, updated_time timestamp)");
        jdbc.execute("create table db_table_column_t (tid varchar(64), tenant_id varchar(64), table_id varchar(64), is_del int, column_name varchar(100), column_comment varchar(100), data_type varchar(30), column_type varchar(30), length int, nullable int, primary_key int, is_unique int, indexed int, ordinal_position int)");
        jdbc.execute("create table da_catalog_t (tid varchar(64), tenant_id varchar(64), is_del int, catalog_name varchar(100), catalog_name_en varchar(100), asset_desc varchar(100), source_table_id varchar(64), db_id varchar(64), updated_time timestamp)");
        jdbc.update("insert into db_datasource_t values ('s1','a',0,'A'),('s2','a',0,'B'),('s3','b',0,'C')");
        for (int i = 1; i <= 20; i++) jdbc.update("insert into db_table_t values (?, 'a', 's1', 0, ?, ?, '', 'TABLE', 1, null)", "t" + i, String.format("table_%02d", i), "表" + i);
        jdbc.update("insert into db_table_t values ('view','a','s2',0,'view_1','视图一','','VIEW',2,null)");
        jdbc.update("insert into db_table_t values ('hidden','b','s3',0,'hidden','隐私表','','TABLE',1,null)");
        jdbc.update("insert into db_table_column_t values ('c1','a','t1',0,'id','标识','int','int',11,0,1,1,1,1)");
        jdbc.update("insert into db_table_column_t values ('c2','b','t1',0,'secret','隐私','int','int',11,1,0,0,0,2)");
        jdbc.update("insert into da_catalog_t values ('cat1','a',0,'业务目录','biz','','t1','s1',null)");
        jdbc.update("insert into da_catalog_t values ('cat2','b',0,'隐私目录','secret','','t1','s1',null)");
        jdbc.execute("alter table db_datasource_t add created_by varchar(64) default 'owner'");
        jdbc.execute("alter table db_datasource_t add org_id varchar(64) default 'org1'");
        jdbc.execute("alter table db_table_t add record_count bigint default 0");
        jdbc.execute("alter table db_table_t add created_time timestamp");
        jdbc.execute("alter table db_table_column_t add updated_time timestamp");
        scopes = mock(DataScopeAuthorizationService.class);
        scope(DataScopeAuthorizationService.Scope.ALL,"owner",List.of());
        service = new MagicReads();
    }

    @Test
    void postgresqlPagingUsesTheSameBusinessQueryBudget() {
        setUpDatabase("PostgreSQL", "jdbc:postgresql://metadata-catalog-validation");
        for (int i = 21; i <= 100; i++) {
            jdbc.update("insert into db_table_t (tid,tenant_id,datasource_id,is_del,table_name,field_count) values (?,'a','s1',0,?,?)",
                    "t" + i, "validation_" + i, i);
        }
        TenantContext.call("a", () -> {
            for (int size : List.of(20, 100)) {
                reads = 0;
                var result = service.catalogTablesPage(List.of("s1"), null, null, 1, size, null, "fieldCount", "desc");
                assertThat(StructuredValues.objectRows(result.get("rows"))).hasSize(size);
                assertThat(reads).isEqualTo(3);
            }
            return null;
        });
    }

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void filtersAndPagesInSqlWithinTenantAndReadsOnlyOwnedColumns() {
        TenantContext.call("a", () -> {
            Map<String,Object> first = service.catalogTablesPage(List.of("s1"), "数据表", "", 1, 15);
            Map<String,Object> second = service.catalogTablesPage(List.of("s1"), "数据表", "", 2, 15);
            assertThat(first.get("total")).isEqualTo(20L);
            assertThat((List<?>) first.get("rows")).hasSize(15);
            assertThat((List<?>) second.get("rows")).hasSize(5);
            assertThat((List<?>) first.get("catalogs")).hasSize(1);
            assertThat(service.catalogTablesPage(List.of("s2"), "视图", "视图", 1, 15).get("total")).isEqualTo(1L);
            assertThat(service.catalogTablesPage(List.of(), null, "", 1, 15).get("total")).isEqualTo(0);
            assertThat(service.catalogTablesPage(null, null, "隐私", 1, 15).get("total")).isEqualTo(0L);
            assertThat(service.catalogTableColumns("t1")).hasSize(1);
            assertThat(service.catalogTableColumns("hidden")).isEmpty();
            return null;
        });
    }

    private void scope(DataScopeAuthorizationService.Scope value,String user,List<String> orgs) {
        when(scopes.currentScope(anyString())).thenReturn(new DataScopeAuthorizationService.ScopeDecision("DATA_SOURCE",value,user,orgs,List.of(),true));
    }

    @Test void sortsBeforePaginationWithConstantQueryCountAndRejectsInjectedOrder() {
        for (int i=21;i<=100;i++) jdbc.update("insert into db_table_t (tid,tenant_id,datasource_id,is_del,table_name,table_type,field_count) values (?,'a','s1',0,?,'TABLE',?)","t"+i,"table_"+i,i);
        TenantContext.call("a",()->{
            for(int size:List.of(20,100)) {
                reads = 0;
                var result=service.catalogTablesPage(List.of("s1"),null,"",1,size,null,"fieldCount","desc");
                var rows=StructuredValues.objectRows(result.get("rows"));
                assertThat(rows).hasSize(size);
                assertThat(rows.getFirst().get("field_count")).isEqualTo(100);
                assertThat(reads).isEqualTo(3);
            }
            assertThatThrownBy(()->service.catalogTablesPage(null,null,"",1,20,null,"t.tid;drop table db_table_t","asc")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->service.catalogTablesPage(null,null,"",1,20,null,"fieldCount","asc;--")).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void everyRequestedIdKeepsOwnerAndOrganizationScope() {
        TenantContext.call("a",()->{
            jdbc.update("update db_datasource_t set created_by='someone',org_id='org2' where tid='s2'");
            scope(DataScopeAuthorizationService.Scope.OWNER,"owner",List.of());
            assertThat(service.catalogTablesPage(List.of("s1","s2","s3"),null,"",1,100,null,"fieldCount","desc").get("total")).isEqualTo(20L);
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of("org2"));
            assertThat(service.catalogTablesPage(List.of("s1","s2","s3"),null,"",1,100,null,"tableName","asc").get("total")).isEqualTo(1L);
            assertThat(service.catalogTableColumns("t1","column_name","desc")).isEmpty();
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of());
            assertThat(service.catalogTablesPage(null,null,"",1,100).get("total")).isEqualTo(0L);
            return null;
        });
    }

    @Test void fieldsPageAndBatchStayBoundedAndDoNotReadPerParent() {
        for(int i=21;i<=100;i++)jdbc.update("insert into db_table_t (tid,tenant_id,datasource_id,is_del,table_name) values (?,'a','s1',0,?)","t"+i,"table_"+i);
        for(int i=1;i<=120;i++)jdbc.update("insert into db_table_column_t (tid,tenant_id,table_id,is_del,column_name,ordinal_position,length) values (?,'a','t1',0,?,?,?)","field-"+i,String.format("field_%03d",i),i,i);
        TenantContext.call("a",()->{
            reads = 0;
            var small=service.catalogColumnsPage("t1",1,20,"length","desc");
            assertThat(reads).isEqualTo(2);
            reads = 0;
            var large=service.catalogColumnsPage("t1",1,100,"length","desc");
            assertThat(reads).isEqualTo(2);
            assertThat(small.get("list")).isEqualTo(((List<?>)large.get("list")).subList(0,20));
            assertThat(large.get("total")).isEqualTo(121L);
            for(int size:List.of(20,100)){
                var ids=java.util.stream.IntStream.rangeClosed(1,size).mapToObj(i->"t"+i).toList();reads = 0;
                var result=service.catalogColumnsBatch(ids);assertThat(result).hasSize(size);
                assertThat(result.get("t1")).hasSize(121);
                assertThat(reads).isEqualTo(2);
            }
            reads = 0;
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("t1","hidden"))).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("包含不存在或无权读取的数据表");
            assertThat(reads).isEqualTo(1);
            assertThat(service.catalogColumnsBatch(List.of("t1","t1"))).hasSize(1);
            assertThatThrownBy(()->service.catalogColumnsBatch(java.util.Collections.nCopies(101,"t1"))).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void associatedApisSortCountsBeforePagingAndKeepThreeReadsForTwentyAndOneHundred() {
        jdbc.execute("alter table da_catalog_t add created_by varchar(64) default 'owner'");
        jdbc.execute("alter table da_catalog_t add org_id varchar(64) default 'org1'");
        jdbc.execute("create table api_info_t(tid varchar(64),tenant_id varchar(64),catalog_id varchar(64),service_name varchar(64),created_by varchar(64),org_id varchar(64),updated_time timestamp,is_del int)");
        jdbc.execute("create table api_param_t(api_id varchar(64),tenant_id varchar(64),is_del int)");
        for(int i=0;i<120;i++)jdbc.update("insert into api_info_t values(?,'a','cat1',?,'owner','org1',NULL,0)","api-"+i,String.format("API %03d",i));
        jdbc.update("insert into api_info_t values('secret','b','cat1','私密','other','org1',NULL,0),('unrelated','a','other','无关','owner','org1',NULL,0),('outside','a','cat1','无权','other','org2',NULL,0)");
        jdbc.update("insert into api_param_t values('api-100','a',0),('api-100','a',0),('api-100','b',0),('api-1','a',0)");
        TenantContext.call("a",()->{
            scope(DataScopeAuthorizationService.Scope.OWNER,"owner",List.of());
            java.util.List<?> small=null;
            for(int size:List.of(20,100)){
                reads = 0;var result=service.catalogServices("cat1",1,size,"count","desc");
                assertThat(result.get("total")).isEqualTo(120L);
                var list=StructuredValues.objectRows(result.get("list"));
                assertThat(list).hasSize(size);assertThat(list.getFirst().get("id")).isEqualTo("api-100");assertThat(list.getFirst().get("count")).isEqualTo(2L);
                if(small==null)small=list;else assertThat(list.subList(0,20)).isEqualTo(small);
                assertThat(reads).isEqualTo(3);
            }
            assertThatThrownBy(()->service.catalogServices("cat2",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->service.catalogServices("cat1",1,20,"sql injection","asc")).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void associatedResourceUsesOneAuthorityAndChecksTheCatalogAndTargetSource() {
        jdbc.execute("alter table da_catalog_t add target_table_id varchar(64)");
        jdbc.execute("alter table da_catalog_t add created_by varchar(64) default 'owner'");
        jdbc.execute("alter table da_catalog_t add org_id varchar(64) default 'org1'");
        jdbc.execute("alter table db_datasource_t add db_type varchar(32) default 'mysql'");
        jdbc.execute("alter table db_datasource_t add app_id varchar(64)");
        jdbc.execute("create table sym_application_t(tid varchar(64),tenant_id varchar(64),app_name varchar(64),is_del int)");
        jdbc.update("update da_catalog_t set target_table_id='t1' where tid='cat1'");
        TenantContext.call("a",()->{
            for(int size:List.of(20,100)){
                reads = 0;var result=service.catalogResources("cat1",1,size,"fieldCount","desc");
                assertThat(result.get("total")).isEqualTo(1);assertThat((List<?>)result.get("list")).hasSize(1);
                assertThat(reads).isEqualTo(2);
            }
            assertThatThrownBy(()->service.catalogResources("cat2",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            jdbc.update("update da_catalog_t set target_table_id='hidden' where tid='cat1'");
            assertThatThrownBy(()->service.catalogResources("cat1",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            jdbc.update("update da_catalog_t set target_table_id='t1' where tid='cat1'");
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of());
            assertThatThrownBy(()->service.catalogResources("cat1",1,20,null,null)).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test void mixedParentBatchCannotExpandOwnerOrOrganizationScope() {
        jdbc.update("update db_datasource_t set created_by='someone',org_id='org2' where tid='s2'");
        TenantContext.call("a",()->{
            scope(DataScopeAuthorizationService.Scope.OWNER,"owner",List.of());
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("t1","view"))).isInstanceOf(IllegalArgumentException.class);
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of("org2"));
            assertThat(service.catalogColumnsPage("t1",1,100,"column_name","asc").get("total")).isEqualTo(0L);
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("view","t1"))).isInstanceOf(IllegalArgumentException.class);
            scope(DataScopeAuthorizationService.Scope.ORG,"owner",List.of());
            assertThatThrownBy(()->service.catalogColumnsBatch(List.of("view"))).isInstanceOf(IllegalArgumentException.class);
            return null;
        });
    }

    @Test
    void registrationRetainsMvcBindingForUnifiedReaders() {
        TenantContext.call("a", () -> {
            var missing = service.run("07.元数据目录/01.目录关联服务分页.ms", Map.of(), null);
            assertThat(missing.getStatusCode().value()).isEqualTo(200);
            assertThat(((CommonResponse<?>) missing.getBody()).getCode()).isEqualTo(20001);
            var wrongPage = service.run("07.元数据目录/03.采集表分页.ms", Map.of("pageSize","wrong"), null);
            assertThat(((CommonResponse<?>) wrongPage.getBody()).getCode()).isEqualTo(20002);
            var input = Map.<String,Object>of("sourceIds", List.of("s1", "s2"), "pageSize", 20);
            var get = service.run("07.元数据目录/03.采集表分页.ms", input, null);
            assertThat(get.getStatusCode().value()).isEqualTo(200);
            assertThat(((Map<?,?>) get.getBody()).get("code")).isEqualTo(0);
            var malformed = service.run("08.采集字段/03.批量读取采集字段.ms", Map.of(), "{".getBytes(StandardCharsets.UTF_8));
            assertThat(((CommonResponse<?>) malformed.getBody()).getMsg()).startsWith("JSON parse error:");
            var one = service.run("08.采集字段/03.批量读取采集字段.ms", Map.of(),
                    "{\"tableIds\":\"t1\"}".getBytes(StandardCharsets.UTF_8));
            assertThat(((Map<?,?>) one.getBody()).get("code")).isEqualTo(0);
            return null;
        });
    }

    private final class MagicReads {
        private static final Pattern MODULE = Pattern.compile("(?m)^import (tenantRuntime|dataScope|tableRead|tableSort)\\R");
        private static final Pattern FUNCTION = Pattern.compile("(?m)^import ['\"]@/common/([^'\"]+)['\"] as (\\w+)\\R");
        private final Map<String,Script> queries = new LinkedHashMap<>();
        private final Map<String,Script> endpoints = new LinkedHashMap<>();
        private final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        private final TableSortMagicModule tableSort = new TableSortMagicModule();

        MagicReads() {
            try {
                // The editor format preserves optional fields; the runtime binds only FunctionInfo properties.
                var metadataMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                var magic = Path.of(System.getProperty("magic.sourceRoot", "../magic"));
                if (!Files.isDirectory(magic)) magic = Path.of("magic");
                try (var files = Files.list(magic.resolve("function/01.通用函数"))) {
                    for (var file : files.filter(p -> p.getFileName().toString().endsWith(".ms")).toList()) {
                        var split = Files.readString(file).split("================================\\R", 2);
                        var header = metadataMapper.readTree(split[0]);
                        var path = header.path("path").asText();
                        if (!path.startsWith("/metadataCatalog") && !path.equals("/metadataColumnsScope")) continue;
                        var metadata = metadataMapper.treeToValue(header, FunctionInfo.class);
                        var names = metadata.getParameters().stream().map(Parameter::getName).toList();
                        var source = split.length == 2 ? split[1] : header.path("script").asText();
                        queries.put(path.substring(1), compile(source, names));
                    }
                }
                var api = magic.resolve("api/04.数据治理");
                for (var group : List.of("07.元数据目录", "08.采集字段")) {
                    try (var files = Files.list(api.resolve(group))) {
                        for (var file : files.filter(p -> p.toString().endsWith(".ms")).toList()) {
                            var body = Files.readString(file).split("================================\\R", 2)[1];
                            endpoints.put(group + "/" + file.getFileName(), compile(body, List.of()));
                        }
                    }
                }
            } catch (Exception failure) {
                throw new IllegalStateException("无法读取 B112 目录查询", failure);
            }
        }

        private Script compile(String script, List<String> arguments) {
            MagicScript.create(script, null).compile();
            var imports = new LinkedHashMap<String,String>();
            var matcher = FUNCTION.matcher(script);
            while (matcher.find()) imports.put(matcher.group(2), matcher.group(1));
            String stripped = MODULE.matcher(matcher.replaceAll("")).replaceAll("")
                    .replaceFirst("(?m)^import com\\.fasterxml\\.jackson\\.databind\\.ObjectMapper as mapper\\R", "");
            return new Script(MagicScript.create(stripped, null), imports, arguments);
        }

        private Object execute(Script source, Map<String,Object> values) {
            var context = new LinkedHashMap<>(values);
            source.functions().forEach((alias,name) -> context.put(alias, (MagicScriptLambdaFunction) (ignored, arguments) -> {
                var nested = queries.get(name);
                var variables = new LinkedHashMap<>(values);
                for (int i = 0; i < nested.args().size(); ++i) variables.put(nested.args().get(i), arguments[i]);
                return execute(nested, variables);
            }));
            return source.script().execute(new MagicScriptContext(context));
        }

        private ResponseEntity<?> run(String endpoint, Map<String,?> query, Object body) {
            var request = new MockHttpServletRequest();
            query.forEach((key,value) -> {
                if (value instanceof List<?> list) request.addParameter(key, list.stream().map(Object::toString).toArray(String[]::new));
                else if (value != null) request.addParameter(key, value.toString());
            });
            if (body instanceof byte[] raw) {
                request.setContent(raw);
                var cached = new ContentCachingRequestWrapper(request, 65536);
                try {
                    cached.getInputStream().readAllBytes();
                } catch (java.io.IOException impossible) {
                    throw new IllegalStateException(impossible);
                }
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(cached));
            } else {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            }
            var vars = new LinkedHashMap<String,Object>();
            vars.put("tenantRuntime", new TenantMagicModule(null));
            vars.put("tableRead", tableRead);
            vars.put("tableSort", tableSort);
            vars.put("dataScope", new DataScopeMagicModule(scopes));
            vars.put("mapper", new ObjectMapper());
            vars.put("body", body);
            try {
                return (ResponseEntity<?>) execute(endpoints.get(endpoint), vars);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        @SuppressWarnings("unchecked")
        <T> T data(String endpoint, Map<String,?> query, Object body) {
            var value = run(endpoint, query, body).getBody();
            if (value instanceof CommonResponse<?> error) throw new IllegalArgumentException(error.getMsg());
            assertThat(((Map<?,?>) value).get("code")).isEqualTo(0);
            return (T) ((Map<?,?>) value).get("data");
        }

        private Map<String,Object> query(Object... pairs) {
            var map = new LinkedHashMap<String,Object>();
            for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
            return map;
        }

        Map<String,Object> catalogTablesPage(List<String> ids, String kind, String keyword, int page, int size) {
            return catalogTablesPage(ids, kind, keyword, page, size, null, null, null);
        }

        Map<String,Object> catalogTablesPage(List<String> ids, String kind, String keyword, int page, int size,
                                             String id, String sort, String direction) {
            return data("07.元数据目录/03.采集表分页.ms", query("sourceIds",ids,"kind",kind,"keyword",keyword,
                    "pageNo",page,"pageSize",size,"tableId",id,"sortField",sort,"sortDir",direction), null);
        }

        List<Map<String,Object>> catalogTableColumns(String id) {
            return catalogTableColumns(id, null, null);
        }

        List<Map<String,Object>> catalogTableColumns(String id, String sort, String direction) {
            return data("08.采集字段/01.采集字段清单.ms", query("tableId",id,"sortField",sort,"sortDir",direction), null);
        }

        Map<String,Object> catalogColumnsPage(String id, int page, int size, String sort, String direction) {
            return data("08.采集字段/02.采集字段分页.ms", query("tableId",id,"page",page,"size",size,"sortField",sort,"sortDir",direction), null);
        }

        Map<String,List<Map<String,Object>>> catalogColumnsBatch(List<String> ids) {
            return data("08.采集字段/03.批量读取采集字段.ms", Map.of(), Map.of("tableIds", ids));
        }

        Map<String,Object> catalogServices(String id, int page, int size, String sort, String direction) {
            return data("07.元数据目录/01.目录关联服务分页.ms", query("catalogId",id,"pageNo",page,"pageSize",size,"sortField",sort,"sortDir",direction), null);
        }

        Map<String,Object> catalogResources(String id, int page, int size, String sort, String direction) {
            return data("07.元数据目录/02.目录关联表资源.ms", query("catalogId",id,"pageNo",page,"pageSize",size,"sortField",sort,"sortDir",direction), null);
        }
    }

    private record Script(MagicScript script, Map<String,String> functions, List<String> args) {}
}

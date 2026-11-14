package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;

import com.linewell.dataelement.platform.magic.module.MagicResourceLinkModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class ProxyDatasourceServiceMagicTest {
    private static final String LIST_RESOURCE = "510fe82cba4a42248abd1faefc515441";
    private static final String DETAIL_RESOURCE = "1cc3266227a24804b9ba7c3a9e00d724";
    private static final String GROUP_RESOURCE = "5d26a3dfa99b49edbe32d53bdfca061e";
    private static final String SAVE_RESOURCE = "d5a7019f5e4a4a92a34543c1f5290005";

    public static class ScopeRuntime {
        String role = "ALL", tenant = "tenant-a", org = "org-a";
        List<String> scopeOrgs = List.of("org-a");
        boolean parentReadable = true;

        public String id(){if(tenant==null)throw new IllegalStateException("租户缺失");return tenant;}
        public Map<String, Object> dataSource() {
            return Map.of("scope", role, "userId", "user-a", "organizationIds", scopeOrgs);
        }
        public Map<String, Object> currentUser() {
            return Map.of("id", "user-a", "orgId", org);
        }
        public boolean canReadDataSource(Map<String,Object> row){
            return Objects.equals(row.getOrDefault("tenantId",row.get("tenant_id")),tenant)
                && (role.equals("ALL") || role.equals("ORG") && scopeOrgs.stream()
                        .anyMatch(orgId -> orgId.equals(row.getOrDefault("orgId", row.get("org_id"))))
                    || role.equals("OWNER") && Objects.equals(row.getOrDefault("createdBy",row.get("created_by")),"user-a"));
        }
        public boolean canReadDataSourceFor(String menu, Map<String, Object> row) {
            assertThat(menu).isEqualTo("MENU:2081000000000000002");
            return parentReadable && canReadDataSource(row);
        }
    }

    static class ControlJdbc extends JdbcTemplate {
        int reads;

        ControlJdbc() {
            super(new DriverManagerDataSource("jdbc:h2:mem:service_control_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
            execute("CREATE TABLE api_file_t(tid VARCHAR PRIMARY KEY, api_id VARCHAR, is_del INT)");
        }

        @Override
        public <T> T query(String sql, PreparedStatementSetter setter, ResultSetExtractor<T> extractor) {
            reads++;
            return super.query(sql, setter, extractor);
        }
    }

    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture data=new DatasourceCompletionMagicTest.Fixture(0);
        final ScopeRuntime scope=new ScopeRuntime();
        final TaskCategoryWarehouseScopeMagicTest.Counter counter=new TaskCategoryWarehouseScopeMagicTest.Counter();
        final StructuredTableReadMagicModule tableRead = TaskRuntimeMagicFixture.structuredReads(data.jdbc, counter::record);
        final ControlJdbc control = new ControlJdbc();
        final MagicResourceLinkModule links = new MagicResourceLinkModule(control);

        Fixture() throws Exception {
            for(String column:List.of("org_id varchar(64)","created_by varchar(64)","pool_cfg varchar(6000)"))data.jdbc.execute("alter table db_datasource_t add "+column);
            data.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,org_id,created_by,pool_cfg) values('source-a','tenant-a',0,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('source-b','tenant-a',0,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('foreign','tenant-b',0,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('department','tenant-a',0,'org-b','user-b','{\"accessMode\":\"proxy\"}'),('deleted','tenant-a',1,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('direct','tenant-a',0,'org-a','user-a','{\"accessMode\":\"explore\"}')");
            Path root = Files.isDirectory(Path.of("magic")) ? Path.of("") : Path.of("..");
            String ddl = Files.readString(root.resolve("db/baseline_ga/init.sql"));
            for(String name:List.of("api_info_t","api_param_t","da_prop_t","sym_dict_t")) {
                String table=ddl.substring(ddl.indexOf("CREATE TABLE `"+name+"`"));table=table.substring(0,table.indexOf("\n)"));
                var matcher=Pattern.compile("(?m)^  `([^`]+)` ([^ ,]+)").matcher(table);List<String> columns=new ArrayList<>();
                while(matcher.find()){
                    String field=matcher.group(1),type=matcher.group(2).toLowerCase();
                    columns.add(field+" "+(type.contains("int")?"bigint":type.contains("decimal")?"decimal(38,10)":type.contains("time")?"timestamp":"varchar(6000)"));
                }
                data.jdbc.execute("create table "+name+"("+String.join(",",columns)+")");
            }
            data.db.setSqlInterceptors(List.of(counter));
        }
        Object run(String operation, Map<String, Object> body) throws Exception {
            String resource = switch (operation) {
                case "list" -> LIST_RESOURCE;
                case "detail" -> DETAIL_RESOURCE;
                case "groups" -> GROUP_RESOURCE;
                case "save" -> SAVE_RESOURCE;
                default -> throw new IllegalArgumentException(operation);
            };
            String source = CanonicalMagicSources.byId(resource)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.|org\\.)[^\\r\\n]*\\R", "");
            counter.reads = 0;
            counter.sql.clear();
            control.reads = 0;
            var bindings = Map.of("db", data.db, "tenantRuntime", scope, "dataScope", scope,
                    "magicResourceLink", links, "tableSort", new TableSortMagicModule(), "tableRead", tableRead,
                    "body", body, "parameter", Map.of());
            String script = "var me = () => tenantRuntime.currentUser()\n" + source;
            try (var ignored = TenantContext.use(scope.tenant == null ? "tenant-a" : scope.tenant)) {
                return MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
            }
        }
        Map<String,Object> createBody(String source){return new LinkedHashMap<>(Map.of("name","代理验证服务","uri","/validation/proxy-service","datasourceId",source,"tenantId","tenant-b","orgId","org-b"));}
    }
    @Test void mixedRequestAndResponseParametersRoundTripWithConstantReadsAndTenantIsolation() throws Exception {
        for (int count : List.of(20, 100)) {
            var f = new Fixture();
            var body = f.createBody("source-a");
            List<Map<String,Object>> params = new ArrayList<>();
            for (int i = 0; i < count; i++) params.add(Map.of("paramName", "p" + i, "paramType", "string",
                "paramScope", i % 2 == 0 ? "request" : "response", "showcaseValue", "v" + i));
            body.put("publishParams", params);
            body.put("extendInfo", new LinkedHashMap<>(Map.of("requestExample", "{\"q\":1}", "responseExample", "{\"ok\":true}",
                "exampleInitialization", Map.of("request", true, "response", true))));
            var saved = (Map<?,?>) f.run("save", body);
            String id = saved.get("id").toString();
            body.put("id", id);
            assertThat(f.run("save", body)).isInstanceOf(Map.class);
            assertThat(f.counter.reads).isEqualTo(3);
            assertThat(f.data.jdbc.queryForMap("SELECT tenant_id,org_id,created_by FROM api_info_t WHERE tid=?", id))
                    .containsEntry("tenant_id", "tenant-a").containsEntry("org_id", "org-a").containsEntry("created_by", "user-a");
            f.data.jdbc.update("insert into api_param_t(tid,api_id,tenant_id,param_name,param_scope,is_del) values('foreign-param',?,'tenant-b','hidden','response',0)", id);
            f.control.update("INSERT INTO api_file_t VALUES ('linked',?,0),('deleted-link',?,1),('other-link','foreign',0)", id, id);
            var detail = (Map<?,?>) f.run("detail", Map.of("apiId", id));
            var rows = (List<?>) detail.get("publishParams");
            assertThat(rows).hasSize(count);
            assertThat(rows.stream().filter(row -> "response".equals(((Map<?,?>) row).get("paramScope"))).count()).isEqualTo(count / 2);
            assertThat(detail.get("requestExample")).isEqualTo("{\"q\":1}");
            assertThat(detail.get("responseExample")).isEqualTo("{\"ok\":true}");
            assertThat(((Map<?,?>) detail.get("extendInfo")).get("exampleInitialization").toString()).contains("request", "response");
            assertThat(((Number) detail.get("apiFileCount")).intValue()).isEqualTo(1);
            assertThat(f.counter.reads).isEqualTo(4);
            assertThat(f.control.reads).isEqualTo(1);
            System.out.println("Service detail " + count + " parameters: 4 tenant SQL + 1 control SQL; foreign parameter excluded");
            f.scope.tenant = "tenant-b";
            assertExit(f.run("detail", Map.of("apiId", id)), 404);
            assertThat(f.control.reads).isZero();
        }
    }
    @Test void requiredDictionaryValuesPersistAndReadBackAsBinaryFlags() throws Exception {
        var f = new Fixture();
        var body = f.createBody("source-a");
        List<Object> values = List.of(true, false, "true", "false", "1", "0", 1, 0, "是", "否", "必填", "可选");
        List<Map<String,Object>> params = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) params.add(Map.of("paramName", "p" + i, "paramType", "string", "required", values.get(i)));
        body.put("publishParams", params);
        var saved = (Map<?,?>) f.run("save", body);
        var detail = (Map<?,?>) f.run("detail", Map.of("apiId", saved.get("id")));
        var rows = (List<?>) detail.get("publishParams");
        assertThat(rows).hasSize(values.size());
        for (int i = 0; i < rows.size(); i++) assertThat(((Map<?,?>) rows.get(i)).get("required")).isEqualTo(i % 2 == 0 ? "1" : "0");
    }
    @Test void aNewSourceHasNoServicesAndThreeRegistrationsPersistOnlyTheirOwnSource()throws Exception {
        var f=new Fixture();
        assertThat(((Map<?,?>)f.run("list",Map.of("datasourceId","source-a"))).get("list")).isEqualTo(List.of());
        for(int i=0;i<3;i++)assertThat(f.run("save",f.createBody("source-a"))).isInstanceOf(Map.class);
        assertThat(f.data.jdbc.queryForObject("select count(*) from api_info_t where datasource_id='source-a' and tenant_id='tenant-a' and org_id='org-a'",Integer.class)).isEqualTo(3);
        assertThat((List<?>)((Map<?,?>)f.run("list",Map.of("datasourceId","source-a"))).get("list")).hasSize(3);
        assertThat((List<?>)((Map<?,?>)f.run("list",Map.of("datasourceId","source-b"))).get("list")).isEmpty();
        String id=f.data.jdbc.queryForObject("select min(tid) from api_info_t",String.class);var update=f.createBody("source-b");update.put("id",id);
        assertThat(f.run("save",update)).isInstanceOf(ExitValue.class);
        update=f.createBody("source-a");update.put("id",id);update.remove("datasourceId");assertThat(f.run("save",update)).isInstanceOf(Map.class);
        assertThat(f.data.jdbc.queryForObject("select datasource_id from api_info_t where tid=?",String.class,id)).isEqualTo("source-a");
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void sourceScopedPagingUsesConstantReadsAndOneControlLookup(int count)throws Exception {
        var f=new Fixture();
        for(int i=0;i<count;i++)f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,datasource_id,org_id,created_by,service_name) values(?,'tenant-a',0,'source-a','org-a','user-a',?)","s"+i,"服务"+i);
        f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,datasource_id,org_id,created_by) values('wrong-source','tenant-a',0,'source-b','org-a','user-a'),('unlinked','tenant-a',0,null,'org-a','user-a'),('foreign-service','tenant-b',0,'source-a','org-a','user-a')");
        f.control.update("INSERT INTO api_file_t VALUES ('linked','s0',0),('deleted-link','s0',1),('outside-page','foreign-service',0)");
        var result=(Map<?,?>)f.run("list",Map.of("datasourceId","source-a","pageSize",count));
        assertThat((List<?>)result.get("list")).hasSize(count);assertThat(((Number)result.get("total")).intValue()).isEqualTo(count);
        assertThat(f.counter.reads).isEqualTo(6);
        assertThat(f.control.reads).isEqualTo(1);
        assertThat(rows(result)).filteredOn(row -> "s0".equals(row.get("id"))).singleElement()
                .satisfies(row -> assertThat(((Number) row.get("apiFileCount")).intValue()).isEqualTo(1));
        assertThat(f.counter.sql).anyMatch(sql -> sql.contains("api_info_t") && sql.contains("datasource_id")
                && sql.contains("tenant_id"));
        System.out.println("Proxy service page " + count + " rows: 6 tenant SQL + 1 control SQL");
    }
    @Test void parentAndServiceScopeAreBothCheckedAndForgedTenantOrOrgCannotGrantAccess()throws Exception {
        var f=new Fixture();
        for(String role:List.of("ALL","ORG","OWNER")){
            f.scope.role=role;
            for(String id:List.of("foreign","deleted","missing")) {
                assertThat(f.run("list",Map.of("datasourceId",id,"tenantId","tenant-b","departmentOnly",false))).isInstanceOf(ExitValue.class);
                assertThat(f.run("save",f.createBody(id))).isInstanceOf(ExitValue.class);
            }
            if(!role.equals("ALL")){
                assertThat(f.run("list",Map.of("datasourceId","department"))).isInstanceOf(ExitValue.class);
                assertThat(f.run("save",f.createBody("department"))).isInstanceOf(ExitValue.class);
            }
        }
        assertThat(f.run("save",f.createBody("direct"))).isInstanceOf(ExitValue.class);
        assertThat(f.run("list",Map.of("datasourceId",""))).isInstanceOf(ExitValue.class);
        assertThat(f.run("list",Map.of("datasourceId","x".repeat(33)))).isInstanceOf(ExitValue.class);
        assertThat(f.run("save",f.createBody("x".repeat(33)))).isInstanceOf(ExitValue.class);
        assertThat(f.data.jdbc.queryForObject("select count(*) from api_info_t",Integer.class)).isZero();
        f.scope.tenant=null;assertThatThrownBy(()->f.run("list",Map.of("datasourceId","source-a"))).hasMessageContaining("租户");
    }
    @ParameterizedTest @ValueSource(strings={"ORG","OWNER"})
    void parentPermissionDoesNotBroadenServiceRowScope(String role)throws Exception {
        var f=new Fixture();f.scope.role=role;
        f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,datasource_id,org_id,created_by) values('allowed','tenant-a',0,'source-a','org-a','user-a'),('restricted','tenant-a',0,'source-a','org-b','user-b')");
        var result=(Map<?,?>)f.run("list",Map.of("datasourceId","source-a"));
        assertThat((List<?>)result.get("list")).hasSize(1);
        assertThat(((Number)result.get("total")).intValue()).isEqualTo(1);
        assertExit(f.run("detail", Map.of("apiId", "restricted", "datasourceId", "source-a")), 404);
        assertExit(f.run("detail", Map.of("apiId", "allowed", "datasourceId", "source-b")), 404);
        assertThat(f.run("detail", Map.of("apiId", "allowed", "datasourceId", "source-a"))).isInstanceOf(Map.class);
        f.scope.parentReadable = false;
        assertExit(f.run("list", Map.of("datasourceId", "source-a")), 404);
        assertExit(f.run("detail", Map.of("apiId", "allowed", "datasourceId", "source-a")), 404);
        assertThat(f.control.reads).isZero();
    }

    @Test
    void groupsCombineLegacyDefaultsAndFailClosedForEmptyOrganizationScope() throws Exception {
        var f = new Fixture();
        f.data.jdbc.update("""
                INSERT INTO api_info_t(tid, tenant_id, is_del, group_id, org_id, created_by) VALUES
                  ('null-group','tenant-a',0,NULL,'org-a','user-a'),
                  ('empty-group','tenant-a',0,'','org-a','user-a'),
                  ('default-group','tenant-a',0,'api_service_group_default','org-a','user-a'),
                  ('foreign-group','tenant-b',0,'api_service_group_default','org-a','user-a'),
                  ('deleted-service','tenant-a',1,'api_service_group_default','org-a','user-a'),
                  ('other-org','tenant-a',0,'api_service_group_default','org-b','user-a')
                """);
        f.data.jdbc.update("""
                INSERT INTO sym_dict_t(tid, dict_name, biz_type, tenant_id, is_del) VALUES
                  ('api_service_group_default','当前分组','apiServiceGroup','tenant-a',0),
                  ('foreign-dict','当前分组','apiServiceGroup','tenant-b',0),
                  ('deleted-dict','当前分组','apiServiceGroup','tenant-a',1)
                """);
        f.scope.role = "ORG";
        var request = Map.<String, Object>of("keyword", "当前_组", "departmentOnly", true,
                "tenantId", "tenant-b", "orgId", "org-b");
        var result = (Map<?, ?>) f.run("groups", request);
        assertThat(((Number) result.get("total")).intValue()).isEqualTo(3);
        assertThat(rows(result)).singleElement().satisfies(row -> {
            assertThat(row.get("id")).isEqualTo("api_service_group_default");
            assertThat(((Number) row.get("count")).intValue()).isEqualTo(3);
        });
        assertThat(f.counter.reads).isEqualTo(2);
        var defaultServices = (Map<?, ?>) f.run("list", Map.of("departmentOnly", true,
                "groupId", "api_service_group_default", "tenantId", "tenant-b", "orgId", "org-b"));
        assertThat(((Number) defaultServices.get("total")).intValue()).isEqualTo(3);
        assertThat(rows(defaultServices)).extracting(row -> row.get("id"))
                .containsExactlyInAnyOrder("null-group", "empty-group", "default-group");
        f.scope.scopeOrgs = List.of();
        var emptyScope = (Map<?, ?>) f.run("groups", request);
        assertThat(((Number) emptyScope.get("total")).intValue()).isZero();
        assertThat(rows(emptyScope)).singleElement().satisfies(row ->
                assertThat(((Number) row.get("count")).intValue()).isZero());
        assertThat(f.counter.reads).isEqualTo(2);
        assertThat(f.control.reads).isZero();
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void departmentPageUsesConstantReadsAndPreservesTenantAndRoleScope(int count) throws Exception {
        for (String role : List.of("ALL", "ORG", "OWNER")) {
            var f = new Fixture(); f.scope.role = role;
            for (int i=0; i<count; i++) f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,org_id,created_by,service_name) values(?,'tenant-a',0,'org-a','user-a',?)", "service-"+i, "服务"+i);
            f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,org_id,created_by) values('foreign','tenant-b',0,'org-a','user-a'),('outside-department','tenant-a',0,'org-b','user-a'),('same-org-other-owner','tenant-a',0,'org-a','user-b')");
            var result = (Map<?,?>) f.run("list", Map.of("departmentOnly", true, "pageSize", count));
            assertThat(((Number) result.get("total")).intValue()).isEqualTo(count + (role.equals("OWNER") ? 0 : 1));
            assertThat((List<?>) result.get("list")).hasSize(count);
            assertThat(f.counter.reads).isEqualTo(5);
            assertThat(f.control.reads).isEqualTo(1);
            assertThat(f.counter.sql).anyMatch(sql -> sql.contains("api_info_t") && sql.contains("tenant_id")
                    && sql.contains("org_id"));
            assertThat(f.run("groups", Map.of("departmentOnly", true, "orgId", "org-b"))).isInstanceOfSatisfying(Map.class,
                    groups -> assertThat(((Number) groups.get("total")).intValue()).isEqualTo(count + (role.equals("OWNER") ? 0 : 1)));
            assertThat(f.counter.reads).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void parameterSortUsesAuthorizedCountsBeforePagination(int size) throws Exception {
        var f = new Fixture();
        f.scope.role = "OWNER";
        for (int i = 0; i < 105; i++) {
            String id = "service-%03d".formatted(i);
            f.data.jdbc.update("""
                    INSERT INTO api_info_t(tid, tenant_id, is_del, datasource_id, org_id, created_by, service_name, group_id)
                    VALUES (?, 'tenant-a', 0, 'source-a', 'org-a', 'user-a', 'same-name', 'service-group')
                    """, id);
        }
        f.data.jdbc.update("""
                INSERT INTO api_info_t(tid, tenant_id, is_del, datasource_id, org_id, created_by)
                VALUES ('restricted', 'tenant-a', 0, 'source-a', 'org-a', 'user-b')
                """);
        f.data.jdbc.update("""
                INSERT INTO api_param_t(tid, api_id, tenant_id, is_del) VALUES
                  ('top-a','service-104','tenant-a',0), ('top-b','service-104','tenant-a',0),
                  ('next','service-103','tenant-a',0), ('outside','service-000','tenant-b',0),
                  ('deleted','service-000','tenant-a',1), ('restricted','restricted','tenant-a',0)
                """);
        f.data.jdbc.update("""
                INSERT INTO sym_dict_t(tid, dict_name, biz_type, tenant_id, is_del) VALUES
                  ('service-group','当前分组','apiServiceGroup','tenant-a',0),
                  ('service-group','外租户分组','apiServiceGroup','tenant-b',0)
                """);
        var request = Map.<String, Object>of("pageSize", size, "datasourceId", "source-a",
                "sortField", "paramCount", "sortDir", "desc", "tenantId", "tenant-b", "orgId", "org-b");
        var page = (Map<?, ?>) f.run("list", request);
        var rows = rows(page);
        assertThat(((Number) page.get("total")).intValue()).isEqualTo(105);
        assertThat(rows).hasSize(size).extracting(row -> row.get("id"))
                .startsWith("service-104", "service-103", "service-000").doesNotContain("restricted");
        assertThat(rows.subList(0, 3)).extracting(row -> ((Number) row.get("paramCount")).intValue()).containsExactly(2, 1, 0);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("groupName")).isEqualTo("当前分组"));
        assertThat(f.counter.reads).isEqualTo(5);
        assertThat(f.control.reads).isEqualTo(1);
        var next = new LinkedHashMap<>(request);
        next.put("pageNum", 2);
        assertThat(rows((Map<?, ?>) f.run("list", next))).hasSize(Math.min(size, 105 - size))
                .extracting(row -> row.get("id")).doesNotContainAnyElementsOf(rows.stream().map(row -> row.get("id")).toList());
        assertThat(f.counter.reads).isEqualTo(5);
        assertThat(f.control.reads).isEqualTo(1);
    }

    @Test
    void bindingKeepsWildcardSearchAndLegacyEmptyPageShape() throws Exception {
        var f = new Fixture();
        f.data.jdbc.update("""
                INSERT INTO api_info_t(tid, tenant_id, is_del, service_name, is_use, method, service_create_type)
                VALUES ('draft-id','tenant-a',0,?,0,'post','api'), ('offline-id','tenant-a',0,?,0,NULL,'apiBuild')
                """, "O'Reilly 服务", "plain service");
        f.data.jdbc.update("UPDATE api_info_t SET status='draft' WHERE tid='draft-id'");
        var filters = Map.<String, Object>of("keyword", "O'Reil_y", "groupId", "api_service_group_default",
                "method", "post", "status", "draft", "serviceCreateType", "api");
        var page = (Map<?, ?>) f.run("list", filters);
        assertThat(rows(page)).singleElement().satisfies(row -> {
            assertThat(row.get("id")).isEqualTo("draft-id");
            assertThat(row.get("method")).isEqualTo("POST");
        });
        var stats = (Map<?, ?>) page.get("stats");
        assertThat(((Number) stats.get("draft")).intValue()).isEqualTo(1);
        assertThat(((Number) stats.get("offline")).intValue()).isZero();
        var search = (Map<?, ?>) f.run("list", Map.of("keyword", "' OR 1=1 --"));
        assertThat(rows(search)).isEmpty();
        assertThat(((Map<?, ?>) search.get("stats")).get("draft")).isNull();
        assertThat(f.control.reads).isZero();
        var pastEnd = (Map<?, ?>) f.run("list", Map.of("pageNum", Integer.MAX_VALUE, "pageSize", 100));
        assertThat(pastEnd.get("pageNum")).isEqualTo(Integer.MAX_VALUE);
        assertThat(((Number) pastEnd.get("total")).intValue()).isEqualTo(2);
        assertThat(rows(pastEnd)).isEmpty();
        assertThat(f.control.reads).isZero();
        assertExit(f.run("list", Map.of("serviceCreateType", "invalid")), 400);
        f.scope.org = "";
        assertExit(f.run("list", Map.of("departmentOnly", true)), 403);
        assertExit(f.run("save", f.createBody("source-a")), 403);
    }

    private static void assertExit(Object outcome, int code) {
        assertThat(outcome).isInstanceOfSatisfying(ExitValue.class, exit ->
                assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(code));
    }

    private static List<Map<String, Object>> rows(Map<?, ?> result) {
        return StructuredValues.objectRows(result.get("list"));
    }

    @Test
    void saveRejectsOutOfScopeChangesAndInvalidRegistrationLimits() throws Exception {
        var f = new Fixture();
        f.scope.role = "OWNER";
        f.data.jdbc.update("""
                INSERT INTO api_info_t(tid, tenant_id, is_del, datasource_id, org_id, created_by) VALUES
                    ('outside-role','tenant-a',0,'source-a','org-a','user-b'),
                    ('outside-tenant','tenant-b',0,'source-a','org-a','user-a')
                """);
        var body = f.createBody("source-a");
        body.put("id", "outside-role");
        assertExit(f.run("save", body), 403);
        body.put("id", "outside-tenant");
        assertExit(f.run("save", body), 404);
        body.remove("id");
        body.put("method", "INVALID");
        assertExit(f.run("save", body), 400);
        body.remove("method");
        body.put("publishParams", Collections.nCopies(501, Map.of("paramName", "param", "paramType", "string")));
        assertExit(f.run("save", body), 413);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void ordinaryRegistrationUpdateRetainsTwoReads(int count) throws Exception {
        var f = new Fixture();
        var body = f.createBody("source-a");
        body.remove("datasourceId");
        var params = new ArrayList<Map<String, String>>();
        for (int i = 0; i < count; i++) params.add(Map.of("paramName", "p" + i, "paramType", "string"));
        body.put("publishParams", params);
        var saved = (Map<?, ?>) f.run("save", body);
        body.put("id", saved.get("id"));
        assertThat(f.run("save", body)).isInstanceOf(Map.class);
        assertThat(f.counter.reads).isEqualTo(2);
    }
}

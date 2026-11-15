package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.elasticsearch.EsPageInfo;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the canonical maintenance scripts against the installed Magic DB API and real SQL. */
public class OrganizationUserMaintenanceMagicTest {
    static final String SAVE="885b78dbd4474a4cb47566030e050c76", DELETE="d2ca261a63de4aa38188a0ed93c93acb";
    static final String SORT="288235e0cc4047b09933797f75ecfe12", DETAIL="1f91899a67444ecb8cbdaae5485ef17f";

    public static class Tenant { public String id(){return "tenant-a";} }
    public static class Identity { public boolean hasTable(String name){return false;} }
    public static class AssetScope {
        public Map<String,Object> resource(String code) {
            assertEquals("COMPONENT:DST-DETAIL", code);
            return Map.of("scope", "ORG", "userId", "viewer", "organizationIds", List.of("parent"));
        }
    }
    public static class AssetJson {
        public String toJSONStr(Object value) { return JSONUtil.toJsonStr(value); }
        public Map<?, ?> toJSONObject(String value) { return JSONUtil.parseObj(value); }
    }
    public static class ApprovalPrincipal {
        public Map<String, Object> principal() {
            return Map.of("userId", "operator", "permissionKeys", List.of("operator", "role:reviewer"));
        }
    }
    public static String approvalJsonExtract(String document, String property) {
        return document == null ? null : JSONUtil.parseObj(document).getStr(property.substring(2));
    }
    public static String approvalJsonUnquote(String value) { return value; }
    public static class AssetIndex {
        final List<Map<String,Object>> hits = new ArrayList<>();
        int calls;
        public boolean indexExists(String index) {
            assertEquals("dataassets", index);
            calls++;
            return true;
        }
        public EsPageInfo<Map<String,Object>> searchByJsonQuery(String index, String query, Class<?> type,
                int page, int size) {
            assertTrue(query.contains("\"orgId\":[\"parent\"]"));
            assertEquals(2000, size);
            calls++;
            return new EsPageInfo<>(page, size, hits.size(), hits);
        }
    }
    public static class Sorter {
        public String field(Map<?,?> p,Map<?,?> allowed,String fallback){return String.valueOf(allowed.get(allowed.containsKey(p.get("sortField"))?p.get("sortField"):fallback));}
        public String direction(Map<?,?> p){return "asc".equals(p.get("sortDir"))?"asc":"desc";}
    }
    public static class Accounts {
        final Fixture fixture;final List<String> revoked=new ArrayList<>();
        final List<String> online=new ArrayList<>();
        Accounts(Fixture fixture){this.fixture=fixture;}
        public boolean isLocal(String tenant){return "tenant-a".equals(tenant);}
        public Map<String,Object> session(){return Map.of("appId","app-a");}
        public Map<String,Object> find(String tenant,String id){fixture.reads++;var rows=fixture.jdbc.queryForList("select id from rm_user_t where tenant_id=? and id=? and deleted=0",tenant,id);return rows.isEmpty()?null:rows.getFirst();}
        public Map<String, String> names(List<String> ids) {
            if (ids.isEmpty()) return Map.of();
            var policy = org.mockito.Mockito.mock(com.linewell.dataelement.platform.tenant.application.TenantAccountPolicy.class);
            org.mockito.Mockito.when(policy.isLocal("tenant-a")).thenReturn(true);
            return new com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService(null, policy, fixture.jdbc)
                    .names(ids);
        }
        public void revoke(String id){revoked.add(id);}
        public List<String> onlineUserIds(String tenant){assertEquals("tenant-a", tenant);return online;}
    }
    public static class Fixture {
        final JdbcTemplate jdbc; final SQLModule db; final Accounts accounts=new Accounts(this);
        final LakeGovernanceMagicTest.CountedSource source=new LakeGovernanceMagicTest.CountedSource();
        int reads; final List<String> queries=Collections.synchronizedList(new ArrayList<>());
        boolean failAudit;
        Fixture() throws Exception {
            var ds=source;
            jdbc=new JdbcTemplate(ds);
            String ddl=Files.readString(Path.of("../db/baseline_ga/init.sql"));
            for(String table:List.of("rm_org_t","rm_user_t","rm_user_org_rela_t","rm_role_t","rm_user_role_rela_t")) {
                String section=ddl.split("CREATE TABLE `"+table+"` \\(",2)[1].split("\\) ENGINE=",2)[0];
                var columns=new ArrayList<String>();
                for(String line:section.split("\\r?\\n")) {
                    var m=java.util.regex.Pattern.compile("^\\s*`([^`]+)`\\s+(\\w+)(?:\\(([^)]+)\\))?.*").matcher(line);
                    if(m.matches()) {
                        String type=m.group(2).toLowerCase();
                        String mapped=List.of("tinyint","int","bigint","smallint").contains(type)?"bigint":List.of("datetime","timestamp").contains(type)?"timestamp":type.equals("date")?"date":"varchar(4000)";
                        columns.add("`"+m.group(1)+"` "+mapped);
                    }
                }
                jdbc.execute("create table `"+table+"`("+String.join(",",columns)+")");
            }
            var sources=new MagicDynamicDataSource();sources.setDefault(ds);db=new SQLModule(sources);db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("maintenance-test"));
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);
            var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");
            db.setColumnMapperProvider(mapper);db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(StrUtil::toUnderlineCase);db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity request){
                if(sql.getSql().stripLeading().toLowerCase().startsWith("select")){reads++;queries.add(sql.getSql());}
            }}));
            org("root","ROOT","根机构","根机构","0001","tenant-a");
            org("parent","root","上级","根机构/上级","00010001","tenant-a");
            org("other","root","另一个上级","根机构/另一个上级","00010002","tenant-a");
            org("foreign","root","外租户","外租户","0002","tenant-b");
        }
        void org(String id,String parent,String name,String path,String serial,String tenant){jdbc.update("insert into rm_org_t(id,parent_id,name,org_path,serial_number,sort_num,tenant_id,deleted,status,nature) values(?,?,?,?,?,1,?,0,0,'2')",id,parent,name,path,serial,tenant);}
        public Object users(String operation,Map<String,Object> payload) throws Exception {return run("ga_local_users_01",payload,Map.of("operation",operation,"payload",payload));}
        Object run(String id,Map<String,Object> body) throws Exception {return run(id,body,Map.of());}
        Object scoped(String id, Map<String,Object> body) throws Exception {
            try (var scope = TenantContext.use("tenant-a")) {
                return run(id, body, Map.of("tableRead", new StructuredTableReadMagicModule(jdbc),
                        "tableWrite", new StructuredTableWriteMagicModule(jdbc)));
            }
        }
        Object userPage(Map<String,Object> body) throws Exception {
            source.statements.set(0);
            try (var scope = TenantContext.use("tenant-a")) {
                return run("f75db35437b546f8a8c8e4eda6740e33", body,
                        Map.of("tableRead", new StructuredTableReadMagicModule(jdbc)));
            }
        }
        Object run(String id,Map<String,Object> body,Map<String,Object> extra) throws Exception {
            String source=CanonicalMagicSources.byId(id).replaceAll("(?m)^import[^\\r\\n]*[\\r\\n]+","").replace("StpUtil.getLoginId()","'operator'");
            var input=new HashMap<String,Object>();input.put("body",body);input.put("db",db);input.put("actor","operator");input.put("tenantRuntime",new Tenant());input.put("tenantAccounts",accounts);input.put("identityRuntime",new Identity());input.put("tableSort",new Sorter());input.put("users",this);input.putAll(extra);
            String prelude="import cn.hutool.core.util.IdUtil;\nimport cn.hutool.crypto.SmUtil;\nimport cn.hutool.json.JSONUtil;\nimport com.linewell.dataelement.utils.PwdRuleUtil;\nvar auditOperation=(a,b,c,d,e)=>users.audit();\nvar localUsers=(operation,payload)=>users.users(operation,payload);\nvar me=()=>({orgRootId:'root'});\nvar configData=()=>({orgRootId:'root',es_dataassets:'dataassets'});\n";
            return MagicScript.create(prelude+source,null).execute(new MagicScriptContext(input));
        }
        public boolean audit() {
            if (failAudit) throw new IllegalStateException("validation audit failure");
            return true;
        }
        void user(String id,String tenant,String org){
            jdbc.update("insert into rm_user_t(id,tenant_id,real_name,user_name,phone,password,status,deleted,gender,app_file_id,office_address,date_birth) values(?,?,?,?,?,'preserved-hash',0,0,'1','avatar','办公地址',date '1990-01-02')",id,tenant,"验证用户",id,"13800000000");
            jdbc.update("insert into rm_user_org_rela_t(id,tenant_id,user_id,org_id,job_type,sort_num) values(?,?,?,?,'1',0)","relation-"+id,tenant,id,org);
        }
    }
    static Map<?,?> result(Object value){return assertInstanceOf(Map.class,value);}
    static void exit(int expected,Object value){assertEquals(expected,((Number)assertInstanceOf(ExitValue.class,value).getValues()[0]).intValue());}

    @Test void qualityOrganizationOptionsKeepLegacyFieldsAndScopeInOneRead() throws Exception {
        String resourceId = "a1000000000000000000000000000017";
        for (int size : List.of(20, 100)) {
            var fixture = new Fixture();
            fixture.jdbc.update("delete from rm_org_t");
            fixture.jdbc.update("""
                    insert into rm_org_t(id, tenant_id, serial_number, name, deleted)
                    values ('ROOT', 'tenant-a', '000', 'Root', 0),
                           ('removed', 'tenant-a', '000', 'Removed', 1),
                           ('foreign', 'tenant-b', '000', 'Foreign', null)
                    """);
            var expected = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < size; index++) {
                var option = new LinkedHashMap<String, Object>();
                option.put("tid", "organization-" + index);
                option.put("name", index % 2 == 0 ? "A" : "Z");
                option.put("parentId", index % 2 == 0 ? null : "parent");
                expected.add(option);
            }
            for (int index = size - 1; index >= 0; index--) {
                var option = expected.get(index);
                fixture.jdbc.update("""
                        insert into rm_org_t(id, tenant_id, serial_number, name, parent_id, deleted)
                        values (?, 'tenant-a', ?, ?, ?, ?)
                        """, option.get("tid"), String.format("%03d", index / 2), option.get("name"),
                        option.get("parentId"), index % 2 == 0 ? null : 0);
            }

            assertEquals(expected, fixture.run(resourceId, Map.of("tenantId", "tenant-b")));
            assertEquals(1, fixture.reads);
            System.out.println("Quality organization options " + size + ": SQL reads=1");

            fixture.jdbc.update("update rm_org_t set deleted = 1 where tenant_id = 'tenant-a'");
            fixture.reads = 0;
            assertEquals(List.of(), fixture.run(resourceId, Map.of()));
            assertEquals(1, fixture.reads);
        }
    }

    @Test void orgCreationEditMoveAndStatusPreserveTreeAndUseConstantReads() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();
            for(int i=0;i<count;i++)f.org("child-"+i,"parent","子机构"+i,"根机构/上级/子机构"+i,"000100010001"+i,"tenant-a");
            f.reads=0;
            result(f.run(SAVE,Map.of("id","parent","name","新上级","parentId","other")));
            assertEquals(2,f.reads,"one page query and its count, regardless of subtree size");
            assertEquals(count,f.jdbc.queryForObject("select count(*) from rm_org_t where parent_id='parent' and org_path like '根机构/另一个上级/新上级/%'",Integer.class));
            assertEquals("外租户",f.jdbc.queryForObject("select org_path from rm_org_t where id='foreign'",String.class));
            f.reads=0;result(f.run(SAVE,Map.of("id","parent","status",1)));assertEquals(2,f.reads);
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_org_t where tenant_id='tenant-a' and status=1",Integer.class));
            assertEquals("新上级",f.jdbc.queryForObject("select name from rm_org_t where id='parent'",String.class));
            System.out.println("ORG subtree="+count+": save/status SQL reads=2; foreign tenant unchanged");
        }
        var f=new Fixture();var created=result(f.run(SAVE,Map.of("name","新机构","parentId","parent","tenantId","tenant-b")));
        assertEquals("tenant-a",f.jdbc.queryForObject("select tenant_id from rm_org_t where id=?",String.class,created.get("id")));
        assertEquals("根机构/上级/新机构",f.jdbc.queryForObject("select org_path from rm_org_t where id=?",String.class,created.get("id")));
        exit(400,f.run(SAVE,Map.of("name","新机构","parentId","parent")));
    }
    @Test void orgRejectsInvalidForeignParentsCyclesAndWrongTenantDetails() throws Exception {
        var f=new Fixture();
        exit(404,f.run(SAVE,Map.of("id","foreign","status",1)));
        exit(404,f.run(SAVE,Map.of("id","parent","parentId","foreign")));
        exit(400,f.run(SAVE,Map.of("id","root","parentId","parent")));
        exit(400,f.run(SAVE,Map.of("id","parent","status",9)));
        exit(404,f.run(DETAIL,Map.of("id","foreign")));
        assertEquals("根机构",result(f.run(DETAIL,Map.of("id","root"))).get("name"));
    }
    @Test void sortValidatesEveryIdBeforeWritesWithFixedReads() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();var ids=new ArrayList<String>();
            for(int i=0;i<count;i++){String id="s-"+i;ids.addFirst(id);f.org(id,"parent",id,"根机构/上级/"+id,"00010001"+i,"tenant-a");}
            f.reads=0;result(f.run(SORT,Map.of("ids",ids)));assertEquals(1,f.reads);
            assertEquals(1,f.jdbc.queryForObject("select sort_num from rm_org_t where id=?",Integer.class,ids.getFirst()));
            var mixed=new ArrayList<>(ids);mixed.set(count-1,"foreign");exit(404,f.run(SORT,Map.of("ids",mixed)));
            assertEquals(1,f.jdbc.queryForObject("select sort_num from rm_org_t where id='foreign'",Integer.class));
            exit(400,f.run(SORT,Map.of("ids",List.of("parent",ids.getFirst()))));
            exit(400,f.run(SORT,Map.of("ids",List.of(ids.getFirst(),ids.getFirst()))));
        }
    }
    @Test void deleteChecksWholeSubtreeAndForeignIdsBeforeAnyWrite() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();for(int i=0;i<count;i++)f.org("d-"+i,"parent","子"+i,"根机构/上级/子"+i,"00010001"+i,"tenant-a");
            f.reads=0;result(f.run(DELETE,Map.of("ids",List.of("parent"))));assertEquals(3,f.reads);
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_org_t where deleted=1",Integer.class));
            assertEquals(0,f.jdbc.queryForObject("select deleted from rm_org_t where id='foreign'",Integer.class));
        }
        var f=new Fixture();f.user("member","tenant-a","parent");
        exit(409,f.run(DELETE,Map.of("ids",List.of("parent"))));
        exit(404,f.run(DELETE,Map.of("ids",List.of("other","foreign"))));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from rm_org_t where deleted=1",Integer.class));
    }
    @Test void userStatusOnlyPreservesProfilePasswordAvatarAndPrimaryOrganization() throws Exception {
        var f=new Fixture();f.user("member","tenant-a","parent");
        result(f.users("save",Map.of("id","member","status",1)));
        var row=f.jdbc.queryForMap("select * from rm_user_t where id='member'");
        assertEquals("preserved-hash",row.get("password"));assertEquals("avatar",row.get("app_file_id"));
        assertEquals("办公地址",row.get("office_address"));assertEquals("1990-01-02",row.get("date_birth").toString());
        assertEquals("member",row.get("user_name"));assertEquals(List.of("member"),f.accounts.revoked);
        assertEquals("parent",f.jdbc.queryForObject("select org_id from rm_user_org_rela_t where user_id='member'",String.class));
    }
    @Test void userCreationSupportsExistingUiAccountsAndChecksOrganizationAndDuplicates() throws Exception {
        var f=new Fixture();
        for(String account:List.of("0","001234","yh","中文账号","a@b+测试")) {
            var p=new HashMap<String,Object>(Map.of("realName","验证用户","userName",account,"phone","13800000000","orgId","parent","status",0,"password","Validation!Pass9","tenantId","tenant-b"));
            var created=result(f.users("save",p));String id=created.get("id").toString();
            assertEquals("tenant-a",f.jdbc.queryForObject("select tenant_id from rm_user_t where id=?",String.class,id));
            exit(409,f.users("save",p));
            p.put("orgId","foreign");p.put("userName","foreign-org");exit(400,f.users("save",p));
        }
        f.user("foreign-user","tenant-b","foreign");exit(404,f.users("save",Map.of("id","foreign-user","status",1)));
        f.user("operator","tenant-a","parent");exit(409,f.users("save",Map.of("id","operator","status",1)));
        exit(409,f.users("delete",Map.of("id","operator")));
    }
    @Test void userPagingUsesFixedBatchQueriesAndDoesNotReturnPasswordHash() throws Exception {
        for(int count:List.of(20,100)) {
            var f = new Fixture();
            for (int i = 0; i < count + 1; i++) {
                f.user("member-" + i, "tenant-a", "parent");
                f.user("offline-" + i, "tenant-a", "parent");
                f.accounts.online.add("member-" + i);
            }
            f.jdbc.update("insert into rm_role_t(id,tenant_id,name,deleted) values('role','tenant-a','验证角色',0)");
            f.jdbc.update("insert into rm_user_role_rela_t(id,tenant_id,user_id,role_id) values"
                    + "('online-role','tenant-a','member-0','role'),('offline-role','tenant-a','offline-0','role')");
            f.user("other-org", "tenant-a", "other");
            f.user("foreign-user", "tenant-b", "foreign");
            f.accounts.online.addAll(List.of("foreign-user", "other-org"));
            for (boolean online : List.of(true, false)) {
                var page = result(f.userPage(Map.of("online", online, "orgId", "parent", "tenantId", "tenant-b",
                        "pageNum", 1, "pageSize", count, "sortField", "userName", "sortDir", "asc")));
                var list = (List<?>) page.get("list");
                assertEquals(count, list.size());
                assertEquals(count + 1, ((Number) page.get("total")).intValue());
                assertEquals(5, f.source.statements.get());
                for (Object item : list) {
                    var user = (Map<?, ?>) item;
                    assertEquals("******", user.get("password"));
                    assertEquals("parent", user.get("orgId"));
                    assertEquals(online, user.get("online"));
                    assertTrue(user.get("id").toString().startsWith(online ? "member-" : "offline-"));
                }
            }
            var legacy = result(f.userPage(Map.of("ids", List.of("member-0", "foreign-user"), "orgId", "parent")));
            var row = (Map<?, ?>) ((List<?>) legacy.get("list")).getFirst();
            assertEquals(1, ((Number) legacy.get("total")).intValue());
            assertEquals("member-0", row.get("id"));
            assertFalse(row.containsKey("online"));
            var empty = result(f.userPage(Map.of("orgId", "foreign", "pageNum", 1, "pageSize", count)));
            assertEquals(0, ((List<?>) empty.get("list")).size());
            exit(400, f.userPage(Map.of("online", "false")));
            System.out.println("USER page=" + count + ": online and offline each 5 SQL reads; tenant and organization bounded");
        }
    }
    @Test void userDeletionWithLegacyAvatarDoesNotRequireRetiredPhotoTable() throws Exception {
        var f=new Fixture();f.user("member","tenant-a","parent");
        result(f.users("delete",Map.of("id","member")));
        assertEquals(1,f.jdbc.queryForObject("select deleted from rm_user_t where id='member'",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from rm_user_org_rela_t where user_id='member'",Integer.class));
        assertEquals(List.of("member"),f.accounts.revoked);
    }

    @Test void organizationPageAndAssetsUseFixedReadsAndEnforceTenantAndRoleScope() throws Exception {
        String pageId = "65f57b8f1ff0494c907edc88374859bb";
        String treeId = "14c675168c134d4ea7d0c3ac3bc98131";
        String assetsId = "9caa9255f24d4bd08ca8de3ab9eb7de5";
        for (int size : List.of(20, 100)) {
            var f = new Fixture();
            var requested = new ArrayList<String>();
            var index = new AssetIndex();
            for (int i = 0; i < size; i++) {
                String id = "validation-org-" + i;
                requested.add(id);
                f.org(id, "parent", id, "根机构/上级/" + id, "0001000100" + i, "tenant-a");
                index.hits.add(Map.of("id", "app-" + i, "orgId", "parent", "assetType", "app", "appName", "验证应用"));
            }
            requested.set(size - 1, "outside");
            f.org("outside", "root", "外租户机构", "外租户机构", "00010000", "tenant-b");
            var query = Map.of("limit", size, "offset", 0, "ids", String.join(",", requested), "param", Map.of());
            f.reads = 0;
            var page = assertInstanceOf(org.ssssssss.magicapi.modules.db.model.PageResult.class, f.run(pageId, Map.of(), query));
            assertEquals(size - 1, page.getList().size());
            assertEquals(3, f.reads);
            f.reads = 0;
            var tree = assertInstanceOf(List.class, f.run(treeId, Map.of("parentId", "parent")));
            assertEquals(size, tree.size());
            assertEquals(2, f.reads);

            index.hits.add(Map.of("id", "other-app", "orgId", "other", "assetType", "app"));
            index.hits.add(Map.of("id", "foreign-app", "orgId", "parent", "assetType", "app", "tenantId", "tenant-b"));
            index.hits.add(Map.of("id", "allowed-db", "orgId", "parent", "assetType", "db", "appId", "app-0", "dbName", "验证数据库"));
            index.hits.add(Map.of("id", "mislinked-db", "orgId", "other", "assetType", "db", "appId", "app-0"));
            f.reads = 0;
            var assets = assertInstanceOf(List.class, f.run(assetsId, Map.of(),
                    Map.of("dataScope", new AssetScope(), "esCommonService", index, "jsons", new AssetJson())));
            assertEquals(2, assets.size());
            assertEquals(3, f.reads);
            assertEquals(2, index.calls);
            String serialized = JSONUtil.toJsonStr(assets);
            assertTrue(serialized.contains("allowed-db"));
            for (String forbidden : List.of("outside", "other-app", "foreign-app", "mislinked-db")) {
                assertFalse(serialized.contains(forbidden));
            }
            System.out.printf("Org %d: page 3 SQL, tree 2 SQL, assets 3 SQL and 2 ES; scope enforced%n", size);
        }
    }
    @Test void userRoleMaintenanceValidatesAllRolesAndPreservesOtherApplicationsWithFixedReads() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();f.user("member","tenant-a","parent");var ids=new ArrayList<String>();
            for(int i=0;i<count;i++){String id="r-"+i;ids.add(id);f.jdbc.update("insert into rm_role_t(id,tenant_id,app_id,name,deleted,status) values(?,'tenant-a','app-a',?,0,0)",id,id);}
            f.jdbc.update("insert into rm_role_t(id,tenant_id,app_id,name,deleted,status) values('other-app','tenant-a','app-b','其他应用角色',0,0),('foreign-role','tenant-b','app-a','外租户角色',0,0)");
            f.jdbc.update("insert into rm_user_role_rela_t(id,tenant_id,user_id,role_id) values('existing','tenant-a','member','other-app')");
            f.reads=0;result(f.run("user_role_save_01",Map.of("userId","member","roleIds",ids)));assertEquals(2,f.reads);
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_user_role_rela_t where user_id='member'",Integer.class));
            var invalid=new ArrayList<>(ids);invalid.set(count-1,"foreign-role");exit(400,f.run("user_role_save_01",Map.of("userId","member","roleIds",invalid)));
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_user_role_rela_t where user_id='member'",Integer.class));
        }
    }

    @Test
    void roleMenuReplacementValidatesTheBatchAndRollsBackOnAuditFailure() throws Exception {
        String endpoint = "e4445190b9a543c89d23b3a6adacffc5";
        for (int size : List.of(20, 100)) {
            var fixture = new Fixture();
            fixture.jdbc.execute("create table rm_menu_t(id varchar(40), tenant_id varchar(40), app_id varchar(40), deleted int)");
            fixture.jdbc.execute("""
                    create table rm_role_menu_rela_t(id varchar(40), tenant_id varchar(40), role_id varchar(40),
                    app_resource_id varchar(40), extend_id varchar(40))
                    """);
            fixture.jdbc.update("""
                    insert into rm_role_t(id,tenant_id,app_id,name,deleted,status)
                    values('role','tenant-a','app-a','验证角色',0,0)
                    """);
            fixture.jdbc.update("insert into rm_menu_t values('foreign-menu','tenant-b','app-a',0)");
            var menus = new ArrayList<String>();
            for (int index = 0; index < size; index++) {
                String id = "menu-" + index;
                fixture.jdbc.update("insert into rm_menu_t values(?,'tenant-a','app-a',0)", id);
                menus.add(id);
            }
            var request = Map.<String, Object>of("roleId", "role", "resourceIds", menus);
            fixture.source.statements.set(0);
            assertEquals(size, result(fixture.scoped(endpoint, request)).get("count"));
            assertEquals(4, fixture.source.statements.get());
            fixture.jdbc.update("insert into rm_role_menu_rela_t values('foreign-link','tenant-a','role','foreign-menu','')");
            fixture.source.statements.set(0);
            var detail = result(fixture.scoped("7cca7dbb12174db680af8e5b81aeca5d", Map.of("roleId", "role")));
            assertEquals(new HashSet<>(menus), new HashSet<>((List<?>) detail.get("resourceIds")));
            assertEquals(1, fixture.source.statements.get());
            var invalid = new ArrayList<>(menus);
            invalid.set(size - 1, "foreign-menu");
            exit(400, fixture.scoped(endpoint, Map.of("roleId", "role", "resourceIds", invalid)));
            fixture.failAudit = true;
            assertThrows(Exception.class, () -> fixture.scoped(endpoint, Map.of("roleId", "role", "resourceIds", List.of(menus.getFirst()))));
            assertEquals(size + 1, fixture.jdbc.queryForObject("select count(*) from rm_role_menu_rela_t where role_id='role'", Integer.class));
            System.out.printf("Role menus %d: save 4 SQL statements; detail 1 SQL; invalid IDs and audit rollback checked%n", size);
        }
    }

    @Test
    void menuCascadeAndMoveUseFixedSqlDistinctFromTenantAndApp() throws Exception {
        String state = "ad79907ebe4c4acca241d7e740e9e91b";
        String save = "57d488fe51404e37afe8528c348c87ee";
        for (int size : List.of(20, 100)) {
            var f = new Fixture();
            f.jdbc.execute("""
                    create table rm_menu_t(id varchar(64) primary key, tenant_id varchar(40), app_id varchar(40),
                        parent_id varchar(64), top_id varchar(64), resource_type int, sort_num int, status int,
                        deleted int, name varchar(64), sno varchar(64), url varchar(64), url_type int,
                        static_icon varchar(64), dynamic_img varchar(64), open_type int, code varchar(64),
                        data_scope_enabled int, update_id varchar(64), update_time timestamp,
                        create_id varchar(64), create_time timestamp)
                    """);
            f.jdbc.update("""
                    insert into rm_menu_t(id,tenant_id,app_id,parent_id,top_id,resource_type,status,deleted) values
                        ('moving','tenant-a','app-a','ROOT','moving',0,0,0),
                        ('target','tenant-a','app-a','ROOT','target',0,0,0),
                        ('foreign','tenant-b','app-a','moving','moving',0,0,0),
                        ('other-app','tenant-a','app-b','moving','moving',0,0,0)
                    """);
            for (int i = 0; i < size; i++) {
                f.jdbc.update("""
                        insert into rm_menu_t(id,tenant_id,app_id,parent_id,top_id,status,deleted)
                        values(?,'tenant-a','app-a','moving','moving',0,0)
                        """, "child-" + i);
            }
            f.source.statements.set(0);
            assertEquals(size + 1, result(f.scoped(state, Map.of("id", "moving", "deep", true, "status", 1))).get("affectedCount"));
            assertEquals(4, f.source.statements.get());
            assertEquals(0, f.jdbc.queryForObject("select sum(status) from rm_menu_t where id in ('foreign', 'other-app')", Integer.class));

            var move = Map.<String,Object>of("id", "moving", "parentId", "target", "name", "验证菜单");
            f.jdbc.execute("alter table rm_menu_t add constraint validation_child check (id <> 'child-0' or top_id <> 'target')");
            assertThrows(Exception.class, () -> f.scoped(save, move));
            assertEquals("ROOT", f.jdbc.queryForObject("select parent_id from rm_menu_t where id='moving'", String.class));
            f.jdbc.execute("alter table rm_menu_t drop constraint validation_child");
            f.source.statements.set(0);
            assertEquals("target", result(f.scoped(save, move)).get("topId"));
            assertEquals(5, f.source.statements.get());
            assertEquals(size + 2, f.jdbc.queryForObject("select count(*) from rm_menu_t where top_id='target'", Integer.class));
            exit(400, f.scoped(save, Map.of("id", "moving", "parentId", "other-app", "name", "验证菜单")));
            exit(400, f.scoped(save, Map.of("id", "moving", "parentId", "child-0", "name", "验证菜单")));
            String sort = "daaca32a76bd4787ba35d3d1ef4f871f";
            var ordered = java.util.stream.IntStream.range(0, size).mapToObj(index -> "child-" + index).toList();
            f.source.statements.set(0);
            assertEquals(size, result(f.scoped(sort, Map.of("ids", ordered))).get("count"));
            assertEquals(2, f.source.statements.get());
            exit(400, f.scoped(sort, Map.of("ids", List.of("child-0", "other-app"))));
            String delete = "c1e127f305fd44ac827bab7bb2c65550";
            exit(400, f.scoped(delete, Map.of("id", "moving")));
            exit(404, f.scoped(delete, Map.of("id", "foreign")));
            if (size == 20) {
                f.jdbc.update("""
                        insert into rm_menu_t(id,tenant_id,app_id,parent_id,top_id,resource_type,status,deleted) values
                          ('left','tenant-a','app-a','ROOT','left',0,0,0),
                          ('right','tenant-a','app-a','ROOT','right',0,0,0)
                        """);
                var start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var left = executor.submit(() -> { start.await(); return f.scoped(save,
                            Map.of("id", "left", "parentId", "right", "name", "并发菜单")); });
                    var right = executor.submit(() -> { start.await(); return f.scoped(save,
                            Map.of("id", "right", "parentId", "left", "name", "并发菜单")); });
                    start.countDown();
                    var outcomes = List.of(left.get(8, TimeUnit.SECONDS), right.get(8, TimeUnit.SECONDS));
                    assertEquals(1, outcomes.stream().filter(Map.class::isInstance).count());
                    exit(400, outcomes.stream().filter(ExitValue.class::isInstance).findFirst().orElseThrow());
                }
                f.jdbc.update("""
                        insert into rm_menu_t(id,tenant_id,app_id,parent_id,top_id,resource_type,status,deleted) values
                          ('vacant','tenant-a','app-a','ROOT','vacant',0,0,0),
                          ('entrant','tenant-a','app-a','ROOT','entrant',0,0,0)
                        """);
                var startDelete = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var adding = executor.submit(() -> { startDelete.await(); return f.scoped(save,
                            Map.of("id", "entrant", "parentId", "vacant", "name", "并发菜单")); });
                    var deleting = executor.submit(() -> { startDelete.await(); return f.scoped(delete, Map.of("id", "vacant")); });
                    startDelete.countDown();
                    var outcomes = List.of(adding.get(8, TimeUnit.SECONDS), deleting.get(8, TimeUnit.SECONDS));
                    assertEquals(1, outcomes.stream().filter(Map.class::isInstance).count());
                    exit(400, outcomes.stream().filter(ExitValue.class::isInstance).findFirst().orElseThrow());
                    assertEquals(0, f.jdbc.queryForObject("""
                            select count(*) from rm_menu_t child join rm_menu_t parent on child.parent_id=parent.id
                            where child.id='entrant' and parent.id='vacant' and parent.deleted=1
                            """, Integer.class));
                }
            }
            System.out.printf("Menu descendants %d: cascade 4 SQL, move 5 SQL, sort 2 SQL; bounds, concurrency and rollback checked%n", size);
        }
    }

    @Test
    void tenantUsersPageKeepsCountAndSessionScopeAcrossPageSizes() throws Exception {
        String endpoint = "tenant_user_page_magic_01";
        var f = new Fixture();
        for (int i = 0; i < 115; i++) f.user("validation-" + i, "tenant-a", "parent");
        f.user("foreign-user", "tenant-b", "foreign");
        for (int size : List.of(20, 100)) {
            f.source.statements.set(0);
            var page = result(f.scoped(endpoint, Map.of("pageNum", 1, "pageSize", size, "keyword", "验证用户")));
            assertEquals(size, ((List<?>) page.get("list")).size());
            assertEquals(115L, ((Number) page.get("total")).longValue());
            assertEquals(2, f.source.statements.get());
        }
        exit(403, f.scoped(endpoint, Map.of("targetTenantId", "tenant-b")));
    }

    @Test void approvalViewsPageWithFixedSqlAndScopedParticipants() throws Exception {
        var f = new Fixture();
        f.jdbc.execute("create alias JSON_VALID for '" + DataStandardPageMagicTest.class.getName() + ".validJson'");
        f.jdbc.execute("create alias JSON_EXTRACT for '" + getClass().getName() + ".approvalJsonExtract'");
        f.jdbc.execute("create alias JSON_UNQUOTE for '" + getClass().getName() + ".approvalJsonUnquote'");
        f.jdbc.execute("""
                create table flow_definition(id bigint, tenant_id varchar(32), flow_code varchar(40), flow_name varchar(100),
                    form_path varchar(100), form_custom varchar(1))
                """);
        String nodeFields = """
                id bigint, tenant_id varchar(32), del_flag varchar(1), node_code varchar(64), node_name varchar(64),
                node_type int, definition_id bigint, create_time timestamp, update_time timestamp
                """;
        f.jdbc.execute("create table flow_instance(" + nodeFields
                + ", create_by varchar(64), flow_status varchar(3), business_id varchar(64), activity_status int, ext varchar(500))");
        f.jdbc.execute("create table flow_task(" + nodeFields
                + ", instance_id bigint, flow_status varchar(3), form_path varchar(100), form_custom varchar(1))");
        f.jdbc.execute("create table flow_his_task(" + nodeFields
                + ", instance_id bigint, approver varchar(64), form_path varchar(100), message varchar(100))");
        f.jdbc.execute("create table flow_user(associated bigint, tenant_id varchar(32), del_flag varchar(1), processed_by varchar(64))");
        f.jdbc.update("insert into rm_user_t(id,tenant_id,real_name,deleted) values(?,?,?,0)",
                "operator", "tenant-a", "验证发起人");
        f.jdbc.update("insert into flow_definition(id,tenant_id,flow_code,flow_name) values(1,'tenant-a','validate','验证审批')");
        for (int i = 1; i <= 103; i++) {
            f.jdbc.update("""
                    insert into flow_instance(id,tenant_id,del_flag,node_type,definition_id,create_by,
                        flow_status,business_id,ext) values(?,'tenant-a','0',1,1,?,?,?,?)
                    """, i, i <= 102 ? "operator" : "other", "1", "validation-" + i, "{\"businessName\":\"验证申请\"}");
            f.jdbc.update("insert into flow_task(id,tenant_id,del_flag,node_type,definition_id,instance_id,flow_status) "
                    + "values(?,'tenant-a','0',1,1,?,'1')", i, i);
            f.jdbc.update("insert into flow_user values(?,?, '0', 'operator'),(?,?, '0', 'role:reviewer')",
                    i, i <= 102 ? "tenant-a" : "tenant-b", i, i <= 102 ? "tenant-a" : "tenant-b");
            f.jdbc.update("insert into flow_his_task(id,tenant_id,del_flag,node_type,definition_id,instance_id,approver) "
                    + "values(?,'tenant-a','0',1,1,?,?),(?,'tenant-a','0',1,1,?,?)",
                    i, i, i <= 102 ? "operator" : "other", i + 1000, i, i <= 102 ? "operator" : "other");
        }
        var context = Map.<String, Object>of("tableRead", new StructuredTableReadMagicModule(f.jdbc),
                "approvalRuntime", new ApprovalPrincipal(), "jsons", new AssetJson());
        try (var ignored = TenantContext.use("tenant-a")) {
            for (int size : List.of(20, 100)) {
                for (String view : List.of("todo", "process", "done")) {
                    f.source.statements.set(0);
                    var page = result(f.run("f23cef22cacc4a8cbe5ac3df7c3a1566",
                            Map.of("pageNum", 1, "pageSize", size, "flowSatusType", view,
                                    "sortField", "businessName", "sortDir", "asc"), context));
                    assertEquals(102, ((Number) page.get("total")).intValue());
                    assertEquals(size, ((List<?>) page.get("list")).size());
                    var first = result(((List<?>) page.get("list")).getFirst());
                    assertEquals("done".equals(view) ? "1001" : "1", first.get("id"));
                    assertEquals("验证申请", first.get("businessName"));
                    assertEquals("验证发起人", first.get("createName"));
                    assertEquals(4, f.source.statements.get());
                }
            }
            f.source.statements.set(0);
            var summary = result(f.run("a75efba824d64e6f9a3f12561f0a9f10", Map.of(), context));
            for (String count : List.of("todo", "process", "done")) {
                assertEquals(102, ((Number) summary.get(count)).intValue());
            }
            assertEquals(0, ((Number) summary.get("archive")).intValue());
            assertEquals(3, f.source.statements.get());
            var detail = "e827efbdb789464ea6fbf4b8570f4a61";
            exit(-1, f.run(detail, Map.of(), Map.of("instanceId", "103",
                    "tableRead", context.get("tableRead"), "approvalRuntime", context.get("approvalRuntime"),
                    "jsons", context.get("jsons"))));
            var detailContext = new HashMap<>(context);
            detailContext.put("instanceId", "1");
            detailContext.put("businessId", "validation-1");
            for (int size : List.of(20, 100)) {
                int existing = size == 20 ? 2 : 20;
                for (int i = existing; i < size; i++) {
                    f.jdbc.update("insert into flow_his_task(id,tenant_id,del_flag,node_type,definition_id,instance_id,approver)"
                            + " values(?,'tenant-a','0',1,1,1,null)", 2000 + i);
                }
                f.source.statements.set(0);
                var response = result(f.run(detail, Map.of(), detailContext));
                assertEquals(size, ((List<?>) response.get("flowNodes")).size());
                assertEquals("1", result(response.get("approvalInfo")).get("instanceId"));
                assertEquals(5, f.source.statements.get());
            }
            f.jdbc.update("""
                    insert into flow_instance(id,tenant_id,del_flag,node_type,definition_id,create_by,flow_status,business_id,ext)
                    values(3001,'tenant-a','0',1,1,'operator','1','validation-1','invalid')
                    """);
            detailContext.remove("instanceId");
            var latest = result(result(f.run(detail, Map.of(), detailContext)).get("approvalInfo"));
            assertEquals("3001", latest.get("instanceId"));
            assertEquals(Map.of(), latest.get("ext"));
        }
    }

    @Test void flowDefinitionPagesAndEditsStayInTheSessionTenant() throws Exception {
        var f = new Fixture();
        f.jdbc.execute("""
                create table flow_definition(id bigint primary key, tenant_id varchar(32), flow_code varchar(40),
                    flow_name varchar(100), model_value varchar(40), category varchar(100), version varchar(20),
                    is_publish int, activity_status int, form_custom varchar(1), form_path varchar(100),
                    listener_type varchar(100), listener_path varchar(400), ext varchar(500),
                    create_by varchar(64), update_by varchar(64), create_time timestamp, update_time timestamp, del_flag varchar(1))
                """);
        for (int i = 1; i <= 102; i++) {
            f.jdbc.update("insert into flow_definition(id,tenant_id,flow_code,flow_name,del_flag,is_publish) values(?,?,'validation','验证流程','0',1)",
                    i, i <= 101 ? "tenant-a" : "tenant-b");
        }
        for (int size : List.of(20, 100)) {
            f.source.statements.set(0);
            var page = result(f.scoped("169959dd8fb148e68b4cfe4644f63c76", Map.of("pageNum", 1, "pageSize", size,
                    "keyword", "验证", "publishStatus", 1)));
            assertEquals(101, ((Number) page.get("total")).intValue());
            assertEquals(size, ((List<?>) page.get("list")).size());
            assertEquals(2, f.source.statements.get());
        }
        f.source.statements.set(0);
        var options = (List<?>) f.scoped("6c3ab97a62ce4aec8b9fb91d17703726", Map.of());
        assertEquals(101, options.size());
        assertEquals(1, f.source.statements.get());
        var save = "f058946e714846049e38be378047d1c6";
        exit(-1, f.scoped(save, Map.of("id", "102", "flowName", "越界修改")));
        assertEquals("验证流程", f.jdbc.queryForObject("select flow_name from flow_definition where id=102", String.class));
        var created = result(f.scoped(save, Map.of("flowCode", "validation-create", "flowName", "验证新流程",
                "tenantId", "tenant-b")));
        assertEquals("tenant-a", f.jdbc.queryForObject("select tenant_id from flow_definition where id=?",
                String.class, Long.parseLong((String) created.get("id"))));
        assertEquals("1", result(f.scoped(save, Map.of("id", "1", "flowName", "验证修改"))).get("id"));
        assertEquals("验证修改", f.jdbc.queryForObject("select flow_name from flow_definition where id=1", String.class));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var rename = executor.submit(() -> {
                start.await();
                return f.scoped(save, Map.of("id", "1", "flowName", "并发修改名称"));
            });
            var categorize = executor.submit(() -> {
                start.await();
                return f.scoped(save, Map.of("id", "1", "category", "并发修改分类"));
            });
            start.countDown();
            assertEquals("1", result(rename.get(8, TimeUnit.SECONDS)).get("id"));
            assertEquals("1", result(categorize.get(8, TimeUnit.SECONDS)).get("id"));
        }
        assertEquals("并发修改名称", f.jdbc.queryForObject("select flow_name from flow_definition where id=1", String.class));
        assertEquals("并发修改分类", f.jdbc.queryForObject("select category from flow_definition where id=1", String.class));
        assertThrows(RuntimeException.class, () -> f.scoped(save, Map.of("id", "1",
                "flowName", "失败后回滚", "modelValue", "x".repeat(41))));
        assertEquals("并发修改名称", f.jdbc.queryForObject("select flow_name from flow_definition where id=1", String.class));
    }

    @Test void approvalDelegationsAndSuggestionsDoNotReadOrWriteAnotherOwnersRecords() throws Exception {
        for (int size : List.of(20, 100)) {
            var f = new Fixture();
            f.jdbc.execute("""
                    create table wf_approval_delegation_t(tid varchar(32), tenant_id varchar(32), is_del int,
                        delegator_user_id varchar(64), delegate_user_id varchar(64), flow_code varchar(64),
                        start_time timestamp, end_time timestamp, reason varchar(500), status int,
                        created_time timestamp, updated_time timestamp, updated_by varchar(64))
                    """);
            f.jdbc.execute("""
                    create table flow_suggestion(tid varchar(64), tenant_id varchar(32), is_del int,
                        content varchar(255), create_by varchar(64), use_num bigint, create_time timestamp,
                        update_time timestamp, update_by varchar(64))
                    """);
            f.jdbc.update("insert into rm_user_t(id,tenant_id,real_name,deleted) values(?,?,?,0)",
                    "delegate", "tenant-a", "验证受托人");
            for (int i = 0; i < size + 2; i++) {
                String id = "validation-" + i;
                String tenant = i == size ? "tenant-b" : "tenant-a";
                String owner = i == size + 1 ? "other" : "operator";
                f.jdbc.update("insert into wf_approval_delegation_t(tid,tenant_id,delegator_user_id,delegate_user_id,is_del,status)"
                        + " values(?,?,?,'delegate',0,1)", id, tenant, owner);
                f.jdbc.update("insert into flow_suggestion(tid,tenant_id,create_by,is_del,use_num) values(?,?,?,0,0)",
                        id, tenant, owner);
            }
            f.source.statements.set(0);
            var delegation = (List<?>) f.scoped("b81216bf81ee41d8ad5d09526bf07701", Map.of());
            assertEquals(size, delegation.size());
            assertEquals("验证受托人", result(delegation.getFirst()).get("delegateUserName"));
            assertEquals(3, f.source.statements.get());
            f.source.statements.set(0);
            assertEquals(size, ((List<?>) f.scoped("f8a5b9d39f3a43dabb3ddcbd0c1a2aed", Map.of())).size());
            assertEquals(1, f.source.statements.get());
            for (int i : List.of(size, size + 1)) {
                var target = Map.<String, Object>of("tid", "validation-" + i);
                assertEquals(0, f.scoped("88f84dd78cbb42899740f49437a41148", target));
                exit(-1, f.scoped("b81216bf81ee41d8ad5d09526bf07703", target));
            }
            var own = Map.<String, Object>of("tid", "validation-0");
            assertEquals(1, f.scoped("88f84dd78cbb42899740f49437a41148", own));
            assertEquals(true, f.scoped("b81216bf81ee41d8ad5d09526bf07703", own));
        }
    }
}

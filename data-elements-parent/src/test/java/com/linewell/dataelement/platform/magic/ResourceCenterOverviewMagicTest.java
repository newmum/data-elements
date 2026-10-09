package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Execute the canonical overview and its DST dependency, including SQL and scope predicates. */
class ResourceCenterOverviewMagicTest {
    @ParameterizedTest @ValueSource(ints = {20, 100})
    void overviewUsesSevenAggregateReadsForTwentyAndHundredDomains(int size) throws Exception {
        Fixture f = new Fixture(size);
        List<Map<String, Object>> layers = f.overview();
        Map<String, Object> dwd = layer(layers, "DWD");
        assertThat(((Number) dwd.get("categoryCount")).longValue()).isEqualTo(size);
        List<Map<String, Object>> items = items(dwd);
        assertThat(items).hasSize(size);
        for (Map<String, Object> item : items) {
            assertThat(((Number) item.get("tableCount")).longValue()).isEqualTo(1);
            assertThat(((Number) item.get("dataCount")).longValue()).isEqualTo(7);
            assertThat(item.get("sourceId")).isEqualTo("db-" + item.get("layerCode"));
            assertThat(item.keySet()).doesNotContain("poolCfg", "pool_cfg");
        }
        assertThat(f.counter.reads).isEqualTo(7);
        assertThat(f.counter.writes).isZero();
        assertThat(f.scope.calls).isEqualTo(2);
        assertThat(f.counter.sql).allSatisfy(sql -> assertThat(sql.toLowerCase()).doesNotContain("select *", "field_governance_config"));
    }

    @Test void ownerAndOrganizationScopesHideForeignAndUnauthorizedBindingsAndCounts() throws Exception {
        for (String mode : List.of("OWNER", "ORG")) {
            Fixture f = new Fixture(20);
            f.scope.mode = mode;
            f.addHiddenAndForeignSources();
            List<Map<String, Object>> layers = f.overview();
            assertThat(((Number) layer(layers, "DWD").get("categoryCount")).longValue()).isEqualTo(20);
            assertThat(((Number) layer(layers, "DWS").get("categoryCount")).longValue()).isZero();
            assertThat(layer(layers, "DWS").get("sourceId")).isNull();
            Map<String, Object> dst = layer(layers, "DST");
            assertThat(((Number) dst.get("tableCount")).longValue()).isEqualTo(20);
            assertThat(items(dst)).hasSize(1);
            assertThat(items(dst).getFirst().get("orgId")).isEqualTo("org-a");
            assertThat(f.counter.reads).isEqualTo(7);
        }
    }

    @Test void tableClassificationWinsAndBlankTableTypesUseModernThenLegacySourceConfiguration() throws Exception {
        Fixture f = new Fixture(1);
        f.jdbc.update("insert into db_datasource_t values('modern','ga',0,1,'u','org-a','app','modern','mysql','{\"dataSourceType\":\"dwd-0\"}')");
        f.jdbc.update("insert into db_table_t values('modern-table','ga','modern',0,2,'',11)");
        f.jdbc.update("insert into db_datasource_t values('legacy','ga',0,2,'u','org-a','app','legacy','mysql',null)");
        f.jdbc.update("insert into da_prop_t values('legacy','ga',0,'dataSourceType','dwd-0')");
        f.jdbc.update("insert into db_table_t values('legacy-table','ga','legacy',0,2,null,13)");
        f.jdbc.update("insert into db_table_t values('explicit','ga','modern',0,2,'dws',17)");
        f.jdbc.update("insert into db_table_t values('pending','ga','modern',0,1,'dwd-0',19)");
        f.jdbc.update("insert into db_table_t values('deleted','ga','modern',1,2,'dwd-0',23)");
        List<Map<String, Object>> layers = f.overview();
        Map<String, Object> dwd = layer(layers, "DWD");
        assertThat(((Number) dwd.get("categoryCount")).longValue()).isEqualTo(3);
        assertThat(((Number) items(dwd).getFirst().get("dataCount")).longValue()).isEqualTo(31);
        assertThat(((Number) layer(layers, "DWS").get("categoryCount")).longValue()).isEqualTo(1);
        assertThat(((Number) layer(layers, "DST").get("tableCount")).longValue()).isEqualTo(4);
    }

    @Test void missingTenantOrUnknownRoleFailsClosedBeforeAnySql() throws Exception {
        Fixture f = new Fixture(1);
        f.scope.tenant = "";
        assertThat(f.executeOverview()).isInstanceOf(ExitValue.class);
        assertThat(f.counter.reads).isZero();
        f.scope.tenant = "ga";
        f.scope.mode = "UNKNOWN";
        assertThat(f.executeOverview()).isInstanceOf(ExitValue.class);
        assertThat(f.counter.reads).isZero();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> items(Map<String, Object> layer) {
        return (List<Map<String, Object>>) ((List<Map<String, Object>>) layer.get("domains")).getFirst().get("items");
    }
    static Map<String, Object> layer(List<Map<String, Object>> layers, String name) {
        return layers.stream().filter(row -> name.equals(row.get("name"))).findFirst().orElseThrow();
    }

    public static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();
        final Counter counter = new Counter();
        final String path = "0.8961bb1c926143a89dd9d0b0e0adb4ef.";
        Fixture(int size) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:center_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(32),is_del int,dict_name varchar(64),dict_code varchar(64),parent_id varchar(64),tree_path varchar(256),level_no int,sort_no int)");
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),is_del int,asset_status int,created_by varchar(64),org_id varchar(64),app_id varchar(64),db_name varchar(64),db_type varchar(32),pool_cfg clob)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),is_del int,asset_status int,data_source_type varchar(64),record_count bigint)");
            jdbc.execute("create table da_prop_t(parent_id varchar(64),tenant_id varchar(32),is_del int,prop_name varchar(64),prop_value varchar(128))");
            jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(32),is_del int,target_id varchar(64),target_type varchar(32),datasource_id varchar(64))");
            jdbc.execute("create table rm_org_t(id varchar(64),tenant_id varchar(32),name varchar(64),deleted int,serial_number varchar(64),sort_num int)");
            jdbc.update("insert into rm_org_t values('org-a','ga','本部门',0,'000101',0)");
            jdbc.update("insert into rm_org_t values('org-b','ga','其他部门',0,'000102',1)");
            for (String code : List.of("dst", "ods", "dwd", "dwm", "dws")) {
                jdbc.update("insert into sym_dict_t values(?,'ga',0,?,?, 'root',?,1,0)", code, code, code, path + code + ".");
            }
            for (int i = 0; i < size; i++) {
                String code = "dwd-" + i;
                jdbc.update("insert into sym_dict_t values(?,'ga',0,?,?,'dwd',?,2,?)", code, code, code, path + "dwd." + code + ".", i);
                jdbc.update("insert into db_datasource_t values(?,'ga',0,2,'u','org-a','app',?,'mysql',null)", "db-" + code, code);
                jdbc.update("insert into db_table_t values(?,'ga',?,0,2,?,7)", code, "db-" + code, code);
                jdbc.update("insert into dwm_center_layer_source_t values('ga',0,?,'domain',?)", code, "db-" + code);
            }
            var dynamic = new MagicDynamicDataSource(); dynamic.setDefault(source);
            db = new SQLModule(dynamic); db.setDataSourceNode(dynamic.getDataSource());
            var columns = new ColumnMapperAdapter(); columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider()); columns.setDefault("camel");
            db.setColumnMapperProvider(columns); db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(v -> v); db.setSqlInterceptors(List.of(counter)); db.setNamedTableInterceptors(List.of());
        }
        void addHiddenAndForeignSources() {
            jdbc.update("insert into db_datasource_t values('hidden','ga',0,2,'other','org-b','app','不可见','mysql',null)");
            jdbc.update("insert into db_table_t values('hidden-table','ga','hidden',0,2,'dws',1000)");
            jdbc.update("insert into dwm_center_layer_source_t values('ga',0,'dws','layer','hidden')");
            // 同 ID 的其他租户行不能被 join 或统计，其他租户的组织/配置也不能回显。
            jdbc.update("insert into db_datasource_t values('db-dwd-0','other',0,2,'u','org-a','app','外租户','mysql',null)");
            jdbc.update("insert into db_table_t values('foreign','other','db-dwd-0',0,2,'dws',2000)");
            jdbc.update("insert into rm_org_t values('org-a','other','外租户部门',0,'000101',0)");
            jdbc.update("insert into sym_dict_t values('foreign-dict','other',0,'外租户分类','foreign','dwd',?,2,0)", path + "dwd.foreign.");
            jdbc.update("insert into dwm_center_layer_source_t values('other',0,'dwd','layer','db-dwd-0')");
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> overview() throws Exception {
            return (List<Map<String, Object>>) executeOverview();
        }
        Object executeOverview() throws Exception {
            return execute("77dd13a825764993848cb05d8cb6a771", "var dstInfo = () => fixture.dst();\n");
        }
        public Object dst() throws Exception {
            return execute("48f4dd83b6a943fbba6b252921c730f8", "var me = () => { return {orgRootSerialNumber:'0001'} };\n");
        }
        Object execute(String id, String prelude) throws Exception {
            String script = CanonicalMagicSources.byId(id)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            return MagicScript.create(prelude + script, null).execute(new MagicScriptContext(Map.of(
                    "db", db, "tenantRuntime", scope, "dataScope", scope, "fixture", this)));
        }
    }
    public static class Scope {
        String mode = "ALL", tenant = "ga"; int calls;
        public String id() { return tenant; }
        public Map<String, Object> dataSource() {
            calls++;
            return Map.of("scope", mode, "userId", "u", "organizationIds", List.of("org-a"));
        }
    }
    static class Counter implements SQLInterceptor {
        int reads, writes; List<String> sql = new ArrayList<>();
        @Override public void preHandle(BoundSql bound, RequestEntity request) {
            sql.add(bound.getSql());
            if (bound.getSql().stripLeading().toLowerCase().startsWith("select")) reads++; else writes++;
        }
    }
}

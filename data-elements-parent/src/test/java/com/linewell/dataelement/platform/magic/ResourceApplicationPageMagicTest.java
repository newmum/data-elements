package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class ResourceApplicationPageMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagesOnlyOwnActiveApplicationsAndBatchesTheirRelatedRows(int size) throws Exception {
        var fixture = new DatasourceCompletionMagicTest.Fixture(0);
        var dialects = new DialectAdapter();
        dialects.add(new MySQLDialect() {
            @Override public boolean match(String url) { return url.contains(":h2:"); }
        });
        fixture.db.setDialectAdapter(dialects);
        fixture.db.setResultProvider(new DefaultResultProvider("resource-application-test"));
        var jdbc = fixture.jdbc;
        jdbc.execute("""
            create table data_apply_form_t(tid varchar(32),tenant_id varchar(32),apply_user_id varchar(32),
                created_by varchar(32),apply_name varchar(64),catalog_ids varchar(500),flow_status int,
                is_del int,created_time timestamp,updated_time timestamp,flow_instance_id varchar(64))
            """);
        jdbc.execute("create table da_catalog_t(tid varchar(32),tenant_id varchar(32),is_del int,catalog_name varchar(64),org_id varchar(32),target_table_id varchar(32))");
        jdbc.execute("create table da_prop_t(parent_id varchar(32),tenant_id varchar(32),is_del int,data_type varchar(32),prop_name varchar(32),prop_value varchar(64))");
        jdbc.execute("create table flow_instance(id bigint,tenant_id varchar(32),business_id varchar(32),definition_id bigint,del_flag varchar(1),create_time timestamp)");
        jdbc.execute("create table flow_definition(id bigint,tenant_id varchar(32),del_flag varchar(1),flow_code varchar(32))");
        jdbc.execute("create table flow_task(id bigint,tenant_id varchar(32),instance_id bigint,del_flag varchar(1),node_type int,create_time timestamp)");
        jdbc.update("insert into da_catalog_t values ('catalog','ga',0,'Visible','org','table'),('catalog','other',0,'Foreign','other','other')");
        jdbc.update("insert into da_prop_t values ('catalog','ga',0,'catalog','shareType','shared'),('catalog','other',0,'catalog','shareType','foreign')");
        for (int i=0; i<=size; i++) {
            String id = String.format("apply-%03d", i);
            jdbc.update("insert into data_apply_form_t values (?,'ga','owner','owner','example %','catalog',2,0,current_timestamp,TIMESTAMP '2026-01-01 12:00:00','1')", id);
            jdbc.update("insert into da_prop_t values (?,'ga',0,'apply_form','useTitle','visible')", id);
        }
        jdbc.update("insert into data_apply_form_t values ('foreign','other','owner','owner','example %','catalog',2,0,current_timestamp,current_timestamp,null)");
        jdbc.update("insert into data_apply_form_t values ('someone-else','ga','other','other','example %','catalog',2,0,current_timestamp,current_timestamp,null)");
        jdbc.update("insert into data_apply_form_t values ('deleted','ga','owner','owner','example %','catalog',2,1,current_timestamp,current_timestamp,null)");
        jdbc.update("insert into flow_definition values (1,'ga','0','catalogSubscribe'),(1,'other','0','foreign')");
        jdbc.update("insert into flow_instance values (1,'ga','apply-000',1,'0',current_timestamp),(2,'ga','apply-000',1,'0',current_timestamp),(1,'other','apply-000',1,'0',current_timestamp)");
        jdbc.update("insert into flow_task values (11,'ga',1,'0',1,current_timestamp),(22,'ga',2,'0',1,current_timestamp),(33,'other',1,'0',1,current_timestamp)");
        var counter = new TaskCategoryWarehouseScopeMagicTest.Counter();
        fixture.db.setSqlInterceptors(List.of(counter));
        String script = CanonicalMagicSources.byId("ce1844634b80491c8aeb9c7da6ebec7e")
                .replaceAll("(?m)^import (?!java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
        var body = Map.of("pageSize", size, "tenantId", "other", "userId", "other", "conditions", List.of(
                Map.of("field", "flowStatus", "type", "match", "value", "2"),
                Map.of("field", "applyName", "type", "like", "value", "%")));
        var response = (Map<?, ?>) MagicScript.create(script, null).execute(new MagicScriptContext(Map.of(
                "db", fixture.db, "body", body, "tenantRuntime", fixture.scope,
                "approvalRuntime", new Principal(), "tableSort", new TableSortMagicModule())));
        assertThat(((Number) response.get("total")).intValue()).isEqualTo(size + 1);
        var rows = (List<?>) response.get("list");
        assertThat(rows).hasSize(size);
        var first = (Map<?, ?>) rows.get(0);
        assertThat(first.get("tid")).isEqualTo("apply-000");
        assertThat(first.get("taskId")).isEqualTo("11");
        assertThat(first.get("useTitle")).isEqualTo("visible");
        assertThat(((Map<?, ?>) first.get("detail")).get("shareType")).isEqualTo("shared");
        assertThat(counter.reads).isEqualTo(6);
    }

    public static class Principal {
        public Map<String, Object> principal() { return Map.of("userId", "owner"); }
    }
}

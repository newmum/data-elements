package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** The materialization choices must not reveal target databases outside the role's data scope. */
class TargetDatasourceOptionsScopeMagicTest {
    @Test
    void optionsOnlyIncludeVisibleDatasourcesFromTheCurrentTenant() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:target_options_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var sources = new MagicDynamicDataSource();
        sources.setDefault(source);
        var db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        db.setSqlInterceptors(List.of());
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(64),dict_code varchar(64),parent_id varchar(64),is_del int,sort_no int,dict_name varchar(64))");
        jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(64),target_type varchar(32),target_id varchar(64),is_del int,updated_time timestamp,datasource_id varchar(64))");
        jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,show_connect int,db_name varchar(64),db_type varchar(32),asset_status varchar(32))");
        jdbc.update("insert into sym_dict_t values('root','police','dataSourceType','0',0,0,'source types')");
        jdbc.update("insert into sym_dict_t values('ods','police','ods','root',0,0,'ODS')");
        jdbc.update("insert into sym_dict_t values('domain-a','police','original','ods',0,1,'原始库')");
        jdbc.update("insert into sym_dict_t values('domain-b','police','warehouse','ods',0,2,'汇聚库')");
        jdbc.update("insert into dwm_center_layer_source_t values('police','domain','domain-a',0,current_timestamp,'visible-db')");
        jdbc.update("insert into dwm_center_layer_source_t values('police','domain','domain-b',0,current_timestamp,'hidden-db')");
        jdbc.update("insert into db_datasource_t values('visible-db','police',0,1,'visible','mysql','active')");
        jdbc.update("insert into db_datasource_t values('hidden-db','police',0,1,'hidden','hive','active')");

        var scope = new Scope();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("db", db);
        inputs.put("tenantRuntime", scope);
        inputs.put("dataScope", scope);
        String script = CanonicalMagicSources.byId("ods_target_datasource_options_01")
                .replaceAll("(?m)^import [^\\n]*\\n", "");
        Map<?, ?> response = assertInstanceOf(Map.class,
                MagicScript.create(script, null).execute(new MagicScriptContext(inputs)));
        assertEquals(1, response.get("total"));
        var choices = assertInstanceOf(List.class, response.get("list"));
        assertEquals("visible-db", ((Map<?, ?>) choices.getFirst()).get("value"));
    }

    public static class Scope {
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !"hidden-db".equals(row.get("tid"))).toList();
        }
    }
}

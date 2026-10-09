package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class DatasourceCompletionMagicTest {
    @ParameterizedTest @ValueSource(ints={20,100})
    void finishPersistsOneForPartialAndTwoForAllRegistered(int count) throws Exception {
        Fixture fixture = new Fixture(count);
        Map<?,?> result = (Map<?,?>) fixture.run(false);
        assertThat(result.get("assetStatus")).isEqualTo(1);
        assertThat(fixture.status()).isEqualTo(1);
        fixture.jdbc.update("update db_table_t set asset_status=2 where tenant_id='ga'");
        result = (Map<?,?>) fixture.run(false);
        assertThat(result.get("assetStatus")).isEqualTo(2);
        assertThat(fixture.status()).isEqualTo(2);
        assertThat(fixture.scope.calls).isEqualTo(2);
        assertThat(fixture.es.calls).isEqualTo(2);
    }
    @Test void previewAndDeniedScopeNeverWrite() throws Exception {
        Fixture fixture = new Fixture(20);
        Map<?,?> result = (Map<?,?>) fixture.run(true);
        assertThat(result.get("assetStatus")).isEqualTo(1);
        assertThat(fixture.status()).isZero();
        fixture.scope.denied=true;
        assertThat(fixture.run(false)).isInstanceOf(ExitValue.class);
        assertThat(fixture.status()).isZero();
        assertThat(fixture.es.calls).isZero();
    }
    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final MetadataAssetPersistenceModule metadata;
        final ScopeRuntime scope=new ScopeRuntime();
        final IndexRuntime es=new IndexRuntime();
        Fixture(int count) {
            var source=new DriverManagerDataSource("jdbc:h2:mem:finish_"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
            jdbc=new JdbcTemplate(source);
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(32),asset_status int,flow_status int,updated_time timestamp,is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),asset_status int,is_del int)");
            jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,is_del) values('db','ga',0,0)");
            for(int i=0;i<count;i++) jdbc.update("insert into db_table_t values(?,'ga','db',?,0)","table-"+i,i==0?0:2);
            var dynamic=new MagicDynamicDataSource();dynamic.setDefault(source);
            db=new SQLModule(dynamic);db.setDataSourceNode(dynamic.getDataSource());
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("camel");
            db.setColumnMapperProvider(columns);db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);
            db.setSqlInterceptors(List.of());db.setNamedTableInterceptors(List.of());
            metadata=new MetadataAssetPersistenceModule(source);
        }
        int status() { return jdbc.queryForObject("select asset_status from db_datasource_t where tid='db'",Integer.class); }
        Object run(boolean dryRun) throws Exception {
            String script=CanonicalMagicSources.byId("datapd_save_update_datasource_01")
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            Map<String,Object> bindings=new LinkedHashMap<>();
            bindings.put("body",Map.of("tid","db","propList",Map.of("completeOnly",true,"dryRun",dryRun)));
            bindings.put("db",db);bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
            bindings.put("metadataAsset",metadata);bindings.put("esCommonService",es);
            try(var ignored=TenantContext.use("ga")) {
                return MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
            }
        }
    }
    public static class ScopeRuntime {
        int calls;boolean denied;
        public String id(){return "ga";}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){calls++;return denied?List.of():rows;}
    }
    public static class IndexRuntime {
        int calls;
        public boolean existsById(String index,String id){return true;}
        public void partialUpdateFieldsFast(String index,String id,Map<String,Object> fields){calls++;}
    }
}

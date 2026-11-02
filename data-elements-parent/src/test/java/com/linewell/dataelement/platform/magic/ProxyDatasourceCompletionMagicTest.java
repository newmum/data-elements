package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class ProxyDatasourceCompletionMagicTest {
    public static class ProxyScope extends DatasourceCompletionMagicTest.ScopeRuntime {
        String role = "ALL";
        public Map<String,Object> dataSource() {
            return Map.of("scope", role, "userId", "user-a", "organizationIds", List.of("org-a"));
        }
    }

    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture data = new DatasourceCompletionMagicTest.Fixture(0);
        final ProxyScope scope = new ProxyScope();
        final TaskCategoryWarehouseScopeMagicTest.Counter counter = new TaskCategoryWarehouseScopeMagicTest.Counter();
        Fixture() {
            data.jdbc.execute("alter table db_datasource_t add pool_cfg varchar(1000)");
            data.jdbc.update("update db_datasource_t set pool_cfg='{\"accessMode\":\"proxy\"}'");
            data.jdbc.execute("create table api_info_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),is_del int,org_id varchar(64),created_by varchar(64))");
            data.db.setSqlInterceptors(List.of(counter));
        }
        void add(String id, String tenant, String source, String org, String user, int deleted) {
            data.jdbc.update("insert into api_info_t values(?,?,?,?,?,?)", id, tenant, source, deleted, org, user);
        }
        Object run(boolean dryRun) throws Exception {
            String script = CanonicalMagicSources.byId("datapd_save_update_datasource_01")
                .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            var bindings = new LinkedHashMap<String,Object>();
            bindings.put("body", Map.of("tid", "db", "propList", Map.of("completeOnly", true, "dryRun", dryRun)));
            bindings.put("db", data.db); bindings.put("tenantRuntime", scope); bindings.put("dataScope", scope);
            bindings.put("metadataAsset", data.metadata); bindings.put("esCommonService", data.es);
            return MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
        }
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void serviceCompletionUsesTwoReadsIndependentOfServiceCount(int count) throws Exception {
        var f = new Fixture();
        for (int i=0; i<count; i++) f.add("service-"+i, "ga", "db", "org-a", "user-a", 0);
        var preview = (Map<?,?>) f.run(true);
        assertThat(((Number) preview.get("serviceCount")).intValue()).isEqualTo(count);
        assertThat(f.counter.reads).isEqualTo(2);
        assertThat(f.data.status()).isZero();
        f.counter.reads = 0;
        assertThat(((Map<?,?>) f.run(false)).get("assetStatus")).isEqualTo(2);
        assertThat(f.counter.reads).isEqualTo(2);
        assertThat(f.data.status()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"ORG","OWNER"})
    void otherTenantsSourcesDeletedServicesAndRoleScopesCannotCompleteThisSource(String role) throws Exception {
        var f = new Fixture(); f.scope.role = role;
        f.add("foreign", "other", "db", "org-a", "user-a", 0);
        f.add("wrong-source", "ga", "other", "org-a", "user-a", 0);
        f.add("deleted", "ga", "db", "org-a", "user-a", 1);
        f.add("restricted", "ga", "db", "org-b", "user-b", 0);
        assertThat(((Map<?,?>) f.run(true)).get("completionEligible")).isEqualTo(false);
        assertThat(f.run(false)).isInstanceOf(ExitValue.class);
        assertThat(f.data.status()).isZero();
        f.add("allowed", "ga", "db", "org-a", "user-a", 0);
        assertThat(f.run(false)).isInstanceOf(Map.class);
        assertThat(f.data.status()).isEqualTo(2);
    }

    @Test void parentPermissionRemainsMandatory() throws Exception {
        var f = new Fixture(); f.add("allowed", "ga", "db", "org-a", "user-a", 0);
        f.scope.denied = true;
        assertThat(f.run(false)).isInstanceOf(ExitValue.class);
        assertThat(f.data.status()).isZero();
        f.scope.denied = false;
        f.data.jdbc.update("update db_datasource_t set tenant_id='other'");
        assertThat(f.run(false)).isInstanceOf(ExitValue.class);
        assertThat(f.data.status()).isZero();
    }
}

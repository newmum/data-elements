package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** Card counts must stay batched and preserve the service scope independently of the source menu. */
class ProxyDatasourceCardCountsMagicTest {
    @ParameterizedTest
    @CsvSource({"20,ALL,ALL,3", "100,ALL,ALL,3", "20,ORG,ORG,2", "100,ORG,ORG,2",
            "20,OWNER,OWNER,1", "100,OWNER,OWNER,1", "20,ALL,OWNER,1", "100,ALL,OWNER,1"})
    void serviceCountsAreBatchedAndScoped(int count, String sourcePolicy, String servicePolicy, int expected) throws Exception {
        var f = new DatasourceManagementPermissionsMagicTest.Fixture(sourcePolicy, count);
        f.jdbc.update("delete from db_datasource_t where tid like 'dept-%' or tid like 'other-%'");
        f.jdbc.execute("create table api_info_t(tid varchar(64),tenant_id varchar(64),datasource_id varchar(64),org_id varchar(64),created_by varchar(64),is_del int)");
        for (int i = 0; i < count; i++) {
            String source = "own-" + i;
            if (i < count - 1) {
                insert(f, source + "-own", "ga", source, "dept-a", "person-a", 0);
                insert(f, source + "-dept", "ga", source, "dept-a", "person-b", 0);
                insert(f, source + "-other", "ga", source, "dept-b", "person-b", 0);
            }
            insert(f, source + "-foreign", "foreign", source, "dept-a", "person-a", 0);
            insert(f, source + "-deleted", "ga", source, "dept-a", "person-a", 1);
        }
        insert(f, "orphan", "ga", null, "dept-a", "person-a", 0);
        insert(f, "foreign-source", "ga", "foreign", "dept-a", "person-a", 0);
        insert(f, "deleted-source", "ga", "deleted", "dept-a", "person-a", 0);

        String script = CanonicalMagicSources.byId("datapd_dept_datasources_01")
                .replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "")
                .replaceAll("(?s)coalesce\\(\\s*nullif\\(json_unquote.*?\\) as accessMode", "'proxy' as accessMode")
                .replaceAll("(?s)upper\\(coalesce\\(nullif\\(json_unquote.*?\\) as pushDataType", "'STRUCTURED' as pushDataType")
                .replaceAll("(?s)coalesce\\(json_length\\(json_extract.*?\\) as reportFileCount", "0 as reportFileCount");
        script = "var me=()=>{orgId:'dept-a',orgName:'本部门'};var configData=()=>{es_dataassets:'assets'};\n" + script;
        var scope = new Scope(f.scope, servicePolicy);
        var variables = new LinkedHashMap<String,Object>();
        variables.put("body", Map.of("mode", "summary", "tenantId", "foreign", "orgId", "dept-b", "scope", "ALL"));
        variables.put("db", f.db); variables.put("tenantRuntime", f.scope);
        variables.put("tenantAccounts", f.scope); variables.put("tenantEs", f.index); variables.put("dataScope", scope);
        f.counter.reads = 0; f.counter.writes = 0; f.scope.decisions = 0; f.index.reads = 0;
        var result = (Map<?,?>) MagicScript.create(script, null).execute(new MagicScriptContext(variables));
        var rows = (List<?>) result.get("list");
        assertThat(rows).hasSize(count);
        for (Object value : rows) {
            var row = (Map<?,?>) value;
            String id = row.get("tid").toString();
            assertThat(id).startsWith("own-");
            assertThat(((Number) row.get("serviceCount")).intValue())
                    .as("service scope for %s", id).isEqualTo(id.equals("own-" + (count - 1)) ? 0 : expected);
        }
        assertThat(f.counter.reads).isEqualTo(4);
        assertThat(f.counter.writes).isZero();
        assertThat(f.scope.decisions).isEqualTo(1);
        assertThat(scope.serviceDecisions).isEqualTo(1);
        assertThat(f.index.reads).isEqualTo(2);
        System.out.printf("proxy cards=%d source=%s service=%s: SQL=4, ES=2; foreign/deleted/orphan services excluded%n", count, sourcePolicy, servicePolicy);
    }

    private static void insert(DatasourceManagementPermissionsMagicTest.Fixture f, String id, String tenant,
                               String source, String org, String creator, int deleted) {
        f.jdbc.update("insert into api_info_t values(?,?,?,?,?,?)", id, tenant, source, org, creator, deleted);
    }

    public static class Scope {
        private final DatasourceManagementPermissionsMagicTest.Scope sources;
        private final String servicePolicy;
        int serviceDecisions;
        Scope(DatasourceManagementPermissionsMagicTest.Scope sources, String servicePolicy) {
            this.sources = sources; this.servicePolicy = servicePolicy;
        }
        public Map<String,Object> resource(String resource) { return sources.resource(resource); }
        public Map<String,Object> dataSource() {
            serviceDecisions++;
            return Map.of("scope", servicePolicy, "userId", "person-a", "organizationIds", List.of("dept-a"));
        }
    }
}

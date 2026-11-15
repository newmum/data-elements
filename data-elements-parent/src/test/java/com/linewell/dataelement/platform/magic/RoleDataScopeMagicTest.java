package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class RoleDataScopeMagicTest {
    private static final String READ = "sym_scope_role_v1";
    private static final String SAVE = "sym_scope_save_v1";

    @Test
    void readsOneTenantRoleAndPreservesFallbackAndResourceLabels() throws Exception {
        var fixture = new Fixture();
        assertThat(fixture.run(READ, Map.of("roleId", "shared", "resourceCode", " menu:validation ")))
                .isEqualTo(Map.of("roleId", "shared", "scope", "ORG", "configured", true,
                        "resourceCode", "MENU:VALIDATION"));
        for (String id : new String[]{"foreign", "deleted", "missing", "legacy"}) {
            assertThat(fixture.run(READ, Map.of("roleId", id)))
                    .isEqualTo(Map.of("roleId", id, "scope", "OWNER", "configured", false,
                            "resourceCode", "DATA_SOURCE"));
        }
        assertThat(fixture.run(READ, Map.of("roleId", "disabled")).get("scope")).isEqualTo("ALL");
    }

    @Test
    void rejectsBadResourceWithoutChangingTheRoleAndRestrictsWriteToAnActiveTenantRole() throws Exception {
        var fixture = new Fixture();
        assertThatThrownBy(() -> fixture.run(SAVE,
                Map.of("roleId", "shared", "scope", "ALL", "resourceCode", "bad/path")))
                .hasStackTraceContaining("资源标识不合法");
        assertThat(fixture.stored("shared", "police")).isEqualTo("org");
        for (String id : new String[]{"foreign", "disabled", "deleted", "missing"}) {
            assertThatThrownBy(() -> fixture.run(SAVE, Map.of("roleId", id, "scope", "ALL", "tenantId", "other")))
                    .hasStackTraceContaining("角色不存在或不属于当前租户");
        }
        assertThat(fixture.run(SAVE, Map.of("roleId", "shared", "scope", " owner ", "tenantId", "other")))
                .isEqualTo(Map.of("roleId", "shared", "scope", "OWNER", "configured", true,
                        "resourceCode", "DATA_SOURCE"));
        assertThat(fixture.stored("shared", "police")).isEqualTo("OWNER");
        assertThat(fixture.stored("shared", "other")).isEqualTo("ALL");
        assertThat(fixture.stored("foreign", "other")).isEqualTo("ORG");
    }

    public static final class ScopeTenant {
        public String id() {
            return "police";
        }
    }

    private static final class Fixture {
        private final SQLModule db;
        private final JdbcTemplate jdbc;

        Fixture() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:role_scope_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    create table rm_role_t(id varchar(64),tenant_id varchar(32),data_scope varchar(32),
                        status int,deleted int,update_time timestamp,primary key(id,tenant_id))
                    """);
            jdbc.execute("""
                    insert into rm_role_t(id,tenant_id,data_scope,status,deleted) values
                    ('shared','police','org',0,0),('shared','other','ALL',0,0),('foreign','other','ORG',0,0),
                    ('disabled','police','ALL',1,0),('deleted','police','ALL',0,1),('legacy','police','unknown',0,0)
                    """);
            db = DatasourceCompletionMagicTest.magicDb(source);
            db.setDialectAdapter(new DialectAdapter());
        }

        String stored(String role, String tenant) {
            return jdbc.queryForObject("select data_scope from rm_role_t where id=? and tenant_id=?",
                    String.class, role, tenant);
        }

        Map<?, ?> run(String resource, Map<String, Object> input) throws Exception {
            String script = CanonicalMagicSources.byId(resource)
                    .replaceAll("(?m)^import (?:'@/common/[^']+' as \\w+|db|tenantRuntime)\\s*\\R", "")
                    .replace("var input = readBody(body, Map, 'java.util.Map', signature)", "var input = body")
                    .replace("return respond(() => {", "var action = () => {")
                    .replaceFirst("(?m)^}\\)\\s*$", "}\nreturn action()");
            var context = new MagicScriptContext(Map.of("body", input, "db", db, "tenantRuntime", new ScopeTenant()));
            try (var tenant = TenantContext.use("police")) {
                var response = (ResponseEntity<?>) MagicScript.create(script, null).execute(context);
                return (Map<?, ?>) ((CommonResponse<?>) response.getBody()).getData();
            }
        }
    }
}

package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.utils.StructuredValues;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.configuration.SystemConfigService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.*;

class RegistrationApplicationOptionsMagicTest {
    @ParameterizedTest @ValueSource(ints={20,100})
    void readsOnceAndRetainsTenantDepartmentAndDeletedRecordBoundaries(int count) throws Exception {
        var fixture = new DatasourceCompletionMagicTest.Fixture(0);
        fixture.jdbc.execute("""
                create table sym_application_t(
                    tid varchar(64), app_name varchar(100), tenant_id varchar(32),
                    org_id varchar(64), created_by varchar(64), is_del int
                )
                """);
        for (int i = 0; i < count; i++) {
            fixture.jdbc.update("insert into sym_application_t values(?,?,'ga','own-org','other-user',0)",
                    "app-" + i, "应用" + i);
        }
        fixture.jdbc.update("""
                insert into sym_application_t values
                    ('foreign','跨租户','other','own-org','user',0),
                    ('other-org','其他部门','ga','other-org','user',0),
                    ('different-case','权限大小写','ga','OWN-ORG','user',0),
                    ('deleted','已删除','ga','own-org','user',1),
                    ('null-name',null,'ga','own-org',null,null),
                    ('tie-z','同名应用','ga','own-org','user',0),
                    ('tie-a','同名应用','ga','own-org','user',0),
                    ('second-org','兼任部门','ga','second-org','other-user',0),
                    ('no-org','未归属','ga',null,null,0)
                """);
        var mapper = mock(IdentityUserLookupMapper.class);
        when(mapper.selectOrganizationIds("user", "ga")).thenReturn(Arrays.asList(" own-org ", "second-org", "own-org", null));
        var scope = spy(new DataScopeAuthorizationService(mock(SystemConfigService.class), mapper, new ObjectMapper()));
        var counter = new TaskCategoryWarehouseScopeMagicTest.Counter();
        fixture.db.setSqlInterceptors(List.of(counter));
        String resource = "MENU:2081000000000000002";
        String script = CanonicalMagicSources.byId("application_register_options_01")
                .replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
        var inputs = Map.of("tenantRuntime", fixture.scope, "dataScope", new DataScopeMagicModule(scope), "db", fixture.db);
        var oldOrderedRows = fixture.jdbc.query("""
                select app_name, tid, org_id, created_by from sym_application_t
                where tenant_id='ga' and coalesce(is_del,0)=0 order by app_name, tid
                """, (row, index) -> {
            var dto = new LinkedHashMap<String, Object>();
            dto.put("label", row.getString(1));
            dto.put("value", row.getString(2));
            dto.put("orgid", row.getString(3));
            dto.put("createdby", row.getString(4));
            return dto;
        });

        try (var ignored = TenantContext.use("ga")) {
            for (Scope roleScope : List.of(Scope.OWNER, Scope.ORG, Scope.ALL)) {
                var decision = new ScopeDecision(resource, roleScope, "user",
                        roleScope == Scope.ORG ? List.of("own-org", "second-org") : List.of(), List.of("role"), true);
                doReturn(decision).when(scope).currentScope(resource);
                var expected = roleScope == Scope.ALL ? oldOrderedRows : oldOrderedRows.stream()
                        .filter(row -> "own-org".equals(row.get("orgid")) || "second-org".equals(row.get("orgid")))
                        .toList();
                for (var body : List.of(Map.<String, Object>of(), Map.<String, Object>of(
                        "tenantId", "other", "orgId", "other-org", "scope", "ALL", "keyword", "not-present", "limit", 1))) {
                    var bindings = new LinkedHashMap<>(inputs);
                    bindings.put("body", body);
                    int before = counter.reads;
                    var rows = StructuredValues.objectRows(MagicScript.create(script, null)
                            .execute(new MagicScriptContext(bindings)));
                    assertThat(counter.reads - before).isEqualTo(1);
                    assertThat(counter.sql.getLast()).contains("where", "tenant_id", "order by app_name asc", "tid asc");
                    assertThat(rows).containsExactlyElementsOf(expected);
                }
                System.out.println("registration applications=" + count + ", role=" + roleScope
                        + ", application SQL=1; historical DTO, order and exact organization boundary retained");
            }
            doReturn(new ScopeDecision(resource, Scope.OWNER, "no-membership", List.of(), List.of(), false))
                    .when(scope).currentScope(resource);
            assertThat(StructuredValues.objectRows(MagicScript.create(script, null)
                    .execute(new MagicScriptContext(new LinkedHashMap<>(inputs))))).isEmpty();
        }
    }
}

package com.linewell.dataelement.platform.magic;

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
        var testSource=(org.springframework.jdbc.datasource.DriverManagerDataSource)fixture.jdbc.getDataSource();
        testSource.setUrl(testSource.getUrl()+";NON_KEYWORDS=VALUE");
        fixture.jdbc.execute("create table sym_application_t(tid varchar(64),app_name varchar(100),tenant_id varchar(32),org_id varchar(64),created_by varchar(64),is_del int)");
        for (int i=0;i<count;i++) fixture.jdbc.update("insert into sym_application_t values(?,?,'ga','own-org','other-user',0)","app-"+i,"应用"+i);
        fixture.jdbc.update("insert into sym_application_t values('foreign','跨租户','other','own-org','user',0),('other-org','其他部门','ga','other-org','user',0),('deleted','已删除','ga','own-org','user',1)");
        var mapper=mock(IdentityUserLookupMapper.class);
        when(mapper.selectOrganizationIds("user","ga")).thenReturn(List.of("own-org"));
        var scope=spy(new DataScopeAuthorizationService(mock(SystemConfigService.class),mapper,new ObjectMapper()));
        var counter=new TaskCategoryWarehouseScopeMagicTest.Counter();
        fixture.db.setSqlInterceptors(List.of(counter));
        String script=RegistrationAccessMagicTest.source("api/02.数据盘点/01.应用系统/09.登记所属系统选择项.ms");
        var bindings=new LinkedHashMap<String,Object>();
        bindings.put("tenantRuntime",fixture.scope);bindings.put("dataScope",new DataScopeMagicModule(scope));bindings.put("db",fixture.db);
        // Client fields cannot replace the authenticated tenant or department.
        bindings.put("body",Map.of("tenantId","other","orgId","other-org"));
        try(var ignored=TenantContext.use("ga")) {
            for(Scope roleScope:List.of(Scope.OWNER,Scope.ORG,Scope.ALL)) {
                doReturn(new ScopeDecision("MENU:2081000000000000002",roleScope,"user",List.of("own-org"),List.of("role"),true)).when(scope).currentScope(anyString());
                int before=counter.reads;
                var rows=(List<Map<String,Object>>)MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
                assertThat(counter.reads-before).isEqualTo(1);
                assertThat(rows).hasSize(roleScope==Scope.ALL?count+1:count);
                assertThat(rows).extracting(row->row.get("value")).doesNotContain("foreign","deleted");
                if(roleScope!=Scope.ALL)assertThat(rows).extracting(row->row.get("value")).doesNotContain("other-org");
                System.out.println("registration applications="+count+", role="+roleScope+", SQL=1; tenant/department/deleted boundaries retained");
            }
        }
    }
}

package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.session.SaSession;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the actual context script; no database, servlet container or lasting cache. */
class IdaasBootstrapPermissionTest {
    private final DbStub db = new DbStub();
    private final Map<String,Object> attributes = new LinkedHashMap<>();
    private final PlatformSessionMagicModule platform = mock(PlatformSessionMagicModule.class);
    private final MagicHttpServletRequest http = mock(MagicHttpServletRequest.class);

    @BeforeEach
    void setUp() {
        when(platform.isLogin()).thenReturn(true);
        when(platform.id()).thenReturn("operator-1");
        SaSession session=mock(SaSession.class);
        when(session.get("securityVersion")).thenReturn(1);
        when(platform.session()).thenReturn(session);
        when(http.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> {
            String key=call.getArgument(0);Object value=call.getArgument(1);
            if(value==null)attributes.remove(key);else attributes.put(key,value);
            return null;
        }).when(http).setAttribute(anyString(),any());
    }

    @Test
    void bootstrapSharesPermissionsButNeverMutableGuardFields() throws Exception {
        attributes.put("idaas.bootstrap.permissionSnapshot",new LinkedHashMap<>(Map.of("operatorId","operator-1")));
        Map<String,Object> first=read();
        first.put("requiredPermission","users:read");
        first.put("requiredAppId","app-1");
        Map<String,Object> second=read();
        assertEquals(4,db.queries);
        assertNotSame(first,second);
        assertFalse(second.containsKey("requiredPermission"));
        assertFalse(second.containsKey("requiredAppId"));
        assertEquals(List.of("users:read"), first.get("permissions"));
        assertEquals(first.get("permissions"),second.get("permissions"));
    }

    @Test
    void followingRequestAndOrdinaryEndpointsRecheckRevokedSecurityVersion() throws Exception {
        attributes.put("idaas.bootstrap.permissionSnapshot",new LinkedHashMap<>(Map.of("operatorId","operator-1")));
        read();attributes.clear();db.version=2;
        assertInstanceOf(ExitValue.class,execute());
        verify(platform).logout();
        assertEquals(5,db.queries);
    }

    @Test
    void actualBootstrapFinallyClearsSnapshotWhenMagicExits() throws Exception {
        String source=CanonicalMagicSources.byId("9d76df5fd4ad5e05ba9ff2f10dfa75e4")
                .replaceAll("(?m)^import (db|platformSession|request|identityRuntime);\\r?\\n","")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n","");
        String script="var apiCall=(callback)=>callback();"
                +"var sessionInfo=()=>{exit 403,'denied';};\n"+source;
        Map<String,Object> args=Map.of("db",db,"platformSession",platform,"request",new RequestStub(http),
                "identityRuntime",new ControlStub());
        Object previous=Map.of("previous","sentinel");
        attributes.put("idaas.bootstrap.permissionSnapshot",previous);
        assertInstanceOf(ExitValue.class,MagicScript.create(script,null).execute(new MagicScriptContext(args)));
        assertSame(previous,attributes.get("idaas.bootstrap.permissionSnapshot"));
        attributes.clear();
        assertInstanceOf(ExitValue.class,MagicScript.create(script,null).execute(new MagicScriptContext(args)));
        assertFalse(attributes.containsKey("idaas.bootstrap.permissionSnapshot"));
    }

    @Test
    void differentOperatorCannotReuseSnapshotAndUnscopedReadsNeverCache() throws Exception {
        attributes.put("idaas.bootstrap.permissionSnapshot",new LinkedHashMap<>(Map.of("operatorId","other-operator","context",Map.of("userId","other-operator"))));
        assertEquals("operator-1",read().get("userId"));
        attributes.clear();read();read();assertEquals(12,db.queries);
    }

    @TestFactory
    List<DynamicTest> twentyAndHundredRoleAssignmentsUseFourQueriesWithoutPermissionLoss() {
        return List.of(20,100).stream().map(size -> DynamicTest.dynamicTest("assignments="+size, () -> {
            attributes.clear();db.queries=0;db.assignments=size;
            var context=read();
            assertEquals(4,db.queries);
            assertEquals(size,((List<?>)context.get("pairs")).size());
            assertEquals(List.of("users:read"),context.get("permissions"));
        })).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> read() throws Exception {return (Map<String,Object>)assertInstanceOf(Map.class,execute());}

    private Object execute() throws Exception {
        String source=CanonicalMagicSources.byId("c6d4f90ef64c535784d7f1df4030c92f")
                .replaceAll("(?m)^import (db|platformSession|request|identityRuntime);\\r?\\n","");
        return MagicScript.create(source,null).execute(new MagicScriptContext(Map.of("db",db,"platformSession",platform,"request",new RequestStub(http))));
    }

    public static class RequestStub {
        private final MagicHttpServletRequest request;
        public RequestStub(MagicHttpServletRequest request){this.request=request;}
        public MagicHttpServletRequest get(){return request;}
    }

    public static class ControlStub {
        public Object control(Supplier<Object> action) { return action.get(); }
    }

    public static class DbStub {
        int queries;int version=1;int assignments=1;
        public DbStub normal(){return this;}
        public Map<String,Object> selectOne(String sql){queries++;return Map.of("security_version",version,"must_change_password",0,"username","operator-1");}
        public List<?> select(String sql){
            queries++;
            if(sql.contains("iam_operator_role_rela_t"))return IntStream.range(0,assignments)
                    .mapToObj(i -> Map.of("id","assignment-"+i,"role_id","role-"+i)).toList();
            if(sql.contains("iam_role_resource_rela_t"))return IntStream.range(0,assignments)
                    .mapToObj(i -> Map.of("role_id","role-"+i,"code","users:read")).toList();
            return IntStream.range(0,assignments).mapToObj(i -> Map.of("assignment_id","assignment-"+i,
                    "identity_domain","workforce","scope_kind","ALL")).toList();
        }
    }
}

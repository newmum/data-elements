package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.session.SaSession;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        String source=Files.readString(Path.of("db/migrations/resources/idaas-foundation-20260928/functions/bootstrap.ms"))
                .replaceAll("(?m)^import (db|platformSession|request);\\r?\\n","")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n","");
        String script="var sessionInfo=()=>{exit 403,'denied';};\n"+source;
        Map<String,Object> args=Map.of("db",db,"platformSession",platform,"request",new RequestStub(http));
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

    @SuppressWarnings("unchecked")
    private Map<String,Object> read() throws Exception {return (Map<String,Object>)assertInstanceOf(Map.class,execute());}

    private Object execute() throws Exception {
        String source=Files.readString(Path.of("db/migrations/resources/idaas-foundation-20260928/functions/context.ms"))
                .replaceAll("(?m)^import (db|platformSession|request);\\r?\\n","");
        return MagicScript.create(source,null).execute(new MagicScriptContext(Map.of("db",db,"platformSession",platform,"request",new RequestStub(http))));
    }

    public static class RequestStub {
        private final MagicHttpServletRequest request;
        public RequestStub(MagicHttpServletRequest request){this.request=request;}
        public MagicHttpServletRequest get(){return request;}
    }

    public static class DbStub {
        int queries;int version=1;
        public DbStub normal(){return this;}
        public Map<String,Object> selectOne(String sql){queries++;return Map.of("security_version",version,"must_change_password",0,"username","operator-1");}
        public List<?> select(String sql){
            queries++;
            if(sql.contains("iam_operator_role_rela_t"))return List.of(Map.of("id","assignment-1","role_id","role-1"));
            if(sql.contains("iam_role_resource_rela_t"))return List.of(Map.of("code","users:read"));
            return List.of(Map.of("identity_domain","workforce","scope_kind","ALL"));
        }
    }
}

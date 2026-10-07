package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.session.SaSession;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** Execute the real Magic session DTO against disjoint role/scope pairs. */
class IdaasSessionNavigationTest {
    @Test
    void menuDiscoveryDoesNotTransferPermissionsBetweenDomainsOrGrantWrites() throws Exception {
        var result = execute("workforce", false, List.of(
                pair(List.of("users:read", "users:write"), "workforce", "ALL"),
                pair(List.of("apps:read", "apps:write"), "public", "APPLICATION")));
        assertEquals(List.of("users:read", "users:write"), result.get("permissions"));
        var nav = navigation(result);
        assertEquals(List.of("users:read"), nav.get("workforce").get("permissions"));
        assertEquals(List.of("apps:read"), nav.get("public").get("permissions"));
        assertEquals(List.of(), nav.get("public").get("globalPermissions"));
        assertEquals(List.of(), result.get("appWriteIds"));
    }

    @Test
    void publicContextStillHasOnlyItsOwnEffectivePermissions() throws Exception {
        var result = execute("public", false, List.of(
                pair(List.of("users:read", "users:write"), "workforce", "ALL"),
                pair(List.of("public:read"), "public", "ALL")));
        assertEquals(List.of("public:read"), result.get("permissions"));
        assertEquals(List.of("public:read"), result.get("globalPermissions"));
        assertFalse(((List<?>) result.get("editableTables")).contains("users"));
        assertTrue(navigation(result).containsKey("workforce"));
    }

    @Test
    void unassignedDomainIsNotAdvertisedEvenForAdminRoleLabel() throws Exception {
        var result = execute("workforce", false, List.of(pair(List.of("users:read"), "workforce", "ALL")));
        assertEquals("admin", result.get("role"));
        assertFalse(navigation(result).containsKey("public"));
    }

    @Test
    void forcedPasswordChangeDoesNotAdvertiseOtherBusinessDomains() throws Exception {
        var result = execute("workforce", true, List.of(pair(List.of("public:read", "public:write"), "public", "ALL")));
        assertTrue(navigation(result).isEmpty());
        assertEquals(List.of("workspace", "profile"), result.get("capabilities"));
    }

    private Map<String, Object> pair(List<String> codes, String domain, String kind) {
        return Map.of("codes", codes, "scopes", List.of(Map.of("identity_domain", domain, "scope_kind", kind, "app_id", "app-1")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, List<String>>> navigation(Map<String, Object> result) {
        return (Map<String, Map<String, List<String>>>) result.get("navigationPermissions");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(String domain, boolean forceChange, List<?> pairs) throws Exception {
        var platform = mock(PlatformSessionMagicModule.class);
        var session = mock(SaSession.class);
        when(platform.session()).thenReturn(session);
        when(session.get("identityDomain")).thenReturn(domain);
        var fixture = Map.of("userId", "operator-1", "account", Map.of("username", "operator-1", "display_name", "平台管理员", "must_change_password", forceChange ? 1 : 0, "security_version", 1, "version", 1), "pairs", pairs);
        String source = CanonicalMagicSources.byId("f1b2b970b6195bf8aad27cdded34304d")
                .replaceAll("(?m)^import (db|platformSession);\\r?\\n", "")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n", "");
        String prefix = "var context=()=>fixture;var directoryScope=(permission,domain)=>{return {all:true,orgIds:[]};};\n";
        return (Map<String, Object>) assertInstanceOf(Map.class, MagicScript.create(prefix + source, null).execute(new MagicScriptContext(Map.of("fixture", fixture, "platformSession", platform, "db", new DbStub()))));
    }

    public static class DbStub {
        public DbStub normal() { return this; }
        public List<?> select(String sql) { return List.of(Map.of("id", "app-1")); }
    }
}

package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;

/** Execute the maintained listing branch with the deployed Magic collection runtime. */
class IdaasBusinessCatalogScopeTest {
    public static class Database {
        int reads; String statement;
        public Database normal() { return this; }
        public List<Object> select(String sql) { reads++; statement=sql; return List.of(); }
    }
    @Test void unrelatedRolesAndOtherIdentityDomainsCannotGrantGlobalCatalogAccess() throws Exception {
        var db=new Database();
        var pairs=List.of(
                pair("roles:read","workforce","APPLICATION","permitted-app"),
                pair("resources:read","workforce","ALL",null),
                pair("roles:read","public","ALL",null));
        assertEquals(List.of(),read(db,pairs)); assertEquals(1,db.reads);
        assertTrue(db.statement.contains("t.app_id IN (#{scopedAppIds})"));
    }
    @Test void missingApplicableScopeReturnsNoRowsWithoutReadingCatalogTables() throws Exception {
        var db=new Database();
        assertEquals(List.of(),read(db,List.of(pair("roles:read","public","ALL",null))));
        assertEquals(0,db.reads);
    }
    private static Map<String,Object> pair(String permission,String domain,String kind,String app) {
        var scope=new HashMap<String,Object>();scope.put("identity_domain",domain);scope.put("scope_kind",kind);scope.put("app_id",app);
        return Map.of("codes",List.of(permission),"scopes",List.of(scope));
    }
    private static Object read(Database db,List<Map<String,Object>> pairs) throws Exception {
        String source=CanonicalMagicSources.byId("5ce8c6b8682853419b0fe1db8d286d9a").replaceAll("(?m)^import [^\\r\\n]+;\\r?\\n","");
        return MagicScript.create("var listContext=(permission,appId,domain)=>({pairs:pairs});\n"+source,null)
                .execute(new MagicScriptContext(Map.of("db",db,"pairs",pairs,"entity","roles","operation","list","payload",Map.of("domain","workforce"))));
    }
}

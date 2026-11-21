package com.haitong;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import static org.junit.jupiter.api.Assertions.*;

/** Compile every shipped script and guard against silent loss/duplication of public mappings. */
class PushOperationsMagicTest {
    @Test void allBusinessRoutesAreOwnedByMagicAndScriptsCompile() throws Exception {
        Path root=Path.of("../magic/haitong");
        var json=new com.haitong.core.Json();var ids=new HashSet<String>();var routes=new HashSet<String>();
        var groups=new HashMap<String,Map<String,Object>>();
        try(var paths=Files.walk(root)){for(Path path:paths.filter(p->p.endsWith("group.json")).toList()){
            var meta=json.object(Files.readAllBytes(path));
            assertNull(groups.put(meta.get("id").toString(),meta),path.toString());
        }}
        int migrated=0;
        try(var paths=Files.walk(root)){for(Path path:paths.filter(p->p.toString().endsWith(".ms")).toList()){
            var parts=Files.readString(path,StandardCharsets.UTF_8).split("================================",2);
            var meta=json.object(parts[0].getBytes(StandardCharsets.UTF_8));assertTrue(ids.add(meta.get("id").toString()),path.toString());
            assertDoesNotThrow(()->MagicScript.create(parts[1],null).compile(),path.toString());
            if(meta.containsKey("method")){
                var leaf=groups.get(meta.get("groupId"));
                var parent=groups.get(leaf.get("parentId"));
                assertEquals("0",parent.get("parentId"),path.toString());
                for(var group:List.of(parent,leaf)){
                    assertEquals("api",group.get("type"));
                    assertTrue(group.get("path").toString().matches("/[A-Za-z0-9_-]+"),path.toString());
                }
                String suffix=meta.get("path").toString();
                for (String segment : List.of(parent.get("path").toString(), leaf.get("path").toString(), suffix)) {
                    assertTrue(segment.matches("/[a-z][a-z0-9]*"), path.toString());
                }
                String route=meta.get("method")+" "+parent.get("path")+leaf.get("path")+suffix;
                assertTrue(routes.add(route),route);
                if(route.contains(" /api/v1/")||route.contains(" /api/file/")||route.contains(" /api/push/")||route.contains(" /internal/"))migrated++;
            }
        }}
        // The standalone transport maps both legacy single-file addresses to one Magic resource.
        assertEquals(42,migrated);
        assertEquals(61,routes.size());
        assertTrue(routes.contains("GET /api/file/content"));
        assertFalse(routes.contains("HEAD /api/file/content"));
        assertTrue(routes.containsAll(Set.of(
            "GET /gateway/config/check", "POST /gateway/token/status", "POST /gateway/data/push",
            "POST /ftp/connect/probe", "POST /push/debug/send", "POST /push/message/status", "POST /push/template/test",
            "POST /api/v1/push", "POST /api/v1/ftp", "POST /api/v1/kafka", "POST /api/v1/log",
            "POST /internal/credentials/issue", "POST /validation/push/status", "POST /validation/push/org",
            "POST /validation/push/eventlog",
            "POST /api/v1/ingest", "POST /api/v1/action", "GET /api/v1/message", "POST /api/v1/import", "GET /api/v1/file",
            "PUT /api/push/single", "POST /api/push/init", "PUT /api/push/part",
            "POST /api/push/complete", "GET /api/push/status", "GET /api/push/parts")));
        assertFalse(routes.contains("PUT /api/v1/data-push"));
        assertFalse(routes.contains("PUT /api/v1/data-push/single"));
        try(var paths=Files.walk(Path.of("src/main/java"))){for(Path path:paths.filter(p->p.toString().endsWith(".java")).toList()){
            assertFalse(Files.readString(path).contains("@RestController\n"),path.toString());
        }}
    }
}

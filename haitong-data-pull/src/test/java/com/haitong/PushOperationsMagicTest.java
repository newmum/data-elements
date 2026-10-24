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
        var json=new com.haitong.core.Json();var ids=new HashSet<String>();var routes=new HashSet<String>();var groups=new HashMap<String,String>();
        try(var paths=Files.walk(root)){for(Path path:paths.filter(p->p.endsWith("group.json")).toList()){
            var meta=json.object(Files.readAllBytes(path));groups.put(meta.get("id").toString(),meta.get("path").toString());
        }}
        int migrated=0;
        try(var paths=Files.walk(root)){for(Path path:paths.filter(p->p.toString().endsWith(".ms")).toList()){
            var parts=Files.readString(path,StandardCharsets.UTF_8).split("================================",2);
            var meta=json.object(parts[0].getBytes(StandardCharsets.UTF_8));assertTrue(ids.add(meta.get("id").toString()),path.toString());
            assertDoesNotThrow(()->MagicScript.create(parts[1],null).compile(),path.toString());
            if(meta.containsKey("method")){
                String route=meta.get("method")+" "+groups.get(meta.get("groupId"))+meta.get("path");assertTrue(routes.add(route),route);
                if(route.contains(" /api/v1/")||route.contains(" /internal/"))migrated++;
            }
        }}
        assertEquals(37,migrated);
        try(var paths=Files.walk(Path.of("src/main/java"))){for(Path path:paths.filter(p->p.toString().endsWith(".java")).toList()){
            assertFalse(Files.readString(path).contains("@RestController\n"),path.toString());
        }}
    }
}

package com.haitong.magic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** One classpath inventory for file and database stores; never overwrites existing resources. */
final class MagicResourceCatalog {
    private MagicResourceCatalog() {}
    static List<String> files() {
        try {
            var resources=new PathMatchingResourcePatternResolver().getResources("classpath*:magic-api-bootstrap/**/*");
            var files=new java.util.TreeSet<String>();
            for(var resource:resources){
                String url=java.net.URLDecoder.decode(resource.getURL().toString(),StandardCharsets.UTF_8);
                int start=url.lastIndexOf("magic-api-bootstrap/");
                if(start<0)continue;
                String path=url.substring(start+"magic-api-bootstrap/".length());
                if(path.endsWith(".ms")||path.endsWith("/group.json"))files.add(path);
            }
            if(files.isEmpty())throw new IllegalStateException("Missing Magic resource catalog");
            return List.copyOf(files);
        } catch(IOException e){throw new IllegalStateException("Cannot inventory Magic resources",e);}
    }
    static List<String> directories(){
        var directories=new java.util.TreeSet<String>();directories.add("");
        for(String path:files())for(int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1))directories.add(path.substring(0,slash+1));
        return List.copyOf(directories);
    }
}

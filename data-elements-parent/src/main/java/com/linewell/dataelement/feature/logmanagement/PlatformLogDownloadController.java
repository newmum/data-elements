package com.linewell.dataelement.feature.logmanagement;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.ssssssss.magicapi.core.service.MagicAPIService;

/** Transport for binary output; the platform Magic guard still owns business authorization. */
@RestController
public class PlatformLogDownloadController {
    private final ObjectProvider<MagicAPIService> magic;
    private final LogAccessService access;
    private final LogLifecycleService lifecycle;
    public PlatformLogDownloadController(ObjectProvider<MagicAPIService> magic,LogAccessService access,LogLifecycleService lifecycle) {this.magic=magic;this.access=access;this.lifecycle=lifecycle;}
    @GetMapping("/idaas/logs/export-download") public ResponseEntity<Resource> download(@RequestParam String id,@RequestParam(defaultValue="workforce") String domain) {
        Map<String,Object> context=magic.getObject().invoke("/idaas/log-guard",Map.of("capability","exports","domain",domain));
        var path=lifecycle.download(access.platform(context,"exports"),id);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(path.toString().endsWith(".csv")?"text/csv;charset=UTF-8":"application/x-ndjson;charset=UTF-8")).header("Content-Disposition","attachment; filename=\""+path.getFileName()+"\"").header("Cache-Control","no-store").body(new FileSystemResource(path));
    }
}

package com.linewell.dataelement.platform.web.download;

import com.linewell.dataelement.feature.logmanagement.LogAccessService;
import com.linewell.dataelement.feature.logmanagement.LogLifecycleService;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.magicapi.core.service.MagicAPIService;

/** 负责行业和平台日志文件的 HTTP 流式响应。授权仍按各自身份域执行。 */
@RestController
public class LogDownloadController {
    private final ObjectProvider<MagicAPIService> magic;
    private final LogAccessService access;
    private final LogLifecycleService lifecycle;

    public LogDownloadController(ObjectProvider<MagicAPIService> magic, LogAccessService access,
                                 LogLifecycleService lifecycle) {
        this.magic = magic;
        this.access = access;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/sym/logs/exports/{id}/download")
    public ResponseEntity<Resource> industry(@PathVariable String id) {
        return attachment(lifecycle.download(access.current("exports"), id));
    }

    @GetMapping("/idaas/logs/export-download")
    public ResponseEntity<Resource> platform(@RequestParam String id, @RequestParam(defaultValue = "workforce") String domain) {
        Map<String, Object> context = magic.getObject().invoke("/idaas/log-guard",
                Map.of("capability", "exports", "domain", domain));
        return attachment(lifecycle.download(access.platform(context, "exports"), id));
    }

    private static ResponseEntity<Resource> attachment(Path path) {
        String fileName = path.getFileName().toString();
        String type = fileName.endsWith(".csv") ? "text/csv;charset=UTF-8" : "application/x-ndjson;charset=UTF-8";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .header("Cache-Control", "no-store")
                .body(new FileSystemResource(path));
    }
}

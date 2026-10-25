package com.linewell.dataelement.feature.logmanagement;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sym/logs")
public class LogController {
    private final LogAccessService access;
    private final LogQueryService queries;
    private final LogComponentService components;
    private final LogLifecycleService lifecycle;
    private final LogArchiveService archives;
    public LogController(LogAccessService access,LogQueryService queries,LogComponentService components,LogLifecycleService lifecycle,LogArchiveService archives) {
        this.access=access;this.queries=queries;this.components=components;this.lifecycle=lifecycle;this.archives=archives;
    }
    private Map<String,Object> response(Object value) { return Map.of("code",0,"success",true,"data",value); }
    @GetMapping("/components") public Object components(@RequestParam(defaultValue="auth") String kind) { if(access.capabilities().isEmpty())throw new SecurityException("没有日志访问权限");return response(components.components()); }
    @GetMapping("/capabilities") public Object capabilities() {return response(access.capabilities());}
    @GetMapping("/{kind:auth|operations|requests|security}") public Object page(@PathVariable String kind,@RequestParam Map<String,Object> input) { return response(queries.page(access.current(kind),kind,input)); }
    @GetMapping("/{kind:auth|operations|requests|security}/statistics") public Object statistics(@PathVariable String kind,@RequestParam Map<String,Object> input) { return response(queries.statistics(access.current(kind),kind,input)); }
    @GetMapping("/{kind:auth|operations|requests|security}/{id}") public Object detail(@PathVariable String kind,@PathVariable String id) { return response(queries.detail(access.current(kind),kind,id)); }
    @GetMapping("/{kind:auth|operations|requests|security}/{id}/items") public Object items(@PathVariable String kind,@PathVariable String id,@RequestParam Map<String,Object> input){return response(queries.batchItems(access.current(kind),kind,id,input));}
    @PostMapping("/{kind:auth|operations|requests|security}/batch") public Object batch(@PathVariable String kind,@RequestBody List<String> ids) { return response(queries.authorizedEvents(access.current(kind),kind,ids,false)); }
    @GetMapping("/{module:exports|archives|policies}") public Object lifecycle(@PathVariable String module,@RequestParam Map<String,Object> input) {return response(lifecycle.page(access.current(module),module,input));}
    @PostMapping("/exports") public Object export(@RequestBody Map<String,Object> input) {return response(lifecycle.createExport(access.current("exports"),input));}
    @PostMapping("/archives") public Object archive(@RequestBody Map<String,Object> input) {return response(lifecycle.createArchive(access.current("archives"),input));}
    @PostMapping("/archives/{id}") public Object archiveAction(@PathVariable String id,@RequestBody Map<String,Object> input){return response(archives.action(access.current("archives"),id,input));}
    @RequestMapping(value="/policies",method={RequestMethod.PUT,RequestMethod.POST}) public Object policy(@RequestBody Map<String,Object> input) {return response(lifecycle.savePolicy(access.current("policies"),input));}
    @GetMapping("/exports/{id}/download") public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable String id) {
        var path=lifecycle.download(access.current("exports"),id);
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(path.toString().endsWith(".csv")?"text/csv;charset=UTF-8":"application/x-ndjson;charset=UTF-8")).header("Content-Disposition","attachment; filename=\""+path.getFileName()+"\"").header("Cache-Control","no-store").body(new org.springframework.core.io.FileSystemResource(path));
    }
}

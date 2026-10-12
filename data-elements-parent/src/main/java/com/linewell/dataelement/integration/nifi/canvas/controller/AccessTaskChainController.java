package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.lifecycle.AccessTaskChainService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Combined display does not merge, repair, save, deploy or start the underlying pipelines. */
@RestController
@RequestMapping("/nifi/api/pipelines")
public class AccessTaskChainController {
    private final AccessTaskChainService service;
    public AccessTaskChainController(AccessTaskChainService service) { this.service = service; }

    @GetMapping("/task-chain")
    public Map<String, Object> taskChain(@RequestParam String accessTaskId) { return service.byTask(accessTaskId); }

    @GetMapping("/{id}/chain")
    public Map<String, Object> pipelineChain(@PathVariable String id) { return service.byPipeline(id); }

    @GetMapping("/task-chain/native")
    public Map<String, Object> nativeChain(@RequestParam String accessTaskId) { return service.nativeByTask(accessTaskId); }
}

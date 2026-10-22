package com.linewell.dataelement.dataservice.flowserve;

import com.linewell.dataelement.model.common.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Canvas initialization uses the current tenant's authoritative flow and runtime-node tables. */
@RestController
@RequestMapping("/dws/flowserve")
public class FlowServeCanvasReadController {
    private final FlowServeCanvasReadService service;
    public FlowServeCanvasReadController(FlowServeCanvasReadService service) { this.service = service; }

    @GetMapping("/flows/{id}")
    public CommonResponse<?> flow(@PathVariable String id) { return CommonResponse.success(service.flow(id)); }

    @GetMapping("/runtime-nodes")
    public CommonResponse<?> runtimeNodes() { return CommonResponse.success(service.runtimeNodes()); }
}

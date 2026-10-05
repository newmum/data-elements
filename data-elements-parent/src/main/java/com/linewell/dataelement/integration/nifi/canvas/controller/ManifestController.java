package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/nifi/api/manifests")
@Api(tags = "NiFi 组件清单")
public class ManifestController {

    private final ManifestRegistry registry;

    public ManifestController(ManifestRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "查询组件清单(list)")
    public Collection<ComponentManifest> list() {
        return registry.all();
    }

    @GetMapping("/{key}")
    @Operation(summary = "查询单个组件清单(get)")
    public ResponseEntity<ComponentManifest> get(@PathVariable String key) {
        ComponentManifest m = registry.get(key);
        return m == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(m);
    }
}

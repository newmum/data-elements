package com.linewell.dataelement.feature.metadatagovernance.api;

import com.linewell.dataelement.feature.metadatagovernance.application.MetadataGovernanceService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bounded reads for the metadata catalog table browser. Tenant comes from the authenticated request. */
@RestController
@RequestMapping("/dwm/metadata-governance")
public class MetadataCatalogPageController {
    private final MetadataGovernanceService service;

    public MetadataCatalogPageController(MetadataGovernanceService service) { this.service = service; }

    @GetMapping("/tables/page")
    public Map<String, Object> tablesPage(@RequestParam(value = "sourceIds", required = false) List<String> sourceIds,
                                          @RequestParam(value = "tableId", required = false) String tableId,
                                          @RequestParam(value = "kind", required = false) String kind,
                                          @RequestParam(value = "keyword", required = false) String keyword,
                                          @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "15") int pageSize) {
        return success(service.catalogTablesPage(sourceIds, kind, keyword, pageNo, pageSize, tableId));
    }

    @GetMapping("/tables/columns")
    public Map<String, Object> columns(@RequestParam("tableId") String tableId) {
        return success(service.catalogTableColumns(tableId));
    }

    private static Map<String, Object> success(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "success");
        result.put("data", data);
        return result;
    }
}

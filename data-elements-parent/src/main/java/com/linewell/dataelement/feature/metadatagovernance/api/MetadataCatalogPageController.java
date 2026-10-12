package com.linewell.dataelement.feature.metadatagovernance.api;

import com.linewell.dataelement.feature.metadatagovernance.application.MetadataGovernanceService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Bounded reads for the metadata catalog table browser. Tenant comes from the authenticated request. */
@RestController
@RequestMapping("/dwm/metadata-governance")
public class MetadataCatalogPageController {
    private final MetadataGovernanceService service;

    public MetadataCatalogPageController(MetadataGovernanceService service) { this.service = service; }

    @GetMapping("/catalog-services/page")
    public Map<String,Object> catalogServices(@RequestParam String catalogId,@RequestParam(defaultValue="1") int pageNo,
        @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String sortField,@RequestParam(required=false) String sortDir){
        return success(service.catalogServices(catalogId,pageNo,pageSize,sortField,sortDir));
    }

    @GetMapping("/catalog-resources/page")
    public Map<String,Object> catalogResources(@RequestParam String catalogId,@RequestParam(defaultValue="1") int pageNo,
        @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String sortField,@RequestParam(required=false) String sortDir){
        return success(service.catalogResources(catalogId,pageNo,pageSize,sortField,sortDir));
    }

    @GetMapping("/tables/page")
    public Map<String, Object> tablesPage(@RequestParam(value = "sourceIds", required = false) List<String> sourceIds,
                                          @RequestParam(value = "tableId", required = false) String tableId,
                                          @RequestParam(value = "kind", required = false) String kind,
                                          @RequestParam(value = "keyword", required = false) String keyword,
                                          @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "15") int pageSize,
                                          @RequestParam(value = "sortField", required = false) String sortField,
                                          @RequestParam(value = "sortDir", required = false) String sortDir) {
        return success(service.catalogTablesPage(sourceIds, kind, keyword, pageNo, pageSize, tableId, sortField, sortDir));
    }

    @GetMapping("/tables/columns")
    public Map<String, Object> columns(@RequestParam("tableId") String tableId,
                                       @RequestParam(value = "sortField", required = false) String sortField,
                                       @RequestParam(value = "sortDir", required = false) String sortDir) {
        return success(service.catalogTableColumns(tableId,sortField,sortDir));
    }

    @GetMapping("/tables/columns/page")
    public Map<String,Object> columnsPage(@RequestParam String tableId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String sortField,@RequestParam(required=false) String sortDir) {
        return success(service.catalogColumnsPage(tableId,page,size,sortField,sortDir));
    }

    @PostMapping("/tables/columns/batch")
    public Map<String,Object> columnsBatch(@RequestBody Map<String,List<String>> body) {
        return success(service.catalogColumnsBatch(body.get("tableIds")));
    }

    private static Map<String, Object> success(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "success");
        result.put("data", data);
        return result;
    }
}

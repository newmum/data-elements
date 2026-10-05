package com.linewell.dataelement.feature.assetcenter.api;

import com.linewell.dataelement.feature.assetcenter.application.AssetCenterException;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterOperationService;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterPublicationService;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterQueryService;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterWorkbenchService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** One authenticated, tenant-scoped asset boundary over the governance master data. */
@RestController
@RequestMapping("/dwa/assets")
public class AssetCenterController {
    private final AssetCenterQueryService query;
    private final AssetCenterPublicationService publication;
    private final AssetCenterWorkbenchService workbench;
    private final AssetCenterOperationService operations;

    public AssetCenterController(AssetCenterQueryService query, AssetCenterPublicationService publication,
            AssetCenterWorkbenchService workbench, AssetCenterOperationService operations) {
        this.query = query;
        this.publication = publication;
        this.workbench = workbench;
        this.operations = operations;
    }

    private static Map<String, Object> success(Object value) {
        return Map.of("code", 0, "msg", "success", "data", value);
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam Map<String, String> filters) {
        return success(query.search(filters));
    }

    @GetMapping("/objects/{kind}/{id}")
    public Map<String, Object> object(@PathVariable String kind, @PathVariable String id,
            @RequestParam Map<String, String> filters) {
        return success(query.object(kind, id, filters));
    }

    @GetMapping("/map")
    public Map<String, Object> map(@RequestParam Map<String, String> filters) {
        return success(query.mapGraph(filters));
    }

    @GetMapping("/statistics")
    public Map<String, Object> statistics(@RequestParam Map<String, String> filters) {
        return success(query.statistics(filters));
    }

    @GetMapping("/catalogs/{catalogId}/versions")
    public Map<String, Object> versions(@PathVariable String catalogId, @RequestParam Map<String, String> filters) {
        return success(publication.versions(catalogId, filters));
    }

    @PostMapping("/catalogs/{catalogId}/versions")
    public Map<String, Object> createVersion(@PathVariable String catalogId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(publication.create(catalogId, input, key));
    }

    @GetMapping("/catalogs/{catalogId}/versions/{versionId}")
    public Map<String, Object> version(@PathVariable String catalogId, @PathVariable String versionId) {
        return success(publication.version(catalogId, versionId));
    }

    @PostMapping("/versions/{versionId}/precheck")
    public Map<String, Object> precheck(@PathVariable String versionId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(publication.precheck(versionId, input, key));
    }

    @PostMapping("/versions/{versionId}/publish-requests")
    public Map<String, Object> publish(@PathVariable String versionId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(publication.publish(versionId, input, key));
    }

    @PostMapping("/catalogs/{catalogId}/listing-transition")
    public Map<String, Object> listing(@PathVariable String catalogId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(publication.listing(catalogId, input, key));
    }

    @PutMapping("/catalogs/{catalogId}/attachments")
    public Map<String, Object> attachments(@PathVariable String catalogId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(publication.attachments(catalogId, input, key));
    }

    @PostMapping("/catalogs/{catalogId}/items/import/preview")
    public Map<String, Object> importPreview(@PathVariable String catalogId, @RequestBody Map<String, Object> input) {
        return success(operations.importPreview(catalogId, input));
    }

    @PostMapping(value="/items/import/parse", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> parseImport(@RequestParam("file") MultipartFile file) {
        return success(operations.parseImport(file));
    }

    @PostMapping("/catalogs/{catalogId}/items/import/commit")
    public Map<String, Object> importCommit(@PathVariable String catalogId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(operations.importCommit(catalogId, input, key));
    }

    @PostMapping("/subscriptions/drafts")
    public Map<String, Object> subscriptionDraft(@RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.draft(input, key, false));
    }

    @PostMapping("/subscriptions/{subscriptionId}/submit")
    public Map<String, Object> submitSubscription(@PathVariable String subscriptionId,
            @RequestBody Map<String, Object> input, @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.submit(subscriptionId, input, key, false));
    }

    @PostMapping("/subscriptions/{subscriptionId}/withdraw")
    public Map<String, Object> withdrawSubscription(@PathVariable String subscriptionId,
            @RequestBody Map<String, Object> input, @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.withdraw(subscriptionId, input, key));
    }

    @GetMapping("/subscriptions/{subscriptionId}/progress")
    public Map<String, Object> progress(@PathVariable String subscriptionId) {
        return success(workbench.progress(subscriptionId));
    }

    @GetMapping("/subscriptions/{subscriptionId}/delivery")
    public Map<String, Object> delivery(@PathVariable String subscriptionId) {
        return success(workbench.delivery(subscriptionId));
    }

    @PostMapping("/deliveries/{deliveryId}/access")
    public Map<String, Object> deliveryAccess(@PathVariable String deliveryId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(operations.deliveryAccess(deliveryId, input, key));
    }

    @GetMapping("/workbench")
    public Map<String, Object> workbench(@RequestParam Map<String, String> filters) {
        return success(workbench.workbench(filters));
    }

    @PostMapping("/tasks/{taskId}/handle")
    public Map<String, Object> task(@PathVariable String taskId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.handleTask(taskId, input, key));
    }

    @PostMapping("/demands/drafts")
    public Map<String, Object> demandDraft(@RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.draft(input, key, true));
    }

    @PostMapping("/demands/{demandId}/submit")
    public Map<String, Object> submitDemand(@PathVariable String demandId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.submit(demandId, input, key, true));
    }

    @PostMapping("/demands/{demandId}/responses")
    public Map<String, Object> demandResponse(@PathVariable String demandId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(operations.demandResponse(demandId, input, key));
    }

    @PostMapping("/demands/{demandId}/match")
    public Map<String, Object> demandMatch(@PathVariable String demandId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(operations.demandMatch(demandId, input, key));
    }

    @PatchMapping("/demands/{demandId}")
    public Map<String, Object> withdrawDemand(@PathVariable String demandId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(operations.withdrawDemand(demandId, input, key));
    }

    @GetMapping("/demands/{demandId}")
    public Map<String, Object> demand(@PathVariable String demandId) {
        return success(workbench.demand(demandId));
    }

    @PutMapping("/favorites/{kind}/{id}")
    public Map<String, Object> favorite(@PathVariable String kind, @PathVariable String id,
            @RequestBody Map<String, Object> input, @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.favorite(kind, id, input, key));
    }

    @PostMapping("/visits")
    public Map<String, Object> visit(@RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(workbench.visits(input, key));
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody Map<String, Object> input) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(success(operations.preview(input)));
    }

    @PostMapping("/apis/{apiId}/debug")
    public Map<String, Object> apiDebug(@PathVariable String apiId, @RequestBody Map<String, Object> input,
            @RequestHeader("Idempotency-Key") String key) {
        return success(operations.apiDebug(apiId, input, key));
    }

    @ExceptionHandler(AssetCenterException.class)
    public ResponseEntity<Map<String, Object>> domainError(AssetCenterException error) {
        return ResponseEntity.status(error.status()).body(Map.of(
                "code", error.code(), "message", error.getMessage()));
    }
}

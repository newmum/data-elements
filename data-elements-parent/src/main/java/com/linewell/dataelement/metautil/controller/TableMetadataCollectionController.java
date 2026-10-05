package com.linewell.dataelement.metautil.controller;

import com.linewell.dataelement.metautil.service.TableMetadataCollectionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the asynchronous step-2 metadata collection workflow. */
@RestController
@RequestMapping("/dst/database/metadata/tables")
public class TableMetadataCollectionController {

    private final TableMetadataCollectionService collection;

    public TableMetadataCollectionController(TableMetadataCollectionService collection) {
        this.collection = collection;
    }

    @PostMapping("/collection/start")
    public Map<String, Object> start(@RequestBody Map<String, Object> body) {
        return success(collection.start(value(body, "dbId"), body == null ? null : body.get("mode")));
    }

    @GetMapping("/collection/status")
    public Map<String, Object> status(@RequestParam("dbId") String dbId,
                                      @RequestParam(value = "jobId", required = false) String jobId,
                                      @RequestParam(value = "includeDeletedDetails", defaultValue = "false") boolean includeDeletedDetails) {
        return success(collection.status(jobId, dbId, includeDeletedDetails));
    }

    /** Return one latest collection status per datasource without a browser request per row. */
    @PostMapping("/collection/status/batch")
    public Map<String, Object> batchStatus(@RequestBody Map<String, Object> body) {
        Object ids = body == null ? null : body.get("dbIds");
        if (!(ids instanceof List<?> dbIds)) throw new IllegalArgumentException("dbIds 必须是数组");
        return success(collection.batchStatus(dbIds));
    }

    /** Pages changed-table details so the dialog never transfers every change at once. */
    @GetMapping("/collection/changes")
    public Map<String, Object> changes(@RequestParam("dbId") String dbId,
                                       @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                       @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                       @RequestParam(value = "keyword", required = false) String keyword) {
        return success(collection.changedTables(dbId, pageNo, pageSize, keyword));
    }

    @GetMapping("/saved")
    public Map<String, Object> saved(@RequestParam("dbId") String dbId,
                                     @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                     @RequestParam(value = "pageSize", defaultValue = "15") int pageSize,
                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return success(collection.savedTables(dbId, pageNo, pageSize, keyword));
    }

    @GetMapping("/collection/additions")
    public Map<String, Object> additions(@RequestParam("dbId") String dbId,
                                         @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                         @RequestParam(value = "pageSize", defaultValue = "15") int pageSize) {
        return success(collection.addedTables(dbId, pageNo, pageSize));
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot(@RequestParam("dbId") String dbId,
                                        @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                        @RequestParam(value = "pageSize", defaultValue = "100") int pageSize,
                                        @RequestParam(value = "offset", required = false) Integer offset,
                                        @RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "businessType", required = false) String businessType) {
        return success(collection.snapshot(dbId, pageNo, pageSize, offset, keyword, businessType));
    }

    @PostMapping("/collection/rename-missing")
    public Map<String, Object> renameMissing(@RequestBody Map<String, Object> body) {
        return success(collection.renameMissingTable(
                value(body, "dbId"), value(body, "tableId"), value(body, "tableName")));
    }

    /**
     * This UI's Axios interceptor only treats code 0/1/200 as a successful
     * business response.  Spring controller responses are not wrapped by the
     * Magic-API envelope automatically, so keep this explicit and consistent.
     */
    private static Map<String, Object> success(Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("msg", "success");
        envelope.put("data", data);
        return envelope;
    }

    private static String value(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return "";
        return String.valueOf(body.get(key));
    }
}

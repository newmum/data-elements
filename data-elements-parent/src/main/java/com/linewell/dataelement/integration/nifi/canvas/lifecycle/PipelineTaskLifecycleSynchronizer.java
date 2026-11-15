package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.script.exception.MagicScriptException;

/** Keeps data-access task state aligned with the NiFi pipeline lifecycle. */
@Service
public class PipelineTaskLifecycleSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskLifecycleSynchronizer.class);
    private final DataAssetRuntimeAdapter dataAssetRuntimeAdapter;
    private final MagicAPIService magic;

    public PipelineTaskLifecycleSynchronizer(
            DataAssetRuntimeAdapter dataAssetRuntimeAdapter,
            MagicAPIService magic
    ) {
        this.dataAssetRuntimeAdapter = dataAssetRuntimeAdapter;
        this.magic = magic;
    }

    /** Invokes task persistence at the caller's existing transaction boundary. */
    public void ensureCatalogAccessTask(String rawCatalogTid, Pipeline pipeline) {
        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("rawCatalogTid", rawCatalogTid);
            parameters.put("pipelineId", pipeline.id());
            parameters.put("pipelineName", pipeline.name());
            parameters.put("dsl", pipeline.dsl());
            Map<String, Object> task = invoke("/common/ensureNifiCatalogAccessTask", parameters);
            if (Boolean.TRUE.equals(task.get("mirrorEligible"))) {
                synchronizeLegacyCatalogLink((String) task.get("catalogId"), (String) task.get("taskId"), pipeline.id());
            }
        } catch (Exception exception) {
            log.warn("Synchronize access task failed for pipeline {}: {}", pipeline.id(), exception.getMessage());
            throw new IllegalStateException("无法关联接入任务：" + exception.getMessage(), exception);
        }
    }

    /** Mirrors the confirmed association through the existing catalog and search projection adapter. */
    private void synchronizeLegacyCatalogLink(String catalogTid, String taskId, String pipelineId) {
        try {
            Map<String, Object> catalog = dataAssetRuntimeAdapter.queryById(catalogTid);
            String accessTaskId = catalog == null ? null : stringValue(catalog.get("accessTaskId"));
            if (taskId.equals(accessTaskId)) {
                return;
            }
            dataAssetRuntimeAdapter.propBatchSaveOrUpdate(List.of(catalogTid), Map.of("accessTaskId", taskId));
        } catch (Exception exception) {
            log.info("Skip legacy asset-catalog task mirror for pipeline {} catalog {}: {}",
                    pipelineId, catalogTid, exception.getMessage());
        }
    }

    /** Runs the Magic write at the caller's existing lifecycle and transaction boundary. */
    public void syncState(String pipelineId, String operation, Object value) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pipelineId", pipelineId);
        parameters.put("operation", operation);
        parameters.put("value", value);
        invoke("/common/nifiTaskState", parameters);
    }

    private <T> T invoke(String path, Map<String, Object> parameters) {
        try {
            return magic.invoke(path, parameters);
        } catch (MagicScriptException failure) {
            if (failure.getCause() instanceof RuntimeException original) throw original;
            throw failure;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}

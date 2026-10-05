package com.linewell.dataelement.feature.approval.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ApprovalEngineModels {

    private ApprovalEngineModels() {
    }

    public record StartCommand(
            String businessId,
            String flowCode,
            String handler,
            String extension,
            Map<String, Object> variables
    ) {
    }

    public record CompleteCommand(
            long taskId,
            String action,
            String nodeCode,
            String message,
            String flowStatus,
            String historyStatus,
            Map<String, Object> variables
    ) {
    }

    public record EngineInstance(Long id) {
    }

    public record TaskOperationCommand(
            long taskId,
            String operation,
            String handler,
            String message,
            List<String> targetHandlers,
            List<String> reductionHandlers
    ) {
    }

    public record PreviousNode(
            Long id,
            String nodeCode,
            String nodeName,
            Integer nodeType
    ) {
    }

    public record CleanupResult(
            int users,
            int historyTasks,
            int tasks,
            int instances
    ) {
        public Map<String, Integer> asMap() {
            return Map.of(
                    "flow_user", users,
                    "flow_his_task", historyTasks,
                    "flow_task", tasks,
                    "flow_instance", instances
            );
        }
    }

    public static List<PreviousNode> emptyNodes() {
        return Collections.emptyList();
    }
}

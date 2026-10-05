package com.linewell.dataelement.feature.approval.application;

import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.CleanupResult;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.CompleteCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.EngineInstance;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.PreviousNode;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.StartCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.TaskOperationCommand;
import java.util.List;
import java.util.Optional;

/**
 * Stable platform boundary around the workflow engine.
 */
public interface ApprovalEnginePort {

    EngineInstance startAndEnterFirstTask(StartCommand command);

    EngineInstance complete(CompleteCommand command);

    EngineInstance operateTask(TaskOperationCommand command);

    void terminate(long instanceId, String handler, String message);

    List<PreviousNode> previousNodes(long definitionId, String nodeCode);

    Optional<Long> findInstanceId(String businessId);

    Optional<Long> findFirstTaskId(long instanceId);

    Optional<String> publishedDefinitionName(String flowCode);

    CleanupResult deleteInstances(String businessId);
}

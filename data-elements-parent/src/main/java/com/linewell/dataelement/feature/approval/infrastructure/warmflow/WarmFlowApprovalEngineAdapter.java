package com.linewell.dataelement.feature.approval.infrastructure.warmflow;

import com.linewell.dataelement.feature.approval.application.ApprovalEnginePort;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.CleanupResult;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.CompleteCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.EngineInstance;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.PreviousNode;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.StartCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.TaskOperationCommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.HisTaskService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.NodeService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.service.UserService;
import org.springframework.stereotype.Component;

@Component
public class WarmFlowApprovalEngineAdapter implements ApprovalEnginePort {

    private final InsService insService;
    private final TaskService taskService;
    private final DefService defService;
    private final NodeService nodeService;
    private final HisTaskService hisTaskService;
    private final UserService userService;

    public WarmFlowApprovalEngineAdapter(
            InsService insService,
            TaskService taskService,
            DefService defService,
            NodeService nodeService,
            HisTaskService hisTaskService,
            UserService userService
    ) {
        this.insService = insService;
        this.taskService = taskService;
        this.defService = defService;
        this.nodeService = nodeService;
        this.hisTaskService = hisTaskService;
        this.userService = userService;
    }

    @Override
    public EngineInstance startAndEnterFirstTask(StartCommand command) {
        FlowParams params = FlowParams.build()
                .flowCode(command.flowCode())
                .handler(command.handler())
                .ext(command.extension())
                .variable(command.variables());
        Instance instance = insService.start(command.businessId(), params);
        instance = taskService.skipByInsId(
                instance.getId(),
                FlowParams.build().skipType(SkipType.PASS.getKey())
        );
        return new EngineInstance(instance == null ? null : instance.getId());
    }

    @Override
    public EngineInstance complete(CompleteCommand command) {
        String skipType = switch (command.action()) {
            case "REJECT", "UNPASS", "REVOKE" -> SkipType.REJECT.getKey();
            default -> SkipType.PASS.getKey();
        };
        FlowParams params = FlowParams.build()
                .skipType(skipType)
                .message(command.message())
                .variable(command.variables());
        if (command.nodeCode() != null && !command.nodeCode().isBlank()) {
            params.nodeCode(command.nodeCode());
        }
        if (command.flowStatus() != null) {
            params.flowStatus(command.flowStatus());
        }
        if (command.historyStatus() != null) {
            params.hisStatus(command.historyStatus());
        }
        Instance instance = taskService.skip(command.taskId(), params);
        return new EngineInstance(instance == null ? null : instance.getId());
    }

    @Override
    public EngineInstance operateTask(TaskOperationCommand command) {
        FlowParams params = FlowParams.build()
                .handler(command.handler())
                .message(command.message())
                .addHandlers(command.targetHandlers())
                .reductionHandlers(command.reductionHandlers());
        boolean success;
        switch (command.operation()) {
            case "TRANSFER" -> success = taskService.transfer(command.taskId(), params);
            case "DEPUTE" -> success = taskService.depute(command.taskId(), params);
            case "ADD_SIGNATURE" -> success = taskService.addSignature(command.taskId(), params);
            case "REDUCTION_SIGNATURE" -> success = taskService.reductionSignature(command.taskId(), params);
            case "TAKE_BACK" -> {
                Instance instance = taskService.taskBack(command.taskId(), params);
                return new EngineInstance(instance == null ? null : instance.getId());
            }
            case "PENDING" -> {
                Instance instance = taskService.pending(command.taskId(), params);
                return new EngineInstance(instance == null ? null : instance.getId());
            }
            default -> throw new IllegalArgumentException("Unsupported Warm-Flow task operation: " + command.operation());
        }
        if (!success) {
            throw new IllegalStateException("Warm-Flow task operation failed: " + command.operation());
        }
        return new EngineInstance(null);
    }

    @Override
    public void terminate(long instanceId, String handler, String message) {
        taskService.terminationByInsId(
                instanceId,
                FlowParams.build().handler(handler).message(message)
        );
    }

    @Override
    public List<PreviousNode> previousNodes(long definitionId, String nodeCode) {
        List<Node> nodes = nodeService.list(
                FlowEngine.newNode().setDefinitionId(definitionId).setNodeCode(nodeCode)
        );
        if (nodes == null || nodes.isEmpty() || nodes.get(0).getId() == null) {
            return Collections.emptyList();
        }
        List<Node> previous = nodeService.previousNodeList(nodes.get(0).getId());
        if (previous == null) {
            return Collections.emptyList();
        }
        return previous.stream()
                .map(node -> new PreviousNode(node.getId(), node.getNodeCode(), node.getNodeName(), node.getNodeType()))
                .toList();
    }

    @Override
    public Optional<Long> findInstanceId(String businessId) {
        List<Instance> instances = insService.list(FlowEngine.newIns().setBusinessId(businessId));
        if (instances == null || instances.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(instances.get(0).getId());
    }

    @Override
    public Optional<Long> findFirstTaskId(long instanceId) {
        List<Task> tasks = taskService.list(FlowEngine.newTask().setInstanceId(instanceId));
        if (tasks == null || tasks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(0).getId());
    }

    @Override
    public Optional<String> publishedDefinitionName(String flowCode) {
        Definition definition = defService.getPublishByFlowCode(flowCode);
        return Optional.ofNullable(definition).map(Definition::getFlowName);
    }

    @Override
    public CleanupResult deleteInstances(String businessId) {
        if (businessId == null || businessId.isBlank()) {
            return new CleanupResult(0, 0, 0, 0);
        }
        List<Instance> instances = insService.list(FlowEngine.newIns().setBusinessId(businessId));
        if (instances == null || instances.isEmpty()) {
            return new CleanupResult(0, 0, 0, 0);
        }

        List<Long> instanceIds = instances.stream()
                .map(Instance::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Long> taskIds = new ArrayList<>();
        int historyTaskCount = 0;
        for (Long instanceId : instanceIds) {
            List<Task> tasks = taskService.list(FlowEngine.newTask().setInstanceId(instanceId));
            if (tasks != null) {
                tasks.stream().map(Task::getId).filter(java.util.Objects::nonNull).forEach(taskIds::add);
            }
            List<HisTask> historyTasks = hisTaskService.list(FlowEngine.newHisTask().setInstanceId(instanceId));
            historyTaskCount += historyTasks == null ? 0 : historyTasks.size();
        }

        List<Long> associatedIds = new ArrayList<>(taskIds);
        associatedIds.addAll(instanceIds);
        List<User> users = associatedIds.isEmpty()
                ? Collections.emptyList()
                : userService.getByAssociateds(associatedIds);
        List<Long> userIds = users == null ? Collections.emptyList() : users.stream()
                .map(User::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!userIds.isEmpty()) {
            userService.removeByIds(userIds);
        }
        hisTaskService.deleteByInsIds(instanceIds);
        taskService.deleteByInsIds(instanceIds);
        insService.remove(instanceIds);
        return new CleanupResult(userIds.size(), historyTaskCount, taskIds.size(), instanceIds.size());
    }
}

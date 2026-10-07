package com.linewell.dataelement.feature.approval.application;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import com.linewell.dataelement.model.approval.ApprovalHandleRequest;
import com.linewell.dataelement.model.approval.ApprovalPreviousNodeRequest;
import com.linewell.dataelement.model.approval.ApprovalRevokeRequest;
import com.linewell.dataelement.model.approval.ApprovalStartRequest;
import com.linewell.dataelement.model.approval.ApprovalTerminationRequest;
import com.linewell.dataelement.dataassets.base.entity.DataApplyFormT;
import com.linewell.dataelement.dataassets.base.service.IDataApplyFormTService;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.feature.identity.application.IdentityUserLookupService;
import com.linewell.dataelement.feature.delivery.application.ResourceDeliveryService;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterApprovalBridge;
import com.linewell.dataelement.feature.identity.domain.IdentityUser;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.entity.FlowSuggestion;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.service.IFlowSuggestionService;
import com.linewell.dataelement.model.common.BizException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.CompleteCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.EngineInstance;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.StartCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.TaskOperationCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ApprovalFlowService {

    private final ApprovalEnginePort approvalEngine;
    private final ApprovalBusinessAssociationService businessAssociations;
    private final IDataApplyFormTService dataApplyFormTService;
    private final IFlowSuggestionService flowSuggestionService;
    private final EsCommonService esCommonService;
    private final IdentityUserLookupService identityUserLookupService;
    private final ResourceDeliveryService resourceDeliveryService;
    private final AssetCenterApprovalBridge assetCenterApprovalBridge;

    public ApprovalFlowService(
        ApprovalEnginePort approvalEngine,
        IDataApplyFormTService dataApplyFormTService,
        IFlowSuggestionService flowSuggestionService,
        EsCommonService esCommonService,
        IdentityUserLookupService identityUserLookupService,
        ResourceDeliveryService resourceDeliveryService,
        AssetCenterApprovalBridge assetCenterApprovalBridge,
        ApprovalBusinessAssociationService businessAssociations
    ) {
        this.approvalEngine = approvalEngine;
        this.businessAssociations = businessAssociations;
        this.dataApplyFormTService = dataApplyFormTService;
        this.flowSuggestionService = flowSuggestionService;
        this.esCommonService = esCommonService;
        this.identityUserLookupService = identityUserLookupService;
        this.resourceDeliveryService = resourceDeliveryService;
        this.assetCenterApprovalBridge = assetCenterApprovalBridge;
    }

    public Map<String, Object> start(ApprovalStartRequest request) {
        validate(request);
        String currentUserId = String.valueOf(StpUtil.getLoginId());
        var business = businessAssociations.load(request.getFlowOrderId());
        businessAssociations.requireCompatible(business, request.getApproveType());
        businessAssociations.requireInitiator(business, currentUserId);

        Map<String, Object> variable = new LinkedHashMap<>();
        if (request.getFlowParams() != null) {
            variable.putAll(request.getFlowParams());
        }

        List<String> dataProviderOrgIds = business.orgId().isBlank() ? List.of() : List.of(business.orgId());
        List<String> dataProviderOrgHandlers = resolveOrgHandlers(dataProviderOrgIds);
        if (dataProviderOrgHandlers.isEmpty()) {
            dataProviderOrgHandlers.add(currentUserId);
        }

        String appId = request.getAppId();
        if (appId == null || appId.isBlank()) {
            Object appIdObj = com.linewell.dataelement.platform.tenant.application.TenantSessions.session().get("appId");
            appId = appIdObj == null ? null : String.valueOf(appIdObj);
        }

        List<String> adminRoleHandlers = resolveRoleHandlers(appId, "adminRole");
        List<String> dataProviderRoleHandlers = resolveRoleHandlers(appId, "dataProviderRole");
        List<String> dataManagerRoleHandlers = resolveRoleHandlers(appId, "dataManagerRole");

        variable.put("handle", currentUserId);
        variable.put("adminRole", adminRoleHandlers);
        variable.put("dataProviderRole", dataProviderRoleHandlers);
        variable.put("dataProviderOrg", dataProviderOrgHandlers);
        variable.put("dataManagerRole", dataManagerRoleHandlers);
        variable.put("createBy", currentUserId);
        variable.put("businessId", request.getFlowOrderId());
        variable.put("approveType", request.getApproveType());

        EngineInstance instance = approvalEngine.startAndEnterFirstTask(new StartCommand(
            request.getFlowOrderId(),
            request.getFlowType(),
            currentUserId,
            JSONUtil.toJsonStr(resolveFlowExtInfo(business, request.getFlowType())),
            variable
        ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instance.id());
        result.put("flowCode", request.getFlowType());
        result.put("flowOrderId", request.getFlowOrderId());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handle(ApprovalHandleRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId().isBlank()) {
            throw new BizException(-1, "taskId不能为空");
        }
        String currentUserId = String.valueOf(StpUtil.getLoginId());

        String handleType = request.getHandleType();
        if (!isSupportedOperation(handleType)) {
            throw new BizException(-1, "不支持的审批操作: " + handleType);
        }
        String nodeCode = request.getNodeCode();
        String flowStatus = null;
        String historyStatus = null;
        if ("UNPASS".equals(handleType)) {
            nodeCode = "end";
            flowStatus = "4";
            historyStatus = "4";
        } else if ("REJECT".equals(handleType)) {
            flowStatus = "9";
            historyStatus = "9";
        } else if ("REVOKE".equals(handleType)) {
            flowStatus = "6";
            historyStatus = "6";
        }

        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("taskId", request.getTaskId());
        variable.put("businessId", request.getBusinessId());
        variable.put("handleType", request.getHandleType());
        variable.put("message", request.getMessage());
        variable.put("nodeCode", request.getNodeCode());
        variable.put("approveType", request.getApproveType());
        variable.put("suggestionId", request.getSuggestionId());
        variable.put("handle", currentUserId);
        EngineInstance instance;
        if (isCollaborativeOperation(handleType)) {
            instance = approvalEngine.operateTask(new TaskOperationCommand(
                NumberUtil.parseLong(request.getTaskId()),
                handleType,
                currentUserId,
                request.getMessage(),
                request.getTargetHandlers(),
                request.getReductionHandlers()
            ));
        } else {
            instance = approvalEngine.complete(new CompleteCommand(
                NumberUtil.parseLong(request.getTaskId()),
                handleType,
                nodeCode,
                request.getMessage(),
                flowStatus,
                historyStatus,
                variable
            ));
        }
        persistSuggestionUsage(request, currentUserId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instance == null ? null : instance.id());
        result.putAll(variable);
        return result;
    }

    private void persistSuggestionUsage(ApprovalHandleRequest request, String userId) {
        String message = request.getMessage();
        if (Objects.equals(request.getStorageSuggestion(), 1) && message != null && !message.isBlank()) {
            FlowSuggestion existed = findSuggestionByCreatorAndContent(userId, message);
            if (existed == null) {
                LocalDateTime now = LocalDateTime.now();
                FlowSuggestion add = new FlowSuggestion();
                add.setTid(NumericId.nextId());
                add.setContent(message);
                add.setCreateBy(userId);
                add.setCreateTime(now);
                add.setUseNum(0L);
                add.setIsDel(0);
                saveSuggestion(add);
            }
        }

        String suggestionId = request.getSuggestionId();
        if (suggestionId == null || suggestionId.isBlank()) {
            return;
        }
        FlowSuggestion suggestion = findSuggestionById(suggestionId);
        if (suggestion == null) {
            return;
        }
        Long useNum = suggestion.getUseNum() == null ? 0L : suggestion.getUseNum();
        incrementSuggestionUseNum(suggestionId, useNum + 1, userId);
    }

    private FlowSuggestion findSuggestionByCreatorAndContent(String userId, String content) {
        LambdaQueryWrapper<FlowSuggestion> wrapper = new LambdaQueryWrapper<FlowSuggestion>()
            .eq(FlowSuggestion::getCreateBy, userId)
            .eq(FlowSuggestion::getContent, content)
            .eq(FlowSuggestion::getIsDel, 0);
            return flowSuggestionService.getOne(wrapper, false);
    }

    private FlowSuggestion findSuggestionById(String suggestionId) {
        LambdaQueryWrapper<FlowSuggestion> wrapper = new LambdaQueryWrapper<FlowSuggestion>()
            .eq(FlowSuggestion::getTid, suggestionId)
            .eq(FlowSuggestion::getIsDel, 0);
            return flowSuggestionService.getOne(wrapper, true);
    }

    private void saveSuggestion(FlowSuggestion suggestion) {
            flowSuggestionService.save(suggestion);
    }

    private void incrementSuggestionUseNum(String suggestionId, long nextUseNum, String userId) {
        LambdaUpdateWrapper<FlowSuggestion> wrapper = new LambdaUpdateWrapper<FlowSuggestion>()
            .eq(FlowSuggestion::getTid, suggestionId)
            .set(FlowSuggestion::getUseNum, nextUseNum)
            .set(FlowSuggestion::getUpdateTime, LocalDateTime.now())
            .set(FlowSuggestion::getUpdateBy, userId);
            flowSuggestionService.update(wrapper);
    }

    public Map<String, Object> revoke(ApprovalRevokeRequest request) {
        if (request == null || request.getTid() == null || request.getTid().isBlank()) {
            throw new BizException(-1, "tid不能为空");
        }
        if (request.getFlowOrderId() == null || request.getFlowOrderId().isBlank()) {
            throw new BizException(-1, "flowOrderId不能为空");
        }

        var business = businessAssociations.load(request.getFlowOrderId());
        if (!request.getTid().equals(business.id())) throw new BizException(403, "业务单号与撤销对象不一致");
        businessAssociations.requireInitiator(business, String.valueOf(StpUtil.getLoginId()));
        Long instanceId = approvalEngine.findInstanceId(business.id()).orElse(null);
        if (instanceId == null) {
            throw new BizException(500, "流程实例不存在");
        }
        Long taskIdNum = approvalEngine.findFirstTaskId(instanceId).orElse(null);
        if (taskIdNum == null) {
            throw new BizException(500, "流程任务不存在");
        }
        String taskId = String.valueOf(taskIdNum);

        ApprovalHandleRequest handleRequest = new ApprovalHandleRequest();
        handleRequest.setTaskId(taskId);
        handleRequest.setBusinessId(business.id());
        handleRequest.setHandleType("REVOKE");
        handleRequest.setMessage(request.getMessage());
        handleRequest.setNodeCode("end");
        handleRequest.setApproveType(business.approveType());
        return handle(handleRequest);
    }

    public Object termination(ApprovalTerminationRequest request) {
        if (request == null || request.getInstanceId() == null || request.getInstanceId().isBlank()) {
            throw new BizException(-1, "instanceId不能为空");
        }
        approvalEngine.terminate(
            NumberUtil.parseLong(request.getInstanceId()),
            String.valueOf(StpUtil.getLoginId()),
            request.getMessage() == null || request.getMessage().isBlank() ? "终止流程" : request.getMessage()
        );
        return "success";
    }

    public Object previousNodeList(ApprovalPreviousNodeRequest request) {
        if (request == null || request.getDefinitionId() == null || request.getNodeCode() == null) {
            throw new BizException(-1, "definitionId和nodeCode不能为空");
        }
        var nodes = approvalEngine.previousNodes(
            NumberUtil.parseLong(request.getDefinitionId()),
            request.getNodeCode()
        );
        if (nodes.isEmpty()) {
            throw new BizException(500, "节点不存在");
        }
        return nodes;
    }

    public Object finshHandle(ApprovalCallbackRequest request) {
        String approveType = request == null ? null : request.getApproveType();
        String handleType = request == null ? null : request.getHandleType();
        String businessId = request == null ? null : request.getBusinessId();
        String nextNodeCode = request == null ? null : request.getNextNodeCode();
        if (businessId == null || businessId.isBlank()) {
            throw new BizException(500, "businessId不能为空");
        }

        int flowStatus = 1;
        if ("UNPASS".equals(handleType)) {
            flowStatus = 3;
        }
        if ("end".equals(nextNodeCode)) {
            flowStatus = "PASS".equals(handleType) ? 2 : 3;
        }
        if ("REVOKE".equals(handleType)) {
            flowStatus = 4;
        }
        if ("REJECT".equals(handleType)) {
            flowStatus = 5;
        }

        var business = businessAssociations.load(businessId);
        businessAssociations.requireCompatible(business, approveType);
        LocalDateTime now = LocalDateTime.now();
        if ("haoyuePublication".equals(approveType) || "haoyueSubscription".equals(approveType)) {
            assetCenterApprovalBridge.complete(approveType, businessId, flowStatus);
        } else {
            dataApplyFormTService.update(
                new LambdaUpdateWrapper<DataApplyFormT>()
                    .eq(DataApplyFormT::getTid, businessId)
                    .eq(DataApplyFormT::getTenantId, business.tenantId())
                    .and(active -> active.eq(DataApplyFormT::getIsDel, 0).or().isNull(DataApplyFormT::getIsDel))
                    .set(DataApplyFormT::getFlowStatus, String.valueOf(flowStatus))
                    .set(DataApplyFormT::getUpdatedTime, now)
            );
            esCommonService.saveOrUpdate(
                "dataassets_apply_form",
                businessId,
                "{\"flowStatus\":" + flowStatus + "}",
                true
            );

            // 审批完全通过后，按资源类型生成库表分发任务或 API 授权记录。
            if (flowStatus == 2) {
                resourceDeliveryService.completeApprovedApplication(businessId);
            }
        }
        return true;
    }

    private boolean isCollaborativeOperation(String handleType) {
        return List.of("TRANSFER", "DEPUTE", "ADD_SIGNATURE", "REDUCTION_SIGNATURE", "TAKE_BACK", "PENDING")
            .contains(handleType);
    }

    private boolean isSupportedOperation(String handleType) {
        return List.of(
            "PASS", "UNPASS", "REJECT", "REVOKE",
            "TRANSFER", "DEPUTE", "ADD_SIGNATURE", "REDUCTION_SIGNATURE", "TAKE_BACK", "PENDING"
        ).contains(handleType);
    }

    private void validate(ApprovalStartRequest request) {
        if (request == null) {
            throw new BizException(-1, "参数不能为空");
        }
        if (request.getFlowType() == null || request.getFlowType().isBlank()) {
            throw new BizException(-1, "flowType不能为空");
        }
        if (request.getFlowOrderId() == null || request.getFlowOrderId().isBlank()) {
            throw new BizException(-1, "flowOrderId不能为空");
        }

        if (approvalEngine.publishedDefinitionName(request.getFlowType()).isEmpty()) {
            throw new BizException(-1, "流程定义不存在或未发布: " + request.getFlowType());
        }
    }

    private List<String> resolveOrgHandlers(List<String> orgIds) {
        List<String> handlerIds = new ArrayList<>();
        for (IdentityUser user : identityUserLookupService.listUsersByOrgIds(orgIds)) {
            addUnique(handlerIds, user.getUserId());
        }
        return handlerIds;
    }

    private List<String> resolveRoleHandlers(String appId, String roleCode) {
        List<String> handlerIds = new ArrayList<>();
        for (IdentityUser user : identityUserLookupService.listUsersByRoleCode(appId, roleCode)) {
            addUnique(handlerIds, user.getUserId());
        }
        return handlerIds;
    }

    private Map<String, Object> resolveFlowExtInfo(ApprovalBusinessAssociationService.Business business, String flowCode) {
        Map<String, Object> extInfo = new LinkedHashMap<>();
        extInfo.put("businessId", business.id());
        extInfo.put("businessType", business.type());
        extInfo.put("businessName", business.name());
        extInfo.put("flowCode", flowCode);
        String flowName = approvalEngine.publishedDefinitionName(flowCode).orElse("");
        extInfo.put("flowName", flowName);
        extInfo.put("relaId", business.id());
        extInfo.put("keyword", (flowName + " " + business.name()).trim());
        return extInfo;
    }

    private void addUnique(List<String> list, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}

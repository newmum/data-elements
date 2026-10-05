package com.linewell.dataelement.feature.approval.application;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.dataasset.CatalogItemSaveOrUpdateRequest;
import com.linewell.dataelement.model.dataasset.CatalogItemValueRequest;
import com.linewell.dataelement.model.dataasset.DataAssetPropSaveRequest;
import com.linewell.dataelement.feature.approval.config.DataAssetApprovalMode;
import com.linewell.dataelement.feature.approval.config.DataAssetApprovalProperties;
import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import com.linewell.dataelement.model.approval.ApprovalHandleRequest;
import com.linewell.dataelement.model.approval.ApprovalPreviousNodeRequest;
import com.linewell.dataelement.model.approval.ApprovalRestoreAssetStatusRequest;
import com.linewell.dataelement.model.approval.ApprovalRevokeRequest;
import com.linewell.dataelement.model.approval.ApprovalStartRequest;
import com.linewell.dataelement.model.approval.ApprovalTerminationRequest;
import com.linewell.dataelement.dataassets.base.entity.DaAssetT;
import com.linewell.dataelement.dataassets.base.entity.DaOrderAssetRela;
import com.linewell.dataelement.dataassets.base.entity.DataApplyFormT;
import com.linewell.dataelement.dataassets.base.entity.DataPropT;
import com.linewell.dataelement.dataassets.base.service.IDaAssetTService;
import com.linewell.dataelement.dataassets.base.service.IDataApplyFormTService;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.dataassets.base.service.IDaOrderAssetRelaService;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.feature.identity.application.IdentityUserLookupService;
import com.linewell.dataelement.feature.delivery.application.ResourceDeliveryService;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterApprovalBridge;
import com.linewell.dataelement.feature.identity.domain.IdentityUser;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.entity.FlowSuggestion;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.service.IFlowSuggestionService;
import com.linewell.dataelement.model.common.BizException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.CompleteCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.EngineInstance;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.StartCommand;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.TaskOperationCommand;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ApprovalFlowService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApprovalEnginePort approvalEngine;
    private final IDaOrderAssetRelaService daOrderAssetRelaService;
    private final IDaAssetTService daAssetTService;
    private final IDataApplyFormTService dataApplyFormTService;
    private final IDataPropTService dataPropTService;
    private final IFlowSuggestionService flowSuggestionService;
    private final EsCommonService esCommonService;
    private final IdentityUserLookupService identityUserLookupService;
    private final ObjectMapper objectMapper;
    private final DataAssetRuntimeAdapter dataAssetRuntimeAdapter;
    private final ResourceDeliveryService resourceDeliveryService;
    private final AssetCenterApprovalBridge assetCenterApprovalBridge;
    private final DataAssetApprovalProperties approvalProperties;

    public ApprovalFlowService(
        ApprovalEnginePort approvalEngine,
        IDaOrderAssetRelaService daOrderAssetRelaService,
        IDaAssetTService daAssetTService,
        IDataApplyFormTService dataApplyFormTService,
        IDataPropTService dataPropTService,
        IFlowSuggestionService flowSuggestionService,
        EsCommonService esCommonService,
        IdentityUserLookupService identityUserLookupService,
        ObjectMapper objectMapper,
        @Lazy DataAssetRuntimeAdapter dataAssetRuntimeAdapter,
        ResourceDeliveryService resourceDeliveryService,
        AssetCenterApprovalBridge assetCenterApprovalBridge,
        DataAssetApprovalProperties approvalProperties
    ) {
        this.approvalEngine = approvalEngine;
        this.daOrderAssetRelaService = daOrderAssetRelaService;
        this.daAssetTService = daAssetTService;
        this.dataApplyFormTService = dataApplyFormTService;
        this.dataPropTService = dataPropTService;
        this.flowSuggestionService = flowSuggestionService;
        this.esCommonService = esCommonService;
        this.identityUserLookupService = identityUserLookupService;
        this.objectMapper = objectMapper;
        this.dataAssetRuntimeAdapter = dataAssetRuntimeAdapter;
        this.resourceDeliveryService = resourceDeliveryService;
        this.assetCenterApprovalBridge = assetCenterApprovalBridge;
        this.approvalProperties = approvalProperties;
    }

    public Map<String, Object> start(ApprovalStartRequest request) {
        String approveType = request == null ? null : request.getApproveType();
        if (isAssetApprovalType(approveType)) {
            DataAssetApprovalMode mode = approvalProperties.currentMode();
            if (mode == DataAssetApprovalMode.AUTO) {
                completeAssetApprovalPassByFlowOrderId(request.getFlowOrderId(), approveType);
                return buildApprovalBypassResult(request, mode);
            }
            if (mode == DataAssetApprovalMode.OFF) {
                return buildApprovalBypassResult(request, mode);
            }
        }

        validate(request);

        String currentUserId = String.valueOf(StpUtil.getLoginId());

        Map<String, Object> variable = new LinkedHashMap<>();
        if (request.getFlowParams() != null) {
            variable.putAll(request.getFlowParams());
        }

        List<String> dataProviderOrgIds = resolveFlowOrgIds(request.getFlowOrderId());
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
            JSONUtil.toJsonStr(resolveFlowExtInfo(request.getFlowOrderId(), request.getFlowType())),
            variable
        ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instance.id());
        result.put("flowCode", request.getFlowType());
        result.put("flowOrderId", request.getFlowOrderId());
        return result;
    }

    private boolean isAssetApprovalType(String approveType) {
        return "checkIn".equals(approveType) || "assetUpdate".equals(approveType);
    }

    private Map<String, Object> buildApprovalBypassResult(ApprovalStartRequest request, DataAssetApprovalMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", null);
        result.put("flowCode", request.getFlowType());
        result.put("flowOrderId", request.getFlowOrderId());
        result.put("approvalMode", mode.name());
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

        List<DaOrderAssetRela> relas = daOrderAssetRelaService.list(
            new LambdaQueryWrapper<DaOrderAssetRela>()
                .eq(DaOrderAssetRela::getAssetId, request.getTid())
                .eq(DaOrderAssetRela::getFlowOrderId, request.getFlowOrderId())
                .eq(DaOrderAssetRela::getFlowStatus, 1)
        );
        if (relas.isEmpty()) {
            throw new BizException(500, "撤销记录不存在");
        }

        DaOrderAssetRela rela = relas.get(0);
        Long instanceId = approvalEngine.findInstanceId(rela.getFlowOrderId()).orElse(null);
        if (instanceId == null) {
            throw new BizException(500, "流程实例不存在");
        }
        Long taskIdNum = approvalEngine.findFirstTaskId(instanceId).orElse(null);
        if (taskIdNum == null) {
            throw new BizException(500, "流程任务不存在");
        }
        String taskId = String.valueOf(taskIdNum);

        List<DaAssetT> assets = daAssetTService.list(new LambdaQueryWrapper<DaAssetT>().eq(DaAssetT::getTid, rela.getAssetId()));
        String approveType = "checkIn";
        if (!assets.isEmpty() && Objects.equals(assets.get(0).getAssetStatus(), 2)) {
            approveType = "assetUpdate";
        }

        ApprovalHandleRequest handleRequest = new ApprovalHandleRequest();
        handleRequest.setTaskId(taskId);
        handleRequest.setBusinessId(rela.getFlowOrderId());
        handleRequest.setHandleType("REVOKE");
        handleRequest.setMessage(request.getMessage());
        handleRequest.setNodeCode("end");
        handleRequest.setApproveType(approveType);
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

        boolean assetFlow = "checkIn".equals(approveType) || "assetUpdate".equals(approveType);
        LocalDateTime now = LocalDateTime.now();
        if ("haoyuePublication".equals(approveType) || "haoyueSubscription".equals(approveType)) {
            assetCenterApprovalBridge.complete(approveType, businessId, flowStatus);
        } else if (assetFlow) {
            completeAssetApprovalByFlowOrderId(businessId, approveType, flowStatus);
        } else {
            dataApplyFormTService.update(
                new LambdaUpdateWrapper<DataApplyFormT>()
                    .eq(DataApplyFormT::getTid, businessId)
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

    public void completeAssetApprovalPassByFlowOrderId(String flowOrderId, String approveType) {
        completeAssetApprovalByFlowOrderId(flowOrderId, approveType, 2);
    }

    public void completeAssetApprovalByFlowOrderId(String flowOrderId, String approveType, int flowStatus) {
        boolean assetFlow = "checkIn".equals(approveType) || "assetUpdate".equals(approveType);
        if (!assetFlow) {
            throw new BizException(500, "不支持的资产审批类型: " + approveType);
        }

        List<DaOrderAssetRela> relas = daOrderAssetRelaService.list(
            new LambdaQueryWrapper<DaOrderAssetRela>().eq(DaOrderAssetRela::getFlowOrderId, flowOrderId)
        );
        if (relas.isEmpty()) {
            throw new BizException(500, "未找到关联的资产");
        }

        LocalDateTime now = LocalDateTime.now();
        for (DaOrderAssetRela rela : relas) {
            applyAssetApprovalResult(rela, flowStatus, now);
        }
    }

    private void applyAssetApprovalResult(DaOrderAssetRela rela, int flowStatus, LocalDateTime now) {
        String assetId = rela.getAssetId();
        daAssetTService.update(new LambdaUpdateWrapper<DaAssetT>()
            .eq(DaAssetT::getTid, assetId)
            .set(DaAssetT::getFlowStatus, flowStatus));

        rela.setFlowStatus(flowStatus);
        rela.setUpdatedTime(now);
        daOrderAssetRelaService.updateById(rela);

        boolean needsEsUpdate = false;
        if (flowStatus == 2) {
            needsEsUpdate = true;
            if ("assetUpdate".equals(rela.getFlowType())) {
                applyAssetUpdateNewData(assetId, rela.getAssetNewData());
            }
            daAssetTService.update(new LambdaUpdateWrapper<DaAssetT>()
                .eq(DaAssetT::getTid, assetId)
                .set(DaAssetT::getAssetStatus, 2)
                .set(DaAssetT::getFlowOrderId, null)
                .set(DaAssetT::getUpdatedTime, now));
        }

        if (flowStatus == 4 || flowStatus == 3) {
            needsEsUpdate = true;
            if ("checkIn".equals(rela.getFlowType())) {
                daAssetTService.update(new LambdaUpdateWrapper<DaAssetT>()
                    .eq(DaAssetT::getTid, assetId)
                    .set(DaAssetT::getAssetStatus, 0)
                    .set(DaAssetT::getFlowOrderId, null)
                    .set(DaAssetT::getUpdatedTime, now));
            } else if ("assetUpdate".equals(rela.getFlowType())) {
                daAssetTService.update(new LambdaUpdateWrapper<DaAssetT>()
                    .eq(DaAssetT::getTid, assetId)
                    .set(DaAssetT::getAssetStatus, 2)
                    .set(DaAssetT::getFlowOrderId, null)
                    .set(DaAssetT::getUpdatedTime, now));
            }
        }

        if (needsEsUpdate) {
            Map<String, Object> resultData = buildAssetEsDoc(assetId);
            if ("catalog".equals(stringValue(resultData.get("assetType")))) {
                String sourceTableId = stringValue(resultData.get("sourceTableId"));
                if (sourceTableId != null && !sourceTableId.isBlank()) {
                    daAssetTService.update(new LambdaUpdateWrapper<DaAssetT>()
                        .eq(DaAssetT::getTid, sourceTableId)
                        .set(DaAssetT::getAssetStatus, 2)
                        .set(DaAssetT::getFlowOrderId, null)
                        .set(DaAssetT::getUpdatedTime, now));
                    esCommonService.saveOrUpdate("dataassets", sourceTableId, "{\"assetStatus\":2}", true);
                }
            }
            esCommonService.saveOrUpdate("dataassets", assetId, JSONUtil.toJsonStr(resultData), true);
        }
    }

    private void applyAssetUpdateNewData(String assetId, String assetNewData) {
        if (assetNewData == null || assetNewData.isBlank()) {
            return;
        }
        try {
            Map<String, Object> newData = objectMapper.readValue(assetNewData, new TypeReference<Map<String, Object>>() {});
            Object propListObj = newData.get("propList");
            if (propListObj instanceof Map<?, ?> propMapObj) {
                Map<String, Object> props = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : propMapObj.entrySet()) {
                    if (entry.getKey() != null) {
                        props.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                DataAssetPropSaveRequest propRequest = new DataAssetPropSaveRequest();
                propRequest.setTid(assetId);
                propRequest.setProps(props);
                dataAssetRuntimeAdapter.propSaveOrUpdate(propRequest);
            }

            Object catalogItemsObj = newData.get("catalogItems");
            if (catalogItemsObj instanceof List<?> rawList && !rawList.isEmpty()) {
                List<CatalogItemValueRequest> catalogItems = objectMapper.convertValue(
                    rawList, new TypeReference<List<CatalogItemValueRequest>>() {}
                );
                CatalogItemSaveOrUpdateRequest request = new CatalogItemSaveOrUpdateRequest();
                request.setCatalogId(assetId);
                request.setCatalogItems(catalogItems);
                dataAssetRuntimeAdapter.catalogItemSaveOrUpdate(request);
            }
        } catch (Exception e) {
            throw new BizException(500, "审批通过后应用资产新数据失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildAssetEsDoc(String assetId) {
        List<DaAssetT> assets = daAssetTService.list(
            new LambdaQueryWrapper<DaAssetT>()
                .eq(DaAssetT::getTid, assetId)
                .eq(DaAssetT::getIsDel, 0)
        );
        DaAssetT mainData = assets.isEmpty() ? null : assets.get(0);
        Map<String, Object> resultData = new LinkedHashMap<>();
        if (mainData != null) {
            resultData.put("tid", mainData.getTid());
            resultData.put("tenantId", mainData.getTenantId());
            resultData.put("assetType", mainData.getAssetType());
            resultData.put("assetStatus", mainData.getAssetStatus() == null ? 0 : mainData.getAssetStatus());
            resultData.put("flowStatus", mainData.getFlowStatus() == null ? 0 : mainData.getFlowStatus());
            resultData.put("regTime", formatTime(mainData.getRegTime()));
            resultData.put("createdTime", formatTime(mainData.getCreatedTime()));
            resultData.put("updatedTime", formatTime(mainData.getUpdatedTime()));
            resultData.put("dataCatalogNum", mainData.getDataCatalogNum());
            resultData.put("flowOrderId", mainData.getFlowOrderId());
        }

        List<DataPropT> propData = dataPropTService.list(
            new LambdaQueryWrapper<DataPropT>()
                .eq(DataPropT::getParentId, assetId)
                .eq(DataPropT::getIsDel, 0)
        );
        for (DataPropT prop : propData) {
            if (prop.getPropName() != null) {
                resultData.put(prop.getPropName(), prop.getPropValue());
            }
        }
        return resultData;
    }

    /**
     * 根据 flowOrderId 恢复关联资产状态，并同步 ES。
     * checkIn -> asset_status = 0
     * assetUpdate -> asset_status = 2
     */
    @Transactional(rollbackFor = Exception.class)
    public Object restoreAssetStatusByFlowOrderId(ApprovalRestoreAssetStatusRequest request) {
        if (request == null || request.getFlowOrderId() == null || request.getFlowOrderId().isBlank()) {
            throw new BizException(-1, "flowOrderId不能为空");
        }

        List<DaOrderAssetRela> relaList = daOrderAssetRelaService.list(
            new LambdaQueryWrapper<DaOrderAssetRela>()
                .eq(DaOrderAssetRela::getFlowOrderId, request.getFlowOrderId())
        );
        if (relaList == null || relaList.isEmpty()) {
            throw new BizException(500, "未找到关联资产关系数据");
        }

        LocalDateTime now = LocalDateTime.now();
        int successCount = 0;
        int skipCount = 0;
        List<String> updatedAssetIds = new ArrayList<>();
        for (DaOrderAssetRela rela : relaList) {
            if (rela == null || rela.getAssetId() == null || rela.getAssetId().isBlank()) {
                skipCount++;
                continue;
            }

            Integer restoreStatus = null;
            Integer restoreFlowStatus = null;
            if ("checkIn".equals(rela.getFlowType())) {
                restoreStatus = 0;
                restoreFlowStatus = 0;
            } else if ("assetUpdate".equals(rela.getFlowType())) {
                restoreStatus = 2;
                restoreFlowStatus = 2;
            }
            if (restoreStatus == null) {
                skipCount++;
                continue;
            }

            // 恢复操作统一标记为撤回
//            rela.setFlowStatus(4);
//            rela.setUpdatedTime(now);
//            rela.setIsDel(1);
//            daOrderAssetRelaService.updateById(rela);
            daOrderAssetRelaService.removeById(rela.getTid());

            daAssetTService.update(
                new LambdaUpdateWrapper<DaAssetT>()
                    .eq(DaAssetT::getTid, rela.getAssetId())
                    .set(DaAssetT::getAssetStatus, restoreStatus)
                    .set(DaAssetT::getFlowStatus, restoreFlowStatus)
                    //更新时间待定
                    //.set(DaAssetT::getUpdatedTime, now)
            );

            Map<String, Object> resultData = buildAssetEsDoc(rela.getAssetId());
            esCommonService.saveOrUpdate("dataassets", rela.getAssetId(), JSONUtil.toJsonStr(resultData), true);
            successCount++;
            updatedAssetIds.add(rela.getAssetId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flowOrderId", request.getFlowOrderId());
        result.put("total", relaList.size());
        result.put("success", successCount);
        result.put("skipped", skipCount);
        result.put("updatedAssetIds", updatedAssetIds);

        // 同步软删除资产关联关系
        daOrderAssetRelaService.update(
            new LambdaUpdateWrapper<DaOrderAssetRela>()
                .eq(DaOrderAssetRela::getFlowOrderId, request.getFlowOrderId())
                .set(DaOrderAssetRela::getIsDel, 1)
                .set(DaOrderAssetRela::getUpdatedTime, now)
        );

        // 清理 warm-flow 中与 business_id 关联的流程数据
        Map<String, Integer> warmFlowDeleteCount = approvalEngine.deleteInstances(request.getFlowOrderId()).asMap();
        result.put("warmFlowDeleted", warmFlowDeleteCount);
        return result;
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

    private List<String> resolveFlowOrgIds(String flowOrderId) {
        List<String> orgIds = new ArrayList<>();
        if (flowOrderId == null || flowOrderId.isBlank()) {
            return orgIds;
        }

        List<DaOrderAssetRela> relaList = daOrderAssetRelaService.list(
            new LambdaQueryWrapper<DaOrderAssetRela>().eq(DaOrderAssetRela::getFlowOrderId, flowOrderId)
        );

        for (DaOrderAssetRela rela : relaList) {
            if (rela.getAssetId() == null || rela.getAssetId().isBlank()) {
                continue;
            }
            Map<String, Object> assetDoc = esCommonService.getDocument("dataassets", rela.getAssetId());
            addUnique(orgIds, stringValue(assetDoc == null ? null : assetDoc.get("orgId")));
        }

        if (orgIds.isEmpty()) {
            Map<String, Object> applyDoc = esCommonService.getDocument("dataassets_apply_form", flowOrderId);
            addUnique(orgIds, stringValue(applyDoc == null ? null : applyDoc.get("orgId")));
        }
        return orgIds;
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

    private Map<String, Object> resolveFlowExtInfo(String flowOrderId, String flowCode) {
        Map<String, Object> extInfo = new LinkedHashMap<>();
        extInfo.put("businessId", flowOrderId);
        extInfo.put("businessType", "");
        extInfo.put("businessName", "");
        extInfo.put("flowCode", flowCode);
        extInfo.put("flowName", "");
        extInfo.put("keyword", "");
        extInfo.put("relaId", "");

        approvalEngine.publishedDefinitionName(flowCode)
            .ifPresent(flowName -> extInfo.put("flowName", flowName));

        List<DaOrderAssetRela> relaList = daOrderAssetRelaService.list(
            new LambdaQueryWrapper<DaOrderAssetRela>().eq(DaOrderAssetRela::getFlowOrderId, flowOrderId)
        );

        if (!relaList.isEmpty()) {
            List<String> assetNames = new ArrayList<>();
            for (DaOrderAssetRela item : relaList) {
                Map<String, Object> assetDoc = esCommonService.getDocument("dataassets", item.getAssetId());
                if (assetDoc == null) {
                    continue;
                }
                String assetName = switch (stringValue(item.getAssetType())) {
                    case "app" -> stringValue(assetDoc.get("appName"));
                    case "db" -> stringValue(assetDoc.get("dbName"));
                    case "table" -> stringValue(assetDoc.get("tableName"));
                    case "catalog" -> stringValue(assetDoc.get("catalogName"));
                    default -> null;
                };
                addUnique(assetNames, assetName);
            }
            extInfo.put("relaId", relaList.get(0).getAssetId());
            extInfo.put("businessType", "asset");
            extInfo.put("businessName", String.join(", ", assetNames));
            extInfo.put("assetNames", assetNames);
        } else {
            Map<String, Object> applyDoc = esCommonService.getDocument("dataassets_apply_form", flowOrderId);
            if (applyDoc != null) {
                String applyName = stringValue(applyDoc.get("applyName"));
                extInfo.put("businessType", "applyForm");
                extInfo.put("businessName", applyName);
                extInfo.put("applyName", applyName);
                extInfo.put("relaId", stringValue(applyDoc.get("tid")));
            }
        }

        String flowName = stringValue(extInfo.get("flowName"));
        String businessName = stringValue(extInfo.get("businessName"));
        String keyword = ((flowName == null ? "" : flowName) + " " + (businessName == null ? "" : businessName)).trim();
        extInfo.put("keyword", keyword);
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

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}

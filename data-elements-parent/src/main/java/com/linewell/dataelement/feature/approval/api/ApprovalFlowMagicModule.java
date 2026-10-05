package com.linewell.dataelement.feature.approval.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import com.linewell.dataelement.model.approval.ApprovalHandleRequest;
import com.linewell.dataelement.model.approval.ApprovalPreviousNodeRequest;
import com.linewell.dataelement.model.approval.ApprovalRestoreAssetStatusRequest;
import com.linewell.dataelement.model.approval.ApprovalRevokeRequest;
import com.linewell.dataelement.model.approval.ApprovalStartRequest;
import com.linewell.dataelement.model.approval.ApprovalTerminationRequest;
import com.linewell.dataelement.feature.approval.application.ApprovalFlowService;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * The single Magic-facing entry point for approval workflow operations.
 * HTTP routing remains in the 08.审批中心 Magic resources; this module only
 * adapts untyped Magic request bodies to the application service commands.
 */
@Component
@MagicModule("approvalFlow")
public class ApprovalFlowMagicModule {

    private final ApprovalFlowService service;
    private final ObjectMapper objectMapper;

    public ApprovalFlowMagicModule(ApprovalFlowService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Comment("发起审批流程")
    public Map<String, Object> start(Map<String, Object> body) {
        return service.start(command(body, ApprovalStartRequest.class));
    }

    @Comment("办理审批任务")
    public Map<String, Object> handle(Map<String, Object> body) {
        return service.handle(command(body, ApprovalHandleRequest.class));
    }

    @Comment("撤销审批流程")
    public Map<String, Object> revoke(Map<String, Object> body) {
        return service.revoke(command(body, ApprovalRevokeRequest.class));
    }

    @Comment("终止审批流程")
    public Object termination(Map<String, Object> body) {
        return service.termination(command(body, ApprovalTerminationRequest.class));
    }

    @Comment("查询可退回节点")
    public Object previousNodeList(Map<String, Object> body) {
        return service.previousNodeList(command(body, ApprovalPreviousNodeRequest.class));
    }

    @Comment("处理流程节点完成回调")
    public Object finshHandle(Map<String, Object> body) {
        return service.finshHandle(command(body, ApprovalCallbackRequest.class));
    }

    @Comment("按流程单号恢复关联资产状态")
    public Object restoreAssetStatusByFlowOrderId(Map<String, Object> body) {
        return service.restoreAssetStatusByFlowOrderId(command(body, ApprovalRestoreAssetStatusRequest.class));
    }

    private <T> T command(Map<String, Object> body, Class<T> type) {
        return objectMapper.convertValue(safe(body), type);
    }

    private Map<String, Object> safe(Map<String, Object> body) {
        return body == null ? Map.of() : body;
    }
}

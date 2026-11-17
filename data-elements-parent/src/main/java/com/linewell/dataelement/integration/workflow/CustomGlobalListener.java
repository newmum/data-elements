package com.linewell.dataelement.integration.workflow;

import com.linewell.dataelement.magic.runtime.route.RegisteredMagicApiInvoker;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import java.util.Map;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Translates engine lifecycle callbacks into platform approval events.
 */
@Component
public class CustomGlobalListener implements GlobalListener {

    private static final Logger log = LoggerFactory.getLogger(CustomGlobalListener.class);
    private static final String FINISH_RESOURCE_ID = "c30f3718868148ce84e65217f49a7720";

    private final RegisteredMagicApiInvoker magicApi;

    public CustomGlobalListener(@Lazy RegisteredMagicApiInvoker magicApi) {
        this.magicApi = magicApi;
    }

    @Override
    public void finish(ListenerVariable listenerVariable) {
        if (listenerVariable.getInstance() == null) {
            return;
        }
        Map<String, Object> variables = listenerVariable.getVariable();
        String businessId = listenerVariable.getInstance().getBusinessId();
        Object approval = variables == null ? null : variables.get("approveType");
        if (businessId == null || businessId.isBlank() || !(approval instanceof String approveType)
                || approveType.isBlank()) {
            return;
        }
        // 历史登记流程仍可在引擎中流转，已退役的资产登记回调不再连接业务记录。
        if ("checkIn".equals(approveType) || "assetUpdate".equals(approveType)) {
            return;
        }
        ApprovalCallbackRequest event = new ApprovalCallbackRequest();
        // 业务单号取自引擎实例，办理请求不能改写回调的目标。
        event.setBusinessId(businessId);
        event.setApproveType(approveType);
        event.setHandleType(variables.get("handleType") instanceof String action ? action : null);
        event.setNextNodeCode(listenerVariable.getNextNodes() == null || listenerVariable.getNextNodes().isEmpty()
                ? ""
                : listenerVariable.getNextNodes().get(0).getNodeCode());
        magicApi.execute(FINISH_RESOURCE_ID, Map.of("engineEvent", event));
        log.debug("Approval task lifecycle event dispatched: instanceId={}",
                listenerVariable.getInstance().getId());
    }
}

package com.linewell.dataelement.feature.approval.infrastructure.warmflow.listener;

import com.linewell.dataelement.feature.approval.application.ApprovalLifecyclePort;
import java.util.LinkedHashMap;
import java.util.Map;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Translates engine lifecycle callbacks into platform approval events.
 */
@Component
public class CustomGlobalListener implements GlobalListener {

    private static final Logger log = LoggerFactory.getLogger(CustomGlobalListener.class);

    private final ApprovalLifecyclePort lifecyclePort;

    public CustomGlobalListener(ApprovalLifecyclePort lifecyclePort) {
        this.lifecyclePort = lifecyclePort;
    }

    @Override
    public void finish(ListenerVariable listenerVariable) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("nextNodeCode", listenerVariable.getNextNodes() == null || listenerVariable.getNextNodes().isEmpty()
                ? ""
                : listenerVariable.getNextNodes().get(0).getNodeCode());
        event.put("reqParam", listenerVariable.getVariable());
        lifecyclePort.taskFinished(event);
        log.debug("Approval task lifecycle event dispatched: instanceId={}",
                listenerVariable.getInstance() == null ? null : listenerVariable.getInstance().getId());
    }
}

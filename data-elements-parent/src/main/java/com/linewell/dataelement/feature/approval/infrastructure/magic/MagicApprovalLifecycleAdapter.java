package com.linewell.dataelement.feature.approval.infrastructure.magic;

import com.linewell.dataelement.feature.approval.application.ApprovalLifecyclePort;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.service.MagicAPIService;

@Component
public class MagicApprovalLifecycleAdapter implements ApprovalLifecyclePort {

    private final MagicAPIService magicApiService;

    public MagicApprovalLifecycleAdapter(MagicAPIService magicApiService) {
        this.magicApiService = magicApiService;
    }

    @Override
    public void taskFinished(ApprovalCallbackRequest event) {
        magicApiService.execute("POST", "/sym/approval/finish", Map.of("engineEvent", event));
    }
}

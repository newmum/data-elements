package com.linewell.dataelement.feature.approval.infrastructure.magic;

import com.linewell.dataelement.feature.approval.application.ApprovalLifecyclePort;
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
    public void taskFinished(Map<String, Object> event) {
        magicApiService.execute("POST", "/sym/approval/callback/finshHandle", event);
    }
}

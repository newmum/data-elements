package com.linewell.dataelement.feature.approval.application;

import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;

public interface ApprovalLifecyclePort {

    void taskFinished(ApprovalCallbackRequest event);
}

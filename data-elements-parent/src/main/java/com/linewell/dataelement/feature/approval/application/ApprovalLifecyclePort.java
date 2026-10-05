package com.linewell.dataelement.feature.approval.application;

import java.util.Map;

public interface ApprovalLifecyclePort {

    void taskFinished(Map<String, Object> event);
}

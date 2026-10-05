package com.linewell.dataelement.model.approval;

import java.util.Map;
import lombok.Data;

@Data
public class ApprovalStartRequest {
    private String flowType;
    private String approveType;
    private String flowOrderId;
    private String appId;
    private Map<String, Object> flowParams;
}

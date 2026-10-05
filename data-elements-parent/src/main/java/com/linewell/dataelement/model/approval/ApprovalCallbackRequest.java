package com.linewell.dataelement.model.approval;

import lombok.Data;

@Data
public class ApprovalCallbackRequest {
    private String approveType;
    private String handleType;
    private String businessId;
    private String nextNodeCode;
}

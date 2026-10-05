package com.linewell.dataelement.model.approval;

import lombok.Data;

@Data
public class ApprovalRevokeRequest {
    private String tid;
    private String flowOrderId;
    private String message;
}

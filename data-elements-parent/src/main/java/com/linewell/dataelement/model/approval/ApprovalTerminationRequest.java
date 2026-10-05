package com.linewell.dataelement.model.approval;

import lombok.Data;

@Data
public class ApprovalTerminationRequest {
    private String instanceId;
    private String message;
}

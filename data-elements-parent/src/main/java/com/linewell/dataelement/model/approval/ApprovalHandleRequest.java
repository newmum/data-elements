package com.linewell.dataelement.model.approval;

import java.util.List;
import lombok.Data;

@Data
public class ApprovalHandleRequest {
    private String taskId;
    private String businessId;
    private String handleType;
    private String message;
    private String nodeCode;
    private String approveType;
    private String suggestionId;
    private Integer storageSuggestion;
    private List<String> targetHandlers;
    private List<String> reductionHandlers;
}

package com.linewell.dataelement.feature.approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "dataasset.approval")
public class DataAssetApprovalProperties {

    private DataAssetApprovalMode mode = DataAssetApprovalMode.REQUIRED;

    public DataAssetApprovalMode getMode() {
        return mode;
    }

    public void setMode(DataAssetApprovalMode mode) {
        this.mode = mode == null ? DataAssetApprovalMode.REQUIRED : mode;
    }

    public DataAssetApprovalMode currentMode() {
        return mode == null ? DataAssetApprovalMode.REQUIRED : mode;
    }
}

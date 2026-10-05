package com.linewell.dataelement.model.dataasset;

import lombok.Data;

@Data
public class DataAssetQueryByIdRequest {
    private String tid;
    private String tenantId;
    private String assetType;
}

package com.linewell.dataelement.model.dataasset;

import java.util.Map;
import lombok.Data;

@Data
public class DataAssetPropSaveRequest {
    private String tid;
    private Map<String, Object> props;
}

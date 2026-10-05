package com.linewell.dataelement.model.dataasset;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DataAssetPropBatchSaveRequest {
    private List<String> tids;
    private Map<String, Object> props;
}

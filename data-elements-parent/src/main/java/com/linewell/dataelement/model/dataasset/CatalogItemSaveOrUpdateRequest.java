package com.linewell.dataelement.model.dataasset;

import java.util.List;
import lombok.Data;

@Data
public class CatalogItemSaveOrUpdateRequest {

    private String tid;
    private String catalogId;
    private List<CatalogItemValueRequest> catalogItems;
}

package com.linewell.dataelement.metautil.structured;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class StructuredProbeResult {

    private String sourceType;
    private String product;
    private String endpoint;
    private String responsePreview;
    private List<StructuredTableData> tables = new ArrayList<>();
}

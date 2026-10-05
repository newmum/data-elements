package com.linewell.dataelement.metautil.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PageQueryResult {

    private Integer pageNo;
    private Integer pageSize;
    private Long total;
    private List<String> columns = new ArrayList<>();
    private List<String> columnTypes = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
}


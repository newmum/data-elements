package com.linewell.dataelement.metautil.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class PageQueryRequest {

    @Valid
    @NotNull
    private DataSourceConfig dataSource;

    @NotBlank
    private String tableName;

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 20;

    private List<String> columns = new ArrayList<>();
    private List<PageQueryCondition> conditions = new ArrayList<>();
    private List<PageQuerySort> sorts = new ArrayList<>();
}


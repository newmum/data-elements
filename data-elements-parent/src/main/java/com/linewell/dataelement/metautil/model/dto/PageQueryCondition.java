package com.linewell.dataelement.metautil.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PageQueryCondition {

    @NotBlank
    private String field;

    @NotBlank
    private String op;

    @NotNull
    private Object value;
}


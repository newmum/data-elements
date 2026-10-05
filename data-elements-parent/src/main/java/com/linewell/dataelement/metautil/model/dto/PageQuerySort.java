package com.linewell.dataelement.metautil.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PageQuerySort {

    @NotBlank
    private String field;

    private String direction = "ASC";
}


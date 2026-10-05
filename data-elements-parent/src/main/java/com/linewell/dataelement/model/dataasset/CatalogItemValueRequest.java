package com.linewell.dataelement.model.dataasset;

import lombok.Data;

@Data
public class CatalogItemValueRequest {

    private String tid;
    private String id;
    private String colName;
    private String colEn;
    private String colType;
    private Long colLength;
    private String isPk;
    private String isNullable;
    private String dataStandardId;
    private String qualityRule;
    private Integer enableCodeTable;
    private String codeTableId;
    private String sourceTableColumnId;
    private String targetTableColumnId;
}

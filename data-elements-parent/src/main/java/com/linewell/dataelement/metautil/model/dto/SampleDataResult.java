package com.linewell.dataelement.metautil.model.dto;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 数据抽样结果
 * 
 * @author MetaUtil
 */
@Data
public class SampleDataResult {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 抽样数量
     */
    private Integer sampleSize;

    /**
     * 实际返回数量
     */
    private Integer actualSize;

    /**
     * 表总记录数
     */
    private Long totalCount;

    /**
     * 列名列表
     */
    private List<String> columnNames;

    /**
     * 列类型列表
     */
    private List<String> columnTypes;

    /**
     * 抽样数据
     */
    private List<Map<String, Object>> data;

    /**
     * 抽样方式（RANDOM/FIRST/LAST）
     */
    private String sampleMethod;
}

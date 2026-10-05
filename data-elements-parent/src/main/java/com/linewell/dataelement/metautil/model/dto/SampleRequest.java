package com.linewell.dataelement.metautil.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 数据抽样请求
 * 
 * @author MetaUtil
 */
@Data
public class SampleRequest {

    @Valid
    @NotNull(message = "数据源配置不能为空")
    /**
     * 数据源配置
     */
    private DataSourceConfig dataSource;

    @NotBlank(message = "表名不能为空")
    /**
     * 表名
     */
    private String tableName;

    @Min(value = 1, message = "抽样数量最小为1")
    @Max(value = 10000, message = "抽样数量最大为10000")
    /**
     * 抽样数量
     */
    private Integer sampleSize = 100;

    @NotNull(message = "抽样方式不能为空")
    /**
     * 抽样方式（RANDOM/FIRST/LAST）
     */
    private SampleMethod sampleMethod = SampleMethod.FIRST;

    @NotBlank(message = "指定查询的列不能为空")
    /**
     * 指定查询的列（为空则查询所有列）
     */
    private String[] columns;

    /**
     * MagicScript binds JSON arrays as List implementations. Keep the generated
     * array setter while accepting that runtime representation as well.
     */
    public void setColumns(List<String> columns) {
        this.columns = columns == null ? null : columns.toArray(String[]::new);
    }

    @NotBlank(message = "WHERE条件不能为空")
    /**
     * WHERE条件（不包含WHERE关键字）
     */
    private String whereClause;

    /**
     * 抽样方式枚举
     */
    public enum SampleMethod {
        /**
         * 随机抽样
         */
        RANDOM,
        /**
         * 取前N条
         */
        FIRST,
        /**
         * 取后N条
         */
        LAST
    }
}

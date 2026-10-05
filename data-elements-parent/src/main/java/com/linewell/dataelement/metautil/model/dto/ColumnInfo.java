package com.linewell.dataelement.metautil.model.dto;

import lombok.Data;

/**
 * 字段信息
 * 
 * @author MetaUtil
 */
@Data
public class ColumnInfo {

    /** Optional origin dialect, used when cross-database types have different semantics (Oracle DATE). */
    private String sourceDatabaseType;

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 字段中文名/注释
     */
    private String columnComment;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 完整类型（包含长度精度等）
     */
    private String columnType;

    /**
     * 字段长度
     */
    private Long length;

    /**
     * 数值精度
     */
    private Integer precision;

    /**
     * 小数位数
     */
    private Integer scale;

    /**
     * 是否可为空
     */
    private Boolean nullable;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 是否为主键
     */
    private Boolean primaryKey;

    /**
     * 是否自增
     */
    private Boolean autoIncrement;

    /**
     * 是否唯一
     */
    private Boolean unique;

    /**
     * 是否有索引
     */
    private Boolean indexed;

    /**
     * 字段序号
     */
    private Integer ordinalPosition;

    /**
     * 字符集
     */
    private String charset;

    /**
     * 排序规则
     */
    private String collation;

    /**
     * 额外信息
     */
    private String extra;
}

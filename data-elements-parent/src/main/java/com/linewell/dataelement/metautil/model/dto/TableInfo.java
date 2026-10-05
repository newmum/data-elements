package com.linewell.dataelement.metautil.model.dto;

import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 表信息
 * 
 * @author MetaUtil
 */
@Data
public class TableInfo {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表中文名/注释
     */
    private String tableComment;

    /**
     * Schema名称
     */
    private String schemaName;

    /**
     * 表类型（TABLE/VIEW）
     */
    private String tableType;

    /**
     * 存储引擎
     */
    private String engine;

    /**
     * 字符集
     */
    private String charset;

    /**
     * 排序规则
     */
    private String collation;

    /**
     * 记录数
     */
    private Long rowCount;

    /** 字段数量 */
    private Integer columnCount;

    /**
     * 数据大小(字节)
     */
    private Long dataSizeBytes;

    /**
     * 数据大小(格式化)
     */
    private String dataSizeFormatted;

    /**
     * 索引大小(字节)
     */
    private Long indexSizeBytes;

    /**
     * 索引大小(格式化)
     */
    private String indexSizeFormatted;

    /**
     * 总大小(字节)
     */
    private Long totalSizeBytes;

    /**
     * 总大小(格式化)
     */
    private String totalSizeFormatted;

    /**
     * 自增值
     */
    private Long autoIncrement;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 数据更新时间
     */
    private Date dataUpdateTime;

    /**
     * 字段列表
     */
    private List<ColumnInfo> columns;

    /**
     * 索引列表
     */
    private List<IndexInfo> indexes;
}

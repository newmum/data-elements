package com.linewell.dataelement.metautil.model.dto;

import java.util.List;
import lombok.Data;

/**
 * 数据库信息
 * 
 * @author MetaUtil
 */
@Data
public class DatabaseInfo {

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 数据库版本
     */
    private String version;

    /**
     * 数据库类型
     */
    private String databaseType;

    /**
     * 字符集
     */
    private String charset;

    /**
     * 排序规则
     */
    private String collation;

    /**
     * 数据库总大小(字节)
     */
    private Long totalSizeBytes;

    /**
     * 数据库总大小(格式化)
     */
    private String totalSizeFormatted;

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
     * 表数量
     */
    private Integer tableCount;

    /**
     * 视图数量
     */
    private Integer viewCount;

    /**
     * 存储过程数量
     */
    private Integer procedureCount;

    /**
     * 用户数量
     */
    private Integer userCount;

    /**
     * Schema列表
     */
    private List<String> schemas;

    /**
     * 数据库列表（当未指定数据库时返回）
     */
    private List<String> databases;
}

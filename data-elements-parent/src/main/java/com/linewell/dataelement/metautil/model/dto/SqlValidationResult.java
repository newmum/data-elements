package com.linewell.dataelement.metautil.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 校验结果
 *
 * @author MetaUtil
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlValidationResult {

    /**
     * 是否校验通过
     */
    private boolean valid;

    /**
     * 错误信息（校验失败时）
     */
    private String errorMessage;

    /**
     * 错误位置（行号，从1开始，可选）
     */
    private Integer errorLine;

    /**
     * 错误位置（列号，从1开始，可选）
     */
    private Integer errorColumn;

    /**
     * SQL 类型（SELECT/INSERT/UPDATE/DELETE/DDL 等）
     */
    private String sqlType;

    /**
     * 数据库类型
     */
    private String databaseType;

    /**
     * 创建成功结果
     */
    public static SqlValidationResult success(String sqlType, String databaseType) {
        return SqlValidationResult.builder()
                .valid(true)
                .sqlType(sqlType)
                .databaseType(databaseType)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static SqlValidationResult failure(String errorMessage, String sqlType, String databaseType) {
        return SqlValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .sqlType(sqlType)
                .databaseType(databaseType)
                .build();
    }

    /**
     * 创建失败结果（带位置信息）
     */
    public static SqlValidationResult failure(String errorMessage, Integer errorLine, Integer errorColumn, 
                                              String sqlType, String databaseType) {
        return SqlValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .errorLine(errorLine)
                .errorColumn(errorColumn)
                .sqlType(sqlType)
                .databaseType(databaseType)
                .build();
    }
}
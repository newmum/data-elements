package com.linewell.dataelement.metautil.model.dto;

import java.util.List;
import lombok.Data;

/**
 * 索引信息
 * 
 * @author MetaUtil
 */
@Data
/**
 * 索引信息
 */
public class IndexInfo {

    /**
     * 索引名称
     */
    private String indexName;

    /**
     * 索引类型（BTREE/HASH/FULLTEXT等）
     */
    private String indexType;

    /**
     * 是否唯一索引
     */
    private Boolean unique;

    /**
     * 是否主键索引
     */
    private Boolean primaryKey;

    /**
     * 索引包含的列
     */
    private List<String> columns;

    /**
     * 索引注释
     */
    private String comment;
}

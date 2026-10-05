package com.linewell.dataelement.elasticsearch;

import java.util.List;
import java.util.Map;

/**
 * 通用批量写入请求：
 * - ids 可选：为空则由 ES 自动生成（或由文档本身的 @Id 决定）
 * - docs：key-value 文档列表
 */
public class EsBulkRequest {
    private List<String> ids;
    private List<Map<String, Object>> docs;

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public List<Map<String, Object>> getDocs() {
        return docs;
    }

    public void setDocs(List<Map<String, Object>> docs) {
        this.docs = docs;
    }
}



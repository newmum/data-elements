{
  "properties": {},
  "id": "8466532157014171aaf6ceb1b9f89910",
  "script": null,
  "groupId": "746218ad99914bdea1a80fc548bbdd4b",
  "name": "03.批量读取采集字段",
  "path": "/batch",
  "method": "POST",
  "description": "一次预取所有请求表，逐 ID 检查租户和角色范围后批量返回字段。",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "CACHED"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import tenantRuntime
import dataScope
import tableRead
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.platform.audit.AuditIdentityResolver
import java.lang.String
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.List
import java.util.Map
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.WebUtils
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import '@/common/metadataCatalogResponse' as result
import '@/common/metadataCatalogScope' as filterScope
import '@/common/metadataColumnsScope' as fields

// 批量读取采集字段。先一次校验全部表归属和角色范围，再批量读字段，任何不可见表都使整批失败。
var servlet = AuditIdentityResolver.request()
var cached = servlet == null ? null : WebUtils.getNativeRequest(servlet, ContentCachingRequestWrapper)
var bytes = cached?.getContentAsByteArray()
var input = body
if (bytes != null && bytes.length > 0 && bytes.length < 65536) {
    // 使用 MVC 原先的字符串列表绑定，保留已公开的非法 JSON 和单值请求行为。
    var types = mapper.getTypeFactory()
    var target = types.constructMapType(Map, types.constructType(String), types.constructCollectionType(List, String))
    try {
        input = mapper.readerFor(target).with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY).readValue(bytes)
    } catch (e) {
        var cause = e
        while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException) && cause.getCause() != null) {
            cause = cause.getCause()
        }
        if (cause instanceof JsonProcessingException) {
            return result(500, new HttpMessageNotReadableException('JSON parse error: ' + cause.getOriginalMessage()))
        }
        throw e
    }
}
if (input == null) {
    return result(500, new HttpMessageNotReadableException(
        'Required request body is missing: public java.util.Map<java.lang.String, java.lang.Object> com.linewell.dataelement.feature.metadatagovernance.api.MetadataCatalogPageController.columnsBatch(java.util.Map<java.lang.String, java.util.List<java.lang.String>>)'))
}
var submitted = input.tableIds
if (submitted != null && !(submitted instanceof List) && !(submitted instanceof Map)) {
    submitted = [submitted]
}
if (!(submitted instanceof List) || submitted.isEmpty() || submitted.size() > 100) {
    return result(500, '每批请选择 1 至 100 张表')
}
var distinct = new LinkedHashSet()
for (entry in submitted) {
    if (entry == null || entry instanceof Map || entry instanceof List) {
        return result(500, '表标识不正确')
    }
    var id = entry.toString()
    if (id.isBlank() || id.length() > 64) {
        return result(500, '表标识不正确')
    }
    distinct.add(id)
}
var ids = new ArrayList(distinct)
try {
    var tenantId = tenantRuntime.id()
    var scope = dataScope.dataSource()
    var col = (a, n, type) => tableRead.column(a, n, type)
    var id = col('t', 'tid', 'TEXT')
    var parent = tableRead.from('db_table_t', 't', tenantId)
    parent.leftJoin('db_datasource_t', 'd', 'tid', 'datasource_id',
        [tableRead.equal(col('d', 'is_del', 'NUMBER'), 0)])
    parent.where(tableRead.all([tableRead.equal(col('t', 'is_del', 'NUMBER'), 0),
        tableRead.not(tableRead.isNull(col('d', 'tid', 'TEXT'))), tableRead.in(id, ids), filterScope('d', scope)]))
    var visible = new LinkedHashSet(parent.rows({tid: id}, ids.size()).map(row => row.tid))
    if (!visible.containsAll(ids)) {
        return result(500, '包含不存在或无权读取的数据表')
    }

    var base = fields(tenantId, scope)
    var projection = base.projection
    projection.put('is_unique', col('c', 'is_unique', 'NUMBER'))
    projection.put('indexed', col('c', 'indexed', 'NUMBER'))
    base.query.where(tableRead.in(projection.table_id, ids))
    var rows = base.query.rows(projection, [projection.table_id, projection.ordinal_position, projection.tid],
        ['asc', 'asc', 'asc'], 10001, true)
    if (rows.size() > 10000) {
        return result(500, '字段超过 10000 项，请缩小批次')
    }
    var indexed = {}
    for (tableId in ids) {
        indexed.put(tableId, [])
    }
    for (row in rows) {
        indexed.get(row.table_id).add(row)
    }
    return result(0, indexed)
} catch (e) {
    return result(500, e.getMessage())
}

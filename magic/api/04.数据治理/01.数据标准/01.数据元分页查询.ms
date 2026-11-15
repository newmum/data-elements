{
  "properties": {},
  "id": "5b83c2702e9b4c9687392a291ca9162a",
  "script": null,
  "groupId": "019b772ee9b44705a27710f91bbd731a",
  "name": "01.数据元分页查询",
  "createTime": 1782955344950,
  "updateTime": 1791107424921,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"10\",\n    \"categoryPath\": \"0,1891200000000000003,1891200000000000301\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据名称、编码、标准编码、分级、分类及发布状态筛选数据元，返回分页记录和总数。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "100120",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "user_no_login",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
import tenantRuntime
import tableRead
import tableSort
import java.lang.Integer
import java.util.Locale
import java.lang.IllegalArgumentException
import java.lang.Boolean
import java.math.BigDecimal
import java.lang.Math
import cn.hutool.core.util.StrUtil
import '@/common/standardCodeResolution' as resolveCodes

// 在当前租户内筛选、排序和分页。代码集匹配沿用 ID、字典编码、代码集编码的顺序。
var tenantId = tenantRuntime.id()
var request = body == null ? {} : body
var pageNum = request.pageNum == null ? 1 : Math.max(1, Integer.parseInt('' + request.pageNum))
var pageSize = request.pageSize == null ? 20 : Math.min(100, Math.max(1, Integer.parseInt('' + request.pageSize)))
var sortField = tableSort.field(request, {
    metaName: 'meta_name', name: 'meta_name', metaCode: 'meta_code', code: 'meta_code',
    standardEncode: 'standard_encode', dictionaryCount: 'dictionary_count',
    fieldLength: 'field_length', length: 'field_length', createdTime: 'created_time',
    updatedTime: 'updated_time', updatedAt: 'updated_time'
}, 'createdTime')
var direction = tableSort.direction(request)
var query = tableRead.from('da_metadata_t', 'element', tenantId)
query.where(tableRead.equal(tableRead.column('element', 'is_del', 'NUMBER'), 0))

for (key, column in {metaName: 'meta_name', metaCode: 'meta_code', standardEncode: 'standard_encode',
        categoryPath: 'data_category_path', dataLevel: 'data_level', dataCategoryId: 'data_category_id',
        publishStatus: 'publish_status', valueDomainId: 'value_domain_id'}) {
    var value = request[key] == null ? '' : ('' + request[key]).trim()
    if (value == '') { continue }
    if (value.length() > 500) { throw new IllegalArgumentException('标准筛选内容过长') }
    var field = tableRead.column('element', column, key == 'publishStatus' ? 'NUMBER' : 'TEXT')
    if (key == 'categoryPath') {
        query.where(tableRead.startsWithLiteral(field, value))
    } else if (['metaName', 'metaCode', 'standardEncode'].contains(key)) {
        query.where(tableRead.containsLiteral(field, value))
    } else {
        query.where(tableRead.equal(field, key == 'publishStatus' ? new BigDecimal(value) : value))
    }
}
if (request.encodingOnly == true && request.encodingOnly instanceof Boolean) {
    var pattern = tableRead.trim(tableRead.column('element', 'format_pattern', 'TEXT'))
    query.where(tableRead.greaterThan(tableRead.textLength(pattern), 0))
}

// 分页总数只使用数据元筛选。代码集和项数在数据库排序前计算，保留未绑定的数据元。
var matched = resolveCodes(tenantId)
var counts = tableRead.from('da_metadata_code_t', 'code_items', tenantId)
var codeId = tableRead.column('code_items', 'tid', 'TEXT')
var itemCount = tableRead.jsonArrayLength(tableRead.column('code_items', 'dict_item_value', 'LOB'))
counts.where(tableRead.equal(tableRead.column('code_items', 'is_del', 'NUMBER'), 0))
matched.leftJoinGroupedProjection(counts, 'item_counts', 'code_id', 'code_id',
    {code_id: codeId, item_count: tableRead.max(itemCount)}, [codeId])
var elementId = tableRead.column('resolved', 'element_id', 'TEXT')
query.leftJoinGroupedProjection(matched, 'code_lookup', 'element_id', 'tid', {
    element_id: elementId,
    code_id: tableRead.max(tableRead.column('resolved', 'code_id', 'TEXT')),
    dictionary_count: tableRead.max(tableRead.column('item_counts', 'item_count', 'NUMBER'))
}, [elementId])
var dictionaryCount = tableRead.coalesce([tableRead.column('code_lookup', 'dictionary_count', 'NUMBER'), 0])
var sort = sortField == 'dictionary_count' ? dictionaryCount :
    tableRead.column('element', sortField, sortField == 'field_length' ? 'NUMBER' : 'TEXT')
var page = query.pageRootRows({
    _code_id: tableRead.column('code_lookup', 'code_id', 'TEXT'),
    dictionary_count: dictionaryCount
}, sort, direction, 'tid', pageNum, pageSize)

var rows = []
for (row in page.rows) {
    var result = {CodeRank: 1}
    for (key, value in row) {
        var column = ('' + key).toLowerCase(Locale.ROOT)
        result[column == '_code_id' ? 'CodeId' : StrUtil.toCamelCase(column)] = value
    }
    rows.add(result)
}
return {list: rows, total: page.total, pageNum: pageNum, pageSize: pageSize}

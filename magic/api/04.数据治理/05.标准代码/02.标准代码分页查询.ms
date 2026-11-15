{
  "properties": {},
  "id": "cc413ac10904400d8dcff45b7df52d98",
  "script": null,
  "groupId": "58248497ecfb406e96ac161fcda32c05",
  "name": "02.标准代码分页查询",
  "createTime": 1782955345008,
  "updateTime": 1786717069730,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "支持按字典名称、字典编码、数据分类ID模糊查询；使用请求参数 pageNum、pageSize、dictName、categoryPath；涉及 da_metadata_code_t 等业务表。",
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
        "value": "0",
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
        "value": "success",
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
        "value": "true",
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
            "name": "total",
            "value": "8",
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
            "name": "list",
            "value": "",
            "description": "",
            "required": false,
            "dataType": "Array",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": [
              {
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
                    "name": "tid",
                    "value": "3db170534c774fa5a8a264ffacfb6cf0",
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
                    "name": "dictName",
                    "value": "性别代码",
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
                    "name": "dictCode",
                    "value": "GB/T 2261.1",
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
                    "name": "dataCategoryId",
                    "value": "cat_001",
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
                    "name": "dataCategoryName",
                    "value": "基础数据/人口信息",
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
                    "name": "dataCategoryPath",
                    "value": "/root/cat_001",
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
                    "name": "desc",
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
                  },
                  {
                    "name": "dictItemValue",
                    "value": "[{\"code\":\"0\",\"name\":\"未知的性别\"},{\"code\":\"1\",\"name\":\"男性\"},{\"code\":\"2\",\"name\":\"女性\"},{\"code\":\"9\",\"name\":\"未说明的性别\"}]",
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
                    "name": "createdBy",
                    "value": "admin",
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
                    "name": "createdTime",
                    "value": "2026-03-10 15:34:08",
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
                    "name": "updatedBy",
                    "value": "admin",
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
                    "name": "updatedTime",
                    "value": "2026-03-10 15:34:08",
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
                    "name": "isDel",
                    "value": "0",
                    "description": "",
                    "required": false,
                    "dataType": "Integer",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  }
                ]
              }
            ]
          }
        ]
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
import java.lang.Math
import cn.hutool.core.util.StrUtil
import '@/common/standardCodeResolution' as resolveCodes

// 当前租户仅分页读取有效的代码集头记录。排序可以使用数据库统计的字典项数或引用数。
var tenantId = tenantRuntime.id()
var request = body == null ? {} : body
var pageNum = request.pageNum == null ? 1 : Math.max(1, Integer.parseInt('' + request.pageNum))
var pageSize = request.pageSize == null ? 20 : Math.min(100, Math.max(1, Integer.parseInt('' + request.pageSize)))
var sortField = tableSort.field(request, {
    name: 'dict_name', dictName: 'dict_name', dictCode: 'dict_code', itemCount: 'item_count',
    referenceCount: 'reference_count', createdTime: 'created_time', updatedAt: 'updated_time',
    updatedTime: 'updated_time'
}, 'createdTime')
var direction = tableSort.direction(request)
var query = tableRead.from('da_metadata_code_t', 'code_set', tenantId)
query.where(tableRead.equal(tableRead.column('code_set', 'is_del', 'NUMBER'), 0))
query.where(tableRead.equal(tableRead.column('code_set', 'code_value', 'TEXT'), '__HEADER__'))
for (key, column in {dictName: 'dict_name', dictCode: 'dict_code', categoryPath: 'data_category_path'}) {
    var value = request[key] == null ? '' : ('' + request[key]).trim()
    if (value == '') { continue }
    if (value.length() > 500) { throw new IllegalArgumentException('标准筛选内容过长') }
    var field = tableRead.column('code_set', column, 'TEXT')
    query.where(key == 'categoryPath' ? tableRead.startsWithLiteral(field, value) :
        tableRead.containsLiteral(field, value))
}

// 每个有效数据元只计入其优先匹配的代码集。计数查询跳过此投影关联。
var matched = resolveCodes(tenantId)
var codeId = tableRead.column('resolved', 'code_id', 'TEXT')
matched.where(tableRead.not(tableRead.isNull(codeId)))
query.leftJoinGroupedProjection(matched, 'reference_counts', 'code_id', 'tid',
    {code_id: codeId, reference_count: tableRead.count()}, [codeId])
var itemCount = tableRead.jsonArrayLength(tableRead.column('code_set', 'dict_item_value', 'LOB'))
var referenceCount = tableRead.coalesce([tableRead.column('reference_counts', 'reference_count', 'NUMBER'), 0])
var sorts = {item_count: itemCount, reference_count: referenceCount}
var sort = sorts[sortField] == null ? tableRead.column('code_set', sortField, 'TEXT') : sorts[sortField]
var page = query.pageRootRows({item_count: itemCount, reference_count: referenceCount},
    sort, direction, 'tid', pageNum, pageSize)

var rows = []
for (row in page.rows) {
    var result = {}
    for (key, value in row) {
        result[StrUtil.toCamelCase(('' + key).toLowerCase(Locale.ROOT))] = value
    }
    rows.add(result)
}
return {list: rows, total: page.total, pageNum: pageNum, pageSize: pageSize}

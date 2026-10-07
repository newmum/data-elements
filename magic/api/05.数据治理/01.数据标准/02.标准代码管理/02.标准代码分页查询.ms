{
  "properties": {},
  "id": "cc413ac10904400d8dcff45b7df52d98",
  "script": null,
  "groupId": "27defcc7cb47425eb18cd62f8bf153b0",
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
// 接口：02.标准代码分页查询
// 路由：POST /dwm/standard/code/page
// 业务说明：支持按字典名称、字典编码、数据分类ID模糊查询；使用请求参数 pageNum、pageSize、dictName、categoryPath；涉及 da_metadata_code_t 等业务表。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log

/**
 * 分页查询标准代码表
 * POST /api/gov/standard/code/page
 * 
 * 支持分页查询标准代码表列表
 * 支持按字典名称、字典编码、数据分类ID模糊查询
 */

// 获取分页参数
var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
var offsetLimit = pageNum == 0 ? 0 : (pageNum - 1) * pageSize

// 获取查询条件
var dictName = body?.dictName
var categoryPath = body?.categoryPath

// 构建查询条件
var query = db.table('da_metadata_code_t')
    .where()
    .eq('is_del', 0)
    .eq('code_value', '__HEADER__')

if (dictName != null && dictName != '') {
    query = query.like('dict_name', '%' + dictName + '%')
}

if (categoryPath != null) {
    query = query.like('data_category_path', categoryPath + "%")
}

// ========== 查询总数 ==========
var total = query.count()

// ========== 查询列表 ==========
var list = query
    .orderBy('created_time', 'desc')
    .page(pageSize, offsetLimit)

log.info('分页查询标准代码表成功, pageNum: {}, pageSize: {}, total: {}', pageNum, pageSize, total)

// ========== 返回结果 ==========
return list

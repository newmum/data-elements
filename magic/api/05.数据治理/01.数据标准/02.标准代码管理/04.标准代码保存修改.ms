{
  "properties": {},
  "id": "2c14c1dc3639474f89c2818cf1a5dc25",
  "script": null,
  "groupId": "27defcc7cb47425eb18cd62f8bf153b0",
  "name": "04.标准代码保存修改",
  "createTime": 1782955344995,
  "updateTime": 1786717069661,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dictName\": \"行政区域代码\",\n    \"dictCode\": \"R-INT-001\",\n    \"dataCategoryId\": \"1891200000000000101\",\n    \"describe\": \"行政区域代码\",\n    \"dataCategoryName\": \"基础通用标准\",\n    \"dataCategoryPath\": \"0,1891200000000000001,1891200000000000101\",\n    \"dictItemValue\": \"[{\\\"code\\\":\\\"0\\\",\\\"name\\\":\\\"未定义\\\"}]\",\n    \"tid\": \"6af9d796b7f64f178824b6b170b2a468\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行标准代码保存修改，返回保存或配置结果；使用请求参数 tid、dictName、dictCode、dataCategoryId、dataCategoryName、dataCategoryPath、description、dictItemValue；涉及 da_metadata_code_t 等业务表。",
  "requestBodyDefinition": {
    "name": "root",
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
        "value": "R-INT-002",
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
        "value": "1891200000000000101",
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
        "name": "describe",
        "value": "d",
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
        "value": "基础通用标准",
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
        "value": "0,1891200000000000001,1891200000000000101",
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
        "name": "dictItemValue",
        "value": "55",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  },
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
            "name": "tid",
            "value": "09673d1ff4b4424bb131e823ca2c4eef",
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
            "value": "R-INT-002",
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
            "value": "1891200000000000101",
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
            "value": "基础通用标准",
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
            "value": "0,1891200000000000001,1891200000000000101",
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
            "name": "description",
            "value": "d",
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
            "name": "dictItemValue",
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
            "name": "createdBy",
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
            "name": "createdTime",
            "value": "2026-03-11 14:50:25",
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
            "name": "updatedTime",
            "value": "2026-03-11 14:50:25",
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
}
================================
// 接口：04.标准代码保存修改
// 路由：POST /dwm/standard/code/saveOrUpdate
// 业务说明：校验请求资料后执行标准代码保存修改，返回保存或配置结果；使用请求参数 tid、dictName、dictCode、dataCategoryId、dataCategoryName、dataCategoryPath、description、dictItemValue；涉及 da_metadata_code_t 等业务表。
import cn.hutool.core.util.IdUtil
import log
import jsons

import com.linewell.dataelement.utils.RedisUtils as redis
import "@/common/configData" as configData

/**
 * 保存或更新标准代码表
 * POST /api/gov/standard/code/saveOrUpdate
 *
 * 如果 body.tid 有值则更新，否则新增
 * 数据保存到 da_metadata_code_t 表
 */

// 获取请求参数
var tid = body?.tid
var dictName = body?.dictName
var dictCode = body?.dictCode
var dataCategoryId = body?.dataCategoryId
var dataCategoryName = body?.dataCategoryName
var dataCategoryPath = body?.dataCategoryPath



var describe = body?.description
var dictItemValue = body?.dictItemValue
var codeSet = body?.codeSet || dictCode
var codeValue = body?.codeValue || '__HEADER__'
var codeName = body?.codeName || dictName
var parentCode = body?.parentCode
var sortNo = body?.sortNo || 0
var status = body?.status == null ? 1 : body?.status
var createdBy = body?.createdBy
var updatedBy = body?.updatedBy

var isUpdate = tid != null && tid != ''

// 当前时间
var now = new Date()

// ========== 保存数据 da_metadata_code_t ==========
if (isUpdate) {
    // 更新数据
    db.table('da_metadata_code_t')
        .primary('tid')
        .update({
            tid: tid,
            dict_name: dictName,
            dict_code: dictCode,
            data_category_id: dataCategoryId,
            data_category_name: dataCategoryName,
            data_category_path: dataCategoryPath,
            description: describe,
            dict_item_value: dictItemValue,
            code_set: codeSet,
            code_value: codeValue,
            code_name: codeName,
            parent_code: parentCode,
            sort_no: sortNo,
            status: status,
            updated_by: updatedBy,
            updated_time: now
        })
    log.info('更新标准代码表成功, tid: {}', tid)
} else {
    // 新增数据，生成新的 tid
    tid = IdUtil.getSnowflakeNextIdStr()
    // 从 MySQL 业务表读取当前有效数据。
    db.table('da_metadata_code_t')
        .primary('tid')
        .insert({
            tid: tid,
            dict_name: dictName,
            dict_code: dictCode,
            data_category_id: dataCategoryId,
            data_category_name: dataCategoryName,
            data_category_path: dataCategoryPath,
            description: describe,
            dict_item_value: dictItemValue,
            code_set: codeSet,
            code_value: codeValue,
            code_name: codeName,
            parent_code: parentCode,
            sort_no: sortNo,
            status: status,
            created_by: createdBy,
            created_time: now,
            updated_by: updatedBy,
            updated_time: now,
            is_del: 0
        })
    log.info('新增标准代码表成功, tid: {}', tid)
}

//更新数据到 redis
var nifi_dict_prefix = configData().nifi_dict_prefix
var jsonList = jsons.toJSONArray(dictItemValue)
if(jsonList != null){
    if(jsonList == []){
        redis.set(nifi_dict_prefix + tid,[])
    }else {
        var list = []
        for(item in jsonList){
            list.add({
                 `${item.code}` :  `${item.name}`         
            })
        }
        redis.set(nifi_dict_prefix + tid, list)
    }
}


// ========== 查询保存后的完整数据 ==========

var resultData = db.table('da_metadata_code_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()

// ========== 返回结果 ==========
return resultData

{
  "properties": {},
  "id": "11e28c173e4245b2b333a87649679aed",
  "script": null,
  "groupId": "55c792d1ba0f4cb79b01a6d5c42dacea",
  "name": "03.数据元保存修改",
  "createTime": 1782955344977,
  "updateTime": 1786716711115,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/metaSaveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"ae1af8482a724d5e855649a9ebb1aead\",\n    \"metaName\": \"数据元名称\",\n    \"metaCode\": \"data_element_code\",\n    \"standardEncode\": \"标准编码\",\n    \"bizDef\": \"业务定义\",\n    \"dataTypeId\": \"数据类型ID\",\n    \"valueDomainId\": \"值域ID\",\n    \"isNullable\": 0,\n    \"dataLevel\": \"数据分级\",\n    \"dataCategoryId\": \"数据分类ID\",\n    \"dataCategoryName\": \"数据分类名称\",\n    \"dataCategoryPath\": \"数据分类路径\",\n    \"publishStatus\": 0,\n    \"createdBy\": \"创建人\",\n    \"createdTime\": \"2026-03-09 14:56:46\",\n    \"updatedBy\": \"更新人\",\n    \"updatedTime\": \"2026-03-09 14:56:46\",\n    \"isDel\": 0\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行数据元保存修改，返回保存或配置结果；使用请求参数 tid、metaName、metaCode、standardEncode、bizDef、dataTypeId、fieldType、fieldLength；涉及 da_metadata_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.数据元保存修改
// 路由：POST /dwm/standard/element/metaSaveOrUpdate
// 业务说明：校验请求资料后执行数据元保存修改，返回保存或配置结果；使用请求参数 tid、metaName、metaCode、standardEncode、bizDef、dataTypeId、fieldType、fieldLength；涉及 da_metadata_t 等业务表。
import cn.hutool.core.util.IdUtil
import log

/**
 * 保存或更新数据元管理
 * POST /api/gov/data/meta/saveOrUpdate
 * 
 * 如果 body.tid 有值则更新，否则新增
 * 数据保存到 da_metadata_t 表
 */

// 获取请求参数
var tid = body?.tid
var metaName = body?.metaName
var metaCode = body?.metaCode
var standardEncode = body?.standardEncode
var bizDef = body?.bizDef
var dataTypeId = body?.dataTypeId
var fieldType = body?.fieldType
var fieldLength = body?.fieldLength
var numericPrecision = body?.numericPrecision
var numericScale = body?.numericScale
var formatPattern = body?.formatPattern
var exampleValue = body?.exampleValue
var standardCodeSet = body?.standardCodeSet
var valueDomainId = body?.valueDomainId
var isNullable = body?.isNullable
var dataLevel = body?.dataLevel
var dataCategoryId = body?.dataCategoryId
var dataCategoryName = body?.dataCategoryName
var dataCategoryPath = body?.dataCategoryPath
var publishStatus = body?.publishStatus
var createdBy = body?.createdBy
var updatedBy = body?.updatedBy
var customRule = body?.customRule
var qualityRule = body?.qualityRule

var isUpdate = tid != null && tid != ''

if (fieldType == null || fieldType == '') {
    exit 400, 'fieldType is required'
}

if (fieldLength == null || fieldLength::long(0) < 1) {
    exit 400, 'fieldLength must be greater than zero'
}

if (fieldType == 'decimal' && numericPrecision != null && numericScale != null && numericScale::long(0) > numericPrecision::long(0)) {
    exit 400, 'numericScale cannot exceed numericPrecision'
}

// 当前时间
var now = new Date()

// ========== 保存数据 da_metadata_t ==========
if (isUpdate) {
    // 更新数据
    db.table('da_metadata_t')
        .primary('tid')
        .update({
            tid: tid,
            meta_name: metaName,
            meta_code: metaCode,
            standard_encode: standardEncode,
            biz_def: bizDef,
            data_type_id: dataTypeId,
            field_type: fieldType,
            field_length: fieldLength,
            numeric_precision: numericPrecision,
            numeric_scale: numericScale,
            format_pattern: formatPattern,
            example_value: exampleValue,
            standard_code_set: standardCodeSet,
            value_domain_id: valueDomainId,
            is_nullable: isNullable,
            data_level: dataLevel,
            data_category_id: dataCategoryId,
            data_category_name: dataCategoryName,
            data_category_path: dataCategoryPath,
            publish_status: publishStatus,
            custom_rule: customRule,
            quality_rule: qualityRule,
            updated_by: updatedBy,
            updated_time: now
        })
    log.info('更新数据元管理成功, tid: {}', tid)
} else {
    // 新增数据，生成新的 tid
    tid = IdUtil.getSnowflakeNextIdStr()
    // 从 MySQL 业务表读取当前有效数据。
    db.table('da_metadata_t')
        .primary('tid')
        .insert({
            tid: tid,
            meta_name: metaName,
            meta_code: metaCode,
            standard_encode: standardEncode,
            biz_def: bizDef,
            data_type_id: dataTypeId,
            field_type: fieldType,
            field_length: fieldLength,
            numeric_precision: numericPrecision,
            numeric_scale: numericScale,
            format_pattern: formatPattern,
            example_value: exampleValue,
            standard_code_set: standardCodeSet,
            value_domain_id: valueDomainId,
            is_nullable: isNullable,
            data_level: dataLevel,
            data_category_id: dataCategoryId,
            data_category_name: dataCategoryName,
            data_category_path: dataCategoryPath,
            publish_status: publishStatus || 0, // 默认为待发布
            custom_rule: customRule,
            quality_rule: qualityRule,
            created_by: createdBy,
            created_time: now,
            updated_by: updatedBy,
            updated_time: now,
            is_del: 0
        })
    log.info('新增数据元管理成功, tid: {}', tid)
}

// ========== 查询保存后的完整数据 ==========

var resultData = db.table('da_metadata_t')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .selectOne()


// 返回当前业务分支的标准结果。
return resultData

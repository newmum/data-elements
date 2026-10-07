{
  "properties": {},
  "id": "9229d9818ec74ecda4b17fbc66d2ed5d",
  "script": null,
  "groupId": "27defcc7cb47425eb18cd62f8bf153b0",
  "name": "01.标准代码列表",
  "createTime": 1782955345026,
  "updateTime": 1786717069782,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"categoryPath\": \"24d89c5eca944680bae13605f074f17e\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询标准代码表所有数据（不分页）；使用请求参数 dictName、dictCode、dataCategoryId、categoryPath；涉及 da_metadata_code_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.标准代码列表
// 路由：POST /dwm/standard/code/list
// 业务说明：查询标准代码表所有数据（不分页）；使用请求参数 dictName、dictCode、dataCategoryId、categoryPath；涉及 da_metadata_code_t 等业务表。
// 核心业务逻辑：按接口约定编排当前请求并返回业务处理结果。
import log
import jsons

/**
 * 查询标准代码表列表
 * POST /api/gov/standard/code/list
 * 
 * 查询标准代码表所有数据（不分页）
 * 支持按字典名称、字典编码、数据分类ID、发布状态等条件过滤
 */

// 获取查询条件
var dictName = body?.dictName
var dictCode = body?.dictCode
var dataCategoryId = body?.dataCategoryId
var categoryPath = body?.categoryPath

// 构建查询条件
var query = db.table('da_metadata_code_t')
    .where()
    .eq('is_del',0)
    .eq('code_value', '__HEADER__')

if (dictName != null && dictName != '') {
    query = query.like('dict_name', '%' + dictName + '%')
}

if (dictCode != null && dictCode != '') {
    query = query.like('dict_code', '%' + dictCode + '%')
}

if (dataCategoryId != null && dataCategoryId != '') {
    query = query.eq('data_category_id', dataCategoryId)
}

if (categoryPath != null && categoryPath != '') {
    query = query.like('data_category_path', categoryPath + '%')
}


// ========== 查询列表 ==========
var list = query
    .orderBy('created_time', 'desc')
    .select()

log.info('查询标准代码表列表成功, 共查询到 {} 条记录', list.length)

// ========== 返回结果 ==========
return list

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
  "description": "读取当前登录租户的有效标准代码集，供值域与字段登记选择。可按名称、编码、分类 ID 或分类路径筛选，按创建时间降序返回完整数组。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// POST /dwm/standard/code/list 提供当前租户的标准代码集候选，供值域和字段登记使用。
// dictName、dictCode 支持模糊匹配，dataCategoryId 精确匹配，categoryPath 匹配路径前缀。
// 返回按创建时间降序排列的完整数组，无匹配时返回空数组。

var tenantId = tenantRuntime.id()
var dictName = body?.dictName
var dictCode = body?.dictCode
var dataCategoryId = body?.dataCategoryId
var categoryPath = body?.categoryPath

// __HEADER__ 标识承载整组字典值的代码集记录，租户和删除条件在查询时同时应用。
var query = db.table('da_metadata_code_t')
    .where()
    .eq('tenant_id', tenantId)
    .eq('is_del', 0)
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

return query
    .orderBy('created_time', 'desc')
    .select()

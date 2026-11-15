{
  "properties": {},
  "id": "resource_layer_page_01",
  "script": null,
  "groupId": "46fe687ba7304a608b5e2865725ad21c",
  "name": "05.分层数据表公共分页",
  "createTime": 1784454007655,
  "updateTime": 1784454007655,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/layer-page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"conditions\": [\n    ],\n    \"layerRoot\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "根据 layerRoot 选择 ODS、DWD 或 DWS 分层，复用登记表分页接口筛选当前租户数据，并补齐页面所需目录字段。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.分层数据表公共分页
// 路由：POST /dwm/center/layer-page
// 业务说明：根据 layerRoot 选择 ODS、DWD 或 DWS 分层，复用登记表分页接口筛选当前租户数据，并补齐页面所需目录字段。
import '@post:/dst/database/table/page' as tablePage

/*
 * 资源中心分层列表公共实现。
 *
 * 调用方通过 body.layerRoot 指定 ods/dwd/dws；页面传入子层 dataSourceType 时按子层
 * 过滤。结果以 db_table_t 为准，并补齐旧列表组件需要的目录兼容字段。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
// 解析请求体并为可选参数设置默认值。
var root = body == null ? null : body.layerRoot
var conditions = body == null || body.conditions == null ? [] : body.conditions
var hasLayerCondition = conditions.find(item => item != null && item.field == 'dataSourceType') != null
if (!hasLayerCondition && !blank(root)) {
    conditions.add({ field: 'dataSourceType', value: root, type: 'match' })
}
var oldBody = body
body = {
    ...(oldBody == null ? {} : oldBody),
    conditions: conditions,
    pageNum: oldBody == null || oldBody.pageNum == null ? 1 : oldBody.pageNum,
    pageSize: oldBody == null || oldBody.pageSize == null ? 20 : oldBody.pageSize
}
var result = tablePage()
body = oldBody
var list = []
for (item in result == null || result.list == null ? [] : result.list) {
    list.add({
        ...item,
        catalogName: blank(item.catalogName) ? item.tableNameCn : item.catalogName,
        catalogNameEn: blank(item.catalogNameEn) ? item.tableName : item.catalogNameEn,
        sourceTableId: item.tid,
        targetTableId: item.tid,
        targetTable: item,
        sourceDir: item.tableNameCn,
        sourceCatalogName: item.tableNameCn,
        processLogic: '',
        status: '1'
    })
}
// 返回当前业务分支的标准结果。
return {
    pageNum: result == null ? 1 : result.pageNum,
    pageSize: result == null ? 20 : result.pageSize,
    list: list,
    total: result == null ? 0 : result.total
}

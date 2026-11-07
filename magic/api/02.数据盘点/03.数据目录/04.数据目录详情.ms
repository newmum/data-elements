{
  "properties": {},
  "id": "datapd_catalog_detail_compat_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "04.数据目录详情",
  "createTime": null,
  "updateTime": 1787153759311,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2086383506325794816\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"tid\": \"2086383506325794816\",\n  \"catalogName\": \"公安案件登记业务表\"\n}",
  "description": "按当前租户和数据目录管理范围读取源头目录及扩展属性。支持 tid 或兼容参数 id，保留历史详情字段。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/resource-center/catalog-read' as readCatalog

/**
 * 数据目录详情 POST /dst/catalog/detail
 * 按当前租户和盘点数据范围读取一条目录及其扩展属性。
 * 请求优先使用 tid，也兼容 id。返回原有详情字段和 props。
 */

var text = value => value == null ? '' : value + ''
var blank = value => text(value).trim() == ''

// 统一旧调用方的两个标识字段，具体的数据范围检查由读取函数执行。
var tid = body == null ? null : (blank(body.tid) ? body.id : body.tid)
if (blank(tid)) {
    exit 400, '目录标识不能为空'
}
return readCatalog({
    mode: 'directory-detail',
    id: text(tid)
})

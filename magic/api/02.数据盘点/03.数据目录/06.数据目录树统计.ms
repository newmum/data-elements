{
  "properties": {},
  "id": "datapd_catalog_tree_stats_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "06.数据目录树统计",
  "createTime": null,
  "updateTime": 1790484211140,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/treeStatistics",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"orgIds\":[]}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"total\":0,\"orgCounts\":{},\"orgApps\":{}}",
  "description": "数据目录树统计。以 da_catalog_t 已完成目录为唯一真值，返回组织、业务系统和数据源下的目录数量；使用请求参数 conditions、orgIds、keyword。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.数据目录树统计
// 路由：POST /dst/catalog/treeStatistics
// 业务说明：数据目录树统计。以 da_catalog_t 已完成目录为唯一真值，返回组织、业务系统和数据源下的目录数量；使用请求参数 conditions、orgIds、keyword。
import '@post:/dst/catalog/page' as catalogPage
import java.util.ArrayList

/* 只读目录统计：与目录分页共用同一份租户范围、动态属性及筛选逻辑。 */
var conditions = body == null || body.conditions == null ? [] : new ArrayList(body.conditions)
if (body != null && body.orgIds != null && body.orgIds.size() > 0) {
  conditions.add({field:'orgIds',value:body.orgIds,type:'in'})
}
body = {statisticsOnly:true,conditions:conditions,keyword:body == null ? '' : body.keyword}
return catalogPage()

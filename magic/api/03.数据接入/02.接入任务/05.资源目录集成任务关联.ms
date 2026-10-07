{
  "properties": {},
  "id": "3ba19eea403c45208c9fb14fdf9a77fe",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "05.资源目录集成任务关联",
  "createTime": 1782955344820,
  "updateTime": 1784437771823,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "catalogTaskRela",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"catalogId\": \"85368b91972c4a35b83b8fa73e34e740\",\n    \"processGroupId\": \"248b7b66-019e-1000-cf70-679477bce2e1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"success\"\n}",
  "description": "清理资源目录与接入任务的关联；使用请求参数 catalogId。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.资源目录集成任务关联
// 路由：POST /ods/task/catalogTaskRela
// 业务说明：清理资源目录与接入任务的关联；使用请求参数 catalogId。
/*
 * 清理资源目录与接入任务的关联
 *
 * 该接口只清空目录动态属性 accessTaskId，不删除接入任务。
 * 目录属性保存接口同时负责更新数据库和 ES 投影。
 */
import '@post:/dst/catalog/properties/save' as saveCatalogProperties

// 解析请求体并为可选参数设置默认值。
var catalogId = body?.catalogId
if (catalogId == null || catalogId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'catalogId不能为空'
}

body = {
    tid: catalogId,
    props: { accessTaskId: null }
}
saveCatalogProperties()
// 返回当前业务分支的标准结果。
return 'success'

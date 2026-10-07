{
  "properties": {},
  "id": "39a9aaf9bc224581ac38baa0bd8066b9",
  "script": null,
  "groupId": "8f2a6564b6fd43c28ff7a5e35d4d7cd2",
  "name": "01.关联页面绑定(新增调用)",
  "createTime": 1782955345267,
  "updateTime": 1784437771878,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/bind",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pid\": \"页签id\",\n    \"tid\": \"子页面id\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验请求资料后执行关联页面绑定(新增调用)，返回保存或配置结果；使用请求参数 pid、tid；涉及 app_workshop_repository_tab 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.关联页面绑定(新增调用)
// 路由：POST /das/repository/tab/bind
// 业务说明：校验请求资料后执行关联页面绑定(新增调用)，返回保存或配置结果；使用请求参数 pid、tid；涉及 app_workshop_repository_tab 等业务表。
// 解析请求体并为可选参数设置默认值。
var tid = body?.pid
var relaId = body?.tid



// 从 MySQL 业务表读取当前有效数据。
db.table('app_workshop_repository_tab')
    .where()
    .eq('tid', tid)
    .eq('is_del', 0)
    .update({
      rela_id:relaId
    })

// 返回当前业务分支的标准结果。
return "success"

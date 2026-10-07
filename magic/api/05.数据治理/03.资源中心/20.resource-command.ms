{
  "properties" : { },
  "id" : "b6aaff9e31925977b94369d347e7c32a",
  "script" : null,
  "groupId" : "46fe687ba7304a608b5e2865725ad21c",
  "name" : "20.resource-command",
  "createTime" : null,
  "updateTime" : 1790982798990,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/resource-command",
  "method" : "POST",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "磐石资源中心受控业务命令",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
// 02.资源中心业务命令入口。路由白名单、租户会话与乐观版本统一校验。
import tenantRuntime
import '@/resource-center/planning-command' as planningCommand
import '@/resource-center/model-command' as modelCommand
import '@/resource-center/materialization-command' as materializationCommand
import '@/resource-center/resource-command' as resourceCommand
import '@/resource-center/catalog-command' as catalogCommand
var tenantId = tenantRuntime.id()
if (body == null || body.kind == null) { exit 400,'缺少资源操作类型' }
var command = body
if (command.tenantId != null) { exit 400,'业务租户由当前会话确定' }
if (!['GET','POST','PUT','DELETE'].contains(command.method)) { exit 400,'不支持的操作方法' }
if (command.body == null) { command.body = {} }
if (command.body.tenantId != null) { exit 400,'不能通过请求切换业务租户' }
var kind = command.kind + ''
if (['layers','databases','bindings','classifications'].contains(kind)) { return planningCommand(command) }
if (['models','itemsets'].contains(kind)) { return modelCommand(command) }
if (kind == 'materializations') { return materializationCommand(command) }
if (kind == 'resources') { return resourceCommand(command) }
if (['catalogs','publications','subscriptions','deliveries','policy'].contains(kind)) { return catalogCommand(command) }
exit 400,'未知资源业务操作'

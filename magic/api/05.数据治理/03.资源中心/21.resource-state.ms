{
  "properties" : { },
  "id" : "f32458ca37a55794b4cb49cfb191ca76",
  "script" : null,
  "groupId" : "46fe687ba7304a608b5e2865725ad21c",
  "name" : "21.resource-state",
  "createTime" : null,
  "updateTime" : 1791068229114,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/resource-state",
  "method" : "GET",
  "parameters" : [ ],
  "options" : [ ],
  "requestBody" : "",
  "headers" : [ ],
  "paths" : [ ],
  "responseBody" : "",
  "description" : "磐石资源中心跨模块状态查询",
  "requestBodyDefinition" : null,
  "responseBodyDefinition" : null
}
================================
import tenantRuntime
import '@/resource-center/technical-read' as technicalRead
import '@/resource-center/planning-read' as planningRead
import '@/resource-center/model-read' as modelRead
import '@/resource-center/catalog-read' as catalogRead

/**
 * 磐石资源中心工作台。GET /dwm/center/resource-state
 * 查询参数 pageNo、pageSize、keyword 用于技术实体分页。返回技术实体、规划、模型及目录工作台状态。
 * 只读。子函数共享当前认证租户，目录和数据源规划按各自角色范围核验。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
// 当前资源未配置参数包装。Magic HTTP 入口注入同名变量，内部调用可传包装后的查询对象。
var requestQuery = query == null ? param : query
var technical = technicalRead({
    pageNo: pageNo == null ? requestQuery?.pageNo : pageNo,
    pageSize: pageSize == null ? requestQuery?.pageSize : pageSize,
    entityKeyword: keyword == null ? requestQuery?.keyword : keyword
})
var planning = planningRead({})
var models = modelRead({entities: technical.entities})
var catalog = catalogRead({})
var state = {schemaVersion: 1, ignoredMatches: [], technicalRevisions: [], autoApprove: false, busConfigured: false}
state.putAll(planning)
state.putAll(models)
for (key in ['catalogs', 'publications', 'subscriptions', 'deliveries', 'autoApprove', 'busConfigured', 'deliveryScheduling']) {
    state[key] = catalog[key]
}
technical.state = state
technical.capabilities = {
    write: true, review: true, manage: true, execute: true,
    scheduledDelivery: false, manualDelivery: true, crossNetworkSync: false
}
technical.actor = catalog.actor
return technical

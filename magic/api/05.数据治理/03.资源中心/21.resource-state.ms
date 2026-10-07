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
// 01.资源中心工作台聚合查询；所有子函数共享认证会话与租户，不装载演示数据。
import tenantRuntime
import '@/resource-center/technical-read' as technicalRead
import '@/resource-center/planning-read' as planningRead
import '@/resource-center/model-read' as modelRead
import '@/resource-center/catalog-read' as catalogRead
var tenantId = tenantRuntime.id()
var technical = technicalRead({pageNo:param?.pageNo,pageSize:param?.pageSize,entityKeyword:param?.keyword})
var planning = planningRead({})
var models = modelRead({entities:technical.entities})
var catalog = catalogRead({})
var state = {schemaVersion:1,ignoredMatches:[],technicalRevisions:[],autoApprove:false,busConfigured:false}
state.putAll(planning); state.putAll(models)
for (key in ['catalogs','publications','subscriptions','deliveries','autoApprove','busConfigured','deliveryScheduling']) { state[key] = catalog[key] }
technical.state = state
technical.capabilities = {write:true,review:true,manage:true,execute:true,scheduledDelivery:false,manualDelivery:true,crossNetworkSync:false}
technical.actor = catalog.actor
return technical

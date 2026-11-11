{
  "id": "ad50e98d5f5e40ed861e767c0426d770",
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "06.打开服务编排",
  "path": "/orchestration/open",
  "method": "POST",
  "properties": {},
  "parameters": [],
  "description": "用户点击编排时，按当前租户和角色范围锁定服务，复用现有流程，或为尚未关联流程的服务初始化草稿。dryRun=true只预检。"
}
================================
// POST /dws/flowserve/gateway/orchestration/open
// 仅用户主动打开时初始化；列表不补查、不创建流程。既有流程或损坏关联不被覆盖。
import tenantRuntime
import dataScope
import cn.hutool.json.JSONUtil

var tenantId = tenantRuntime.id()
var apiId = (body?.apiId == null ? '' : body.apiId + '').trim()
if (apiId == '' || apiId.length() > 64) { exit 400, '请选择有效的数据服务' }
var scope = dataScope.dataSource()
var userId = scope.userId
var orgIds = scope.organizationIds
var scopeSql = ''
if (scope.scope == 'OWNER') { scopeSql = ' AND created_by=#{userId}' }
else if (scope.scope == 'ORG') {
    if (orgIds == null || orgIds.size() == 0) { exit 404, '服务不存在或不在当前数据范围内' }
    if (orgIds.size() > 1000) { exit 403, '部门数据范围超出上限，请联系维护人员' }
    scopeSql = ' AND org_id IN (#{orgIds})'
} else if (scope.scope != 'ALL') { exit 403, '当前角色的数据范围无效' }
var result = {flowId: '', created: false, dryRun: body?.dryRun == true}
db.transaction(() => {
    // 服务行锁串行化首次创建；参数中的租户、部门或流程ID均不作为授权依据。
    var services = db.select("""
        SELECT tid, flow_id AS "flowId", service_name AS "serviceName", method, uri, service_create_type
          FROM api_info_t
         WHERE tid=#{apiId} AND tenant_id=#{tenantId} AND is_del=0
        """ + scopeSql + ' FOR UPDATE')
    if (services == null || services.size() == 0) { exit 404, '服务不存在或不在当前数据范围内' }
    var service = services[0]
    var linkedFlow = service.flowId || service.flowid || service.flow_id || ''
    var serviceName = service.serviceName || service.servicename || service.service_name || ''
    var linked = (linkedFlow + '').trim() != ''
    var flowId = linked ? linkedFlow + '' : apiId
    var flows = db.select("""
        SELECT id, deleted FROM fs_flow WHERE id=#{flowId} AND tenant_id=#{tenantId}
    """)
    if (flows != null && flows.size() > 0) {
        if (flows[0].deleted != 0) { exit 409, '关联流程已移除，请检查服务的流程关联' }
        result.flowId = flowId
        return
    }
    if (linked) { exit 409, '已关联的流程不存在，请检查服务的流程关联' }
    result.flowId = flowId
    result.requiresInitialization = true
    if (result.dryRun) { return }
    var method = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].contains(service.method) ? service.method : 'GET'
    var triggerId = 'trigger-' + flowId
    var responseId = 'response-' + flowId
    var flow = {
        flowId: flowId, name: serviceName, description: '', version: 1, group: '默认分组',
        nodes: [
            {id: triggerId, type: 'trigger', name: 'API 触发器', position: {x: 80, y: 200},
             data: {method: method, path: '/services/' + apiId, request: {query: [], path: [], header: [], body: null}, auth: {required: true, roles: []}}},
            {id: responseId, type: 'response', name: '响应', position: {x: 520, y: 200},
             data: {mode: 'wrap', wrap: true, source: '', statusCode: 200}}
        ],
        edges: [{id: 'edge-' + flowId, source: triggerId, target: responseId, sourcePort: 'out', targetPort: 'in'}],
        settings: {timeoutMs: 10000, transactional: false, errorStrategy: 'fail-fast'}
    }
    if ((service.service_create_type || service.serviceCreateType) != 'flow' && service.uri != null && service.uri != '') {
        var callId = 'http-' + flowId
        flow.nodes.add({id:callId,type:'http-call',name:'调用已登记接口',position:{x:300,y:200},
            data:{serviceId:apiId,outputVar:'httpResult',headers:[],query:[],body:''}})
        flow.edges = [{id:'edge-call-'+flowId,source:triggerId,target:callId,sourcePort:'out',targetPort:'in'},
            {id:'edge-response-'+flowId,source:callId,target:responseId,sourcePort:'out',targetPort:'in'}]
    }
    var now = new Date()
    db.table('fs_flow').primary('id').insert({id: flowId, name: serviceName, flow_group: '默认分组',
        status: 'draft', current_version: 1, dsl: JSONUtil.toJsonStr(flow), updated_by: userId,
        updated_at: now, lock_version: 1, deleted: 0, tenant_id: tenantId})
    db.table('api_info_t').where().eq('tid', apiId).eq('tenant_id', tenantId).eq('is_del', 0)
        .update({flow_id: flowId})
    result.created = true
})
return result

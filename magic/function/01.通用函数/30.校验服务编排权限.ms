{
  "properties": {},
  "id": "0f61399f236e4c2693bdf441cd40f359",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "30.校验服务编排权限",
  "path": "/serviceFlowContext",
  "description": "核验当前会话、租户及编排关联服务的数据范围。",
  "parameters": [{"name":"flowId","required":true,"dataType":"String","type":"java.lang.String"}],
  "options": []
}
================================
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime
import dataScope

// 功能：校验编排与发布入口的会话及关联服务。
var tenantId = tenantRuntime.id()
if (!StpUtil.isLogin()) { exit 401,'请先登录后调用服务' }
var sessionTenant = (StpUtil.getTokenSession().get('tenantId') || '') + ''
if (sessionTenant != tenantId) { exit 403,'当前会话与服务所属租户不一致' }
if (flowId == null || !flowId.matches('[A-Za-z0-9_-]{1,64}')) { exit 400,'流程标识无效' }
var servicePage = db.table('api_info_t').where().eq('tenant_id',tenantId).eq('flow_id',flowId).eq('is_del',0).page(2,0)
var services = servicePage.list || []
if (services.size() != 1 || !dataScope.canReadDataSource(services[0])) { exit 404,'流程不存在或不在当前数据范围内' }
var flow = db.table('fs_flow').where().eq('tenant_id',tenantId).eq('id',flowId).eq('deleted',0).selectOne()
if (flow == null) { exit 404,'流程不存在或不在当前数据范围内' }
return {tenantId:tenantId,userId:StpUtil.getLoginIdAsString(),service:services[0],flow:flow}

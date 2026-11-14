{
  "properties": {},
  "id": "7c34d5fce50f48a8a8eac7da11f609",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "29.保存与发布服务编排",
  "path": "/serviceFlowManage",
  "description": "按租户及服务范围保存、调试和发布HTTP编排。发布由服务端生成受控入口。",
  "parameters": [{"name":"options","required":true,"dataType":"Object","type":"java.lang.Object"}],
  "options": []
}
================================
import '@/common/serviceFlowContext' as contextOf
import '@/common/serviceFlowHttp' as execute
import flowRuntime
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import java.util.Map

// 功能：维护服务关联的HTTP编排草稿与发布版本。
var text = value => value == null ? '' : value + ''
var flowId = text(options.flowId)
var ctx = contextOf(flowId)
var tenantId = ctx.tenantId
var saved = ctx.flow
var body = options.body || {}
if (!(body instanceof Map)) { exit 400,'流程请求须为对象' }
var lockVersion = saved.lockVersion || saved.lock_version
var currentVersion = saved.currentVersion || saved.current_version
var action = options.action
if (action == 'invoke') {
    var record = db.table('fs_publish_record').where().eq('tenant_id',tenantId).eq('flow_id',flowId)
        .eq('version',currentVersion).eq('status','published').selectOne()
    if (saved.status != 'published' || record == null) { exit 404,'服务尚未发布或已下线' }
    var version = db.table('fs_flow_version').where().eq('tenant_id',tenantId).eq('flow_id',flowId)
        .eq('version',currentVersion).eq('status','published').selectOne()
    if (version == null) { exit 404,'服务发布版本不可用' }
    var result = execute({flow:JSONUtil.parseObj(version.dsl),input:body})
    if (!result.success) { exit 502,result.error }
    return result.response
}
if (action == 'versions') {
    var versionPage = db.table('fs_flow_version').where().eq('tenant_id',tenantId).eq('flow_id',flowId)
        .orderBy('version','desc').page(50,0)
    var versions = []
    for (row in versionPage.list || []) {
        versions.add({version:row.version,dsl:JSONUtil.parseObj(row.dsl),script:row.script,
            meta:{},publishTime:row.publishTime || row.publish_time,remark:row.remark,status:row.status})
    }
    return versions
}
if (action == 'offline') {
    db.transaction(()=>{
        var changed = db.table('fs_flow').where().eq('tenant_id',tenantId).eq('id',flowId)
            .eq('lock_version',lockVersion).eq('deleted',0)
            .update({status:'offline',lock_version:lockVersion+1,updated_by:ctx.userId,updated_at:new Date()})
        if (changed != 1) { exit 409,'流程已变化，请刷新后重试' }
        db.table('fs_publish_record').where().eq('tenant_id',tenantId).eq('flow_id',flowId)
            .eq('status','published').update({status:'offline'})
        db.table('api_info_t').where().eq('tenant_id',tenantId).eq('tid',ctx.service.tid)
            .eq('is_del',0).update({status:'offline',updated_by:ctx.userId,updated_time:new Date()})
    })
    return {status:'offline'}
}
var flow = ['save','debug'].contains(action) ? body.flow : JSONUtil.parseObj(saved.dsl)
if (!(flow instanceof Map) || text(flow.flowId) != flowId) { exit 400,'流程标识与请求不匹配' }
if (action == 'debug') { return execute({flow:flow,input:body.input || {}}) }
var plan = execute({flow:flow,validateOnly:true})
var normalized = JSONUtil.toJsonStr(flow)
if (action == 'save') {
    if (body.lockVersion != lockVersion) { exit 409,'流程已被修改，请刷新后再保存' }
    var changed = db.table('fs_flow').where().eq('tenant_id',tenantId).eq('id',flowId)
        .eq('lock_version',lockVersion).eq('deleted',0)
        .update({dsl:normalized,lock_version:lockVersion+1,updated_by:ctx.userId,updated_at:new Date()})
    if (changed != 1) { exit 409,'流程已被修改，请刷新后再保存' }
    return {lockVersion:lockVersion+1}
}
if (action != 'publish') { exit 400,'流程操作无效' }
if (body.lockVersion != lockVersion) { exit 409,'草稿版本已变化，请重新读取后发布' }
var remark = text(body.remark)
if (remark.length() > 500) { exit 400,'发布说明最多500个字符' }
var nodeId = text(body.targetNodeId || 'local_magic_api')
var node = db.table('fs_runtime_node').where().eq('tenant_id',tenantId).eq('id',nodeId).eq('enabled',1).selectOne()
if (nodeId != 'local_magic_api' && (node == null || (node.localNode || node.local_node) != 1)) {
    exit 400,'当前HTTP编排支持发布到本机Magic运行节点'
}
// 由服务端固定生成入口，草稿图和客户端脚本都不能改变认证边界。
var route = '/services/'+ctx.service.tid
var entry = {flowId:flowId,name:'HTTP服务_'+ctx.service.tid,description:'HTTP服务编排发布入口',
    nodes:[{type:'trigger',data:{method:plan.method,path:route,auth:{required:true}}}]}
var script = "import '@/common/serviceFlowManage' as manage\n"
    + "return manage({action:'invoke',flowId:'"+flowId+"',body:body || parameter || {}})\n"
var published = null
db.transaction(()=>{
    // 使用版本号原子占有发布操作。部署失败时回滚租户业务记录。
    var changed = db.table('fs_flow').where().eq('tenant_id',tenantId).eq('id',flowId)
        .eq('lock_version',lockVersion).eq('deleted',0).update({lock_version:lockVersion+1})
    if (changed != 1) { exit 409,'草稿版本已变化，请重新读取后发布' }
    var existing = db.table('fs_flow_version').where().eq('tenant_id',tenantId).eq('flow_id',flowId)
        .eq('version',currentVersion).selectOne()
    var version = existing == null ? currentVersion : currentVersion+1
    var previous = db.table('fs_publish_record').where().eq('tenant_id',tenantId).eq('flow_id',flowId)
        .eq('version',currentVersion).selectOne()
    // 同一服务已使用的入口方法保持稳定，已有调用方可继续访问原地址。
    if (previous != null && existing != null) {
        var oldMeta = JSONUtil.parseObj(existing.meta || '{}')
        if (oldMeta.method != plan.method) { exit 409,'已发布服务的入口方法必须保持一致' }
    }
    var deployed = flowRuntime.deploy(flowId,entry,script,{})
    var now = new Date()
    db.table('fs_flow_version').primary('id').insert({id:IdUtil.getSnowflakeNextId(),tenant_id:tenantId,flow_id:flowId,
        version:version,dsl:normalized,script:script,meta:JSONUtil.toJsonStr({method:plan.method}),
        publisher:ctx.userId,remark:remark,publish_time:now,status:'published'})
    db.table('fs_publish_record').where().eq('tenant_id',tenantId).eq('flow_id',flowId).eq('status','published').update({status:'offline'})
    db.table('fs_publish_record').primary('id').insert({id:IdUtil.getSnowflakeNextId(),tenant_id:tenantId,flow_id:flowId,
        version:version,runtime_node_id:deployed.runtimeNodeId,magic_resource_id:deployed.resourceId,
        url:deployed.url,publish_time:now,status:'published'})
    db.table('fs_flow').where().eq('tenant_id',tenantId).eq('id',flowId).eq('deleted',0)
        .update({status:'published',current_version:version,updated_by:ctx.userId,updated_at:now})
    db.table('api_info_t').where().eq('tenant_id',tenantId).eq('tid',ctx.service.tid).eq('is_del',0)
        .update({status:'published',publish_address:deployed.path,publish_address_full:deployed.url,
            publish_config:JSONUtil.toJsonStr({publishAddress:deployed.path,publishMethod:plan.method,generatedBy:'flowserve'}),
            updated_by:ctx.userId,updated_time:now})
    published = {version:version,url:deployed.url,status:'published',runtimeNodeId:deployed.runtimeNodeId,runtimeNodeName:'本机 Magic API'}
})
return published

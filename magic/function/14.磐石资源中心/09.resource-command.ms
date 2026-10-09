{
  "properties" : { },
  "id" : "4049d07b6bcc53f2b88f544ff0f62d22",
  "script" : null,
  "groupId" : "8b36eff777cc5cc6a8cf7c3048cd8b82",
  "name" : "09.resource-command",
  "createTime" : null,
  "updateTime" : 1790982798850,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/resource-command",
  "description" : null,
  "returnType" : null,
  "mappingPath" : null,
  "parameters" : [ {
    "name" : "input",
    "value" : null,
    "description" : null,
    "required" : false,
    "dataType" : "Object",
    "type" : null,
    "defaultValue" : null,
    "validateType" : null,
    "error" : null,
    "expression" : null,
    "children" : null
  } ]
}
================================
// 07.共享库表的资源归属登记。直接维护 db_table_t，不复制技术实体、不创建物理表。
import tenantRuntime
import metadataAsset
import jsons
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import '@/resource-center/model-read' as readModels
import '@/resource-center/technical-read' as technicalRead
import '@/resource-center/planning-read' as readPlanning
import '@post:/dst/database/metadata/table/sample-data' as sampleData
var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var now = new Date()
var text = v => v == null ? '' : (v + '').trim()
var payload = input.body == null ? {} : input.body
var id = text(input.id); var action = text(input.action); var method = text(input.method)
var find = (rows,key) => { for (r in rows) { if (r.id == key) { return r } }; exit 404,'对象不存在或不属于当前租户' }
if (id == '' && method == 'POST') {
    id = text(payload.entityId)
    var table = db.selectOne('SELECT tid,datasource_id,table_name,table_name_cn,resource_domain_id,resource_state FROM db_table_t WHERE tenant_id=#{tenantId} AND tid=#{id} AND is_del=0')
    if (table == null) { exit 404,'技术实体不存在' }
    if (text(table.resourceDomainId) != '' && table.resourceState == 'ACTIVE') { exit 409,'该技术实体已有有效资源登记' }
    var domain = find(readPlanning({}).databases,text(payload.databaseId))
    if (domain.state != 'ACTIVE') { exit 409,'规划分库已归档' }
    var owner = text(payload.owner); if (owner == '' || owner.length() > 128) { exit 400,'请填写责任方，最多128字' }
    var name = text(payload.name) == '' ? table.tableNameCn : text(payload.name)
    if (name == null || text(name) == '') { name = table.tableName }
    if (text(name).length() > 512 || text(payload.domain).length() > 64) { exit 400,'名称或业务域超过长度限制' }
    var changed = db.update("UPDATE db_table_t SET resource_domain_id=#{domainId},resource_owner=#{owner},resource_state='ACTIVE',resource_revision_no=resource_revision_no+1,table_name_cn=#{name},business_type=#{domain},asset_desc=#{description},asset_status=2,flow_status=2,reg_time=#{now},updated_time=#{now},updated_by=#{actorId} WHERE tid=#{id} AND tenant_id=#{tenantId} AND is_del=0 AND (resource_domain_id IS NULL OR resource_state='ARCHIVED')",
        {domainId:domain.id,owner:owner,name:name,domain:text(payload.domain),description:text(payload.description),now:now,actorId:actorId,id:id,tenantId:tenantId})
    if (changed != 1) { exit 409,'该技术实体已由另一请求登记' }
    if (text(table.datasourceId) != '') { metadataAsset.refreshRegistrationStatus(table.datasourceId, false) }
    db.table('res_resource_operation_log').primary('tid').insert({tid:IdUtil.fastSimpleUUID(),tenant_id:tenantId,actor_id:actorId,action_name:'登记仓库资源',object_id:id,object_name:name,detail_text:'沿用共享技术实体，未复制数据表',created_time:now})
    return find(readModels({}).resources,id)
}
var resource = find(readModels({}).resources,id)
if (method == 'GET') {
    var technical = technicalRead({entityIds:[id]})
    var record = resource; record.source = find(technical.sources,resource.structure.sourceId)
    var allowed = []; var hidden = []; var masked = []
    for (f in db.select('SELECT column_name,is_masking FROM db_table_column_t WHERE tenant_id=#{tenantId} AND table_id=#{id} AND is_del=0 ORDER BY ordinal_position')) {
        var n = text(f.columnName)
        if (n.toLowerCase().matches('.*(password|secret|token|credential|private_key).*')) { hidden.add(n) }
        else { allowed.add(n); if (f.isMasking == 1 || n.toLowerCase().matches('.*(phone|mobile|email|identity|certificate_no).*')) { masked.add(n) } }
    }
    record.records = []
    if (allowed.size() > 0) {
        var previousBody = body
        try {
            body = {tableId:id,sampleSize:10,columns:allowed}
            var sample = sampleData()
            var rows = sample.data == null ? (sample.rows == null ? [] : sample.rows) : sample.data
            for (row in rows) { for (fieldName in masked) { if (row[fieldName] != null) { row[fieldName] = '***' } }; record.records.add(row) }
        } catch (e) { record.previewError = '读取数据预览失败：' + e.getMessage() }
        finally { body = previousBody }
    }
    return record
}
if (action != 'archive') { exit 400,'不支持的资源操作' }
if (text(input.version) != text(resource.version)) { exit 409,'资源已更新，请刷新重试' }
var linked = db.selectOne("SELECT p.tid FROM res_catalog_publish_request p JOIN da_catalog_t c ON p.catalog_id=c.tid AND c.tenant_id=p.tenant_id WHERE p.tenant_id=#{tenantId} AND p.is_del=0 AND c.is_del=0 AND c.source_table_id=#{id} AND p.request_status='PUBLISHED' LIMIT 1")
if (linked != null) { exit 409,'仍有已发布目录引用该资源，请先下线目录' }
var expected = resource.version
var changed = db.update("UPDATE db_table_t SET resource_state='ARCHIVED',resource_revision_no=resource_revision_no+1,updated_time=#{now},updated_by=#{actorId} WHERE tid=#{id} AND tenant_id=#{tenantId} AND is_del=0 AND resource_revision_no=#{expected}",{now:now,actorId:actorId,id:id,tenantId:tenantId,expected:expected})
if (changed != 1) { exit 409,'资源已更新，请刷新重试' }
return {id:id,state:'ARCHIVED',version:expected + 1}

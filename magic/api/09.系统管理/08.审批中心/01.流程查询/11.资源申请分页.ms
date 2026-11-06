{
  "properties": {},
  "id": "ce1844634b80491c8aeb9c7da6ebec7e",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "11.资源申请分页",
  "createTime": 1782955344554,
  "updateTime": 1784657246159,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applyPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"sortField\": \"createdTime\",\n    \"sortDir\": \"desc\",\n    \"conditions\": [\n        {\n            \"field\": \"flowStatus\",\n            \"value\": \"5\",\n            \"type\": \"match\"\n        },\n        {\n            \"field\": \"applyName\",\n            \"value\": \"测试\",\n            \"type\": \"like\"\n        }\n    ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户及申请人分页查询资源申请单。支持状态、名称及排序，批量返回申请表单字段、目录摘要和当前流程任务。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 资源申请分页：申请单以关系库主表为准，目录、扩展字段与流程按当前页批量读取。
import tenantRuntime
import approvalRuntime
import tableSort
import java.util.List

var tenantId = tenantRuntime.id()
var userId = approvalRuntime.principal().userId
if (tenantId == null || userId == null) { exit 401, '请先登录' }
var pageNum = Math.max(1, body?.pageNum::int(1))
var pageSize = Math.min(100, Math.max(1, body?.pageSize::int(20)))
var conditions = body?.conditions || []
if (!(conditions instanceof List) || conditions.size() > 2) { exit 400, '申请单筛选条件无效' }
var status = null
var keyword = null
for (condition in conditions) {
    if (condition == null || condition.value == null || (condition.value+'').trim() == '') { continue }
    var value = (condition.value+'').trim()
    if (condition.field == 'flowStatus' && condition.type == 'match' && value.matches('[0-9]{1,3}')) {
        status = value::int
    } else if (condition.field == 'applyName' && condition.type == 'like' && value.length() <= 100) {
        keyword = '%'+value.toLowerCase().replace('!','!!').replace('%','!%').replace('_','!_')+'%'
    } else { exit 400, '不支持的申请单筛选条件' }
}
var orderColumn = tableSort.field(body, {createdTime:'a.created_time',updatedTime:'a.updated_time',applyName:'a.apply_name'}, 'updatedTime')
var direction = tableSort.direction(body)
var result = db.page("""
    SELECT a.* FROM data_apply_form_t a
    WHERE a.tenant_id=#{tenantId} AND COALESCE(a.is_del,0)=0
      AND COALESCE(NULLIF(a.apply_user_id,''),a.created_by)=#{userId}
    <if test="status != null">AND a.flow_status=#{status}</if>
    <if test="keyword != null">AND LOWER(a.apply_name) LIKE #{keyword} ESCAPE '!'</if>
"""+' ORDER BY CASE WHEN '+orderColumn+' IS NULL THEN 1 ELSE 0 END,'+orderColumn+' '+direction+',a.tid ASC', pageSize, (pageNum-1)*pageSize)
var rows = result.list || []
if (rows.size() == 0) { return {pageNum:pageNum,pageSize:pageSize,total:result.total,list:[]} }

var applyIds = rows.map(row => row.tid)
var catalogIds = []
var catalogByApply = {}
for (row in rows) {
    var references = (row.catalogIds || '').split(',')
    var firstId = references.size() == 0 ? '' : references[0].trim()
    if (firstId != '') {
        catalogByApply[row.tid] = firstId
        if (!catalogIds.contains(firstId)) { catalogIds.add(firstId) }
    }
}
var catalogs = {}
if (catalogIds.size() > 0) {
    var catalogRows = db.table('da_catalog_t').columns('tid','catalog_name','org_id','target_table_id')
        .where().eq('tenant_id',tenantId).eq('is_del',0).in('tid',catalogIds).select()
    for (catalog in catalogRows) { catalogs[catalog.tid] = catalog }
    var catalogProps = db.table('da_prop_t').columns('parent_id','prop_name','prop_value').where()
        .eq('tenant_id',tenantId).eq('is_del',0).eq('data_type','catalog').in('parent_id',catalogIds)
        .in('prop_name',['apiId','fileId','dirId','shareType','updateCycle']).select()
    for (prop in catalogProps) {
        var catalog = catalogs[prop.parentId]
        if (catalog != null) { catalog[prop.propName] = prop.propValue }
    }
}
var applyProps = db.table('da_prop_t').columns('parent_id','prop_name','prop_value').where()
    .eq('tenant_id',tenantId).eq('is_del',0).eq('data_type','apply_form').in('parent_id',applyIds).select()
var byApply = {}
for (row in rows) { byApply[row.tid] = row }
for (prop in applyProps) {
    var row = byApply[prop.parentId]
    if (row != null && !row.containsKey(prop.propName)) { row[prop.propName] = prop.propValue }
}

// 当前流程按业务单关联，优先使用已保存的实例标识。旧单据按最近有效流程回读。
var flowRows = db.select("""
    SELECT ranked.apply_id,ranked.task_id,ranked.flow_code FROM (
        SELECT a.tid AS apply_id,t.id AS task_id,d.flow_code,
               ROW_NUMBER() OVER (PARTITION BY a.tid ORDER BY i.create_time DESC,i.id DESC,t.create_time DESC,t.id DESC) AS row_no
        FROM data_apply_form_t a
        JOIN flow_instance i ON i.business_id=a.tid AND i.tenant_id=a.tenant_id AND i.del_flag='0'
          AND (NULLIF(a.flow_instance_id,'') IS NULL OR TRIM(CAST(i.id AS CHAR(64)))=a.flow_instance_id)
        JOIN flow_definition d ON d.id=i.definition_id AND d.tenant_id=a.tenant_id AND d.del_flag='0'
        JOIN flow_task t ON t.instance_id=i.id AND t.tenant_id=a.tenant_id AND t.del_flag='0' AND t.node_type=1
        WHERE a.tenant_id=#{tenantId} AND COALESCE(a.is_del,0)=0
          AND COALESCE(NULLIF(a.apply_user_id,''),a.created_by)=#{userId}
          AND a.tid IN <foreach collection="applyIds" item="applyId" open="(" separator="," close=")">#{applyId}</foreach>
    ) ranked WHERE ranked.row_no=1
""")
var flows = {}
for (flow in flowRows) { flows[flow.applyId] = flow }
for (row in rows) {
    var catalogId = catalogByApply[row.tid]
    var catalog = catalogs[catalogId] || {tid:catalogId,catalogName:row.applyName}
    var resourceTypes = []
    if (catalog.targetTableId) { resourceTypes.add('table') }
    if (catalog.apiId) { resourceTypes.add('api') }
    if (catalog.fileId) { resourceTypes.add('file') }
    if (catalog.dirId) { resourceTypes.add('dir') }
    catalog.resType = resourceTypes
    catalog.assetType = 'catalog'
    row.detail = catalog
    row.id = row.tid
    row.businessId = row.tid
    var flow = flows[row.tid]
    if (flow != null) { row.taskId = flow.taskId+''; row.approveType = flow.flowCode }
}
return {pageNum:pageNum,pageSize:pageSize,total:result.total,list:rows}

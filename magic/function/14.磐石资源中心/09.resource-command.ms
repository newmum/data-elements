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
import tenantRuntime
import metadataAsset
import tableRead
import cn.hutool.core.util.IdUtil
import cn.dev33.satoken.stp.StpUtil
import '@/resource-center/model-read' as readModels
import '@/resource-center/technical-read' as technicalRead
import '@/resource-center/planning-read' as readPlanning
import '@post:/dst/database/metadata/table/sample-data' as sampleData

/**
 * 磐石资源归属命令。/resource-center/resource-command
 * input 提供 method、id、action、version 和 body。支持读取资源及预览、登记、版本校验归档。
 * 在当前租户维护共享技术表的资源属性，登记与审计一并提交，预览按字段敏感性控制返回内容。
 */

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '无法识别当前租户'
}
var actorId = StpUtil.getLoginIdAsString()
var now = new Date()
var text = v => v == null ? '' : (v + '').trim()
var payload = input.body == null ? {} : input.body
var id = text(input.id)
var action = text(input.action)
var method = text(input.method)
var find = (rows, key) => {
    for (row in rows) {
        if (row.id == key) {
            return row
        }
    }
    exit 404, '对象不存在或不属于当前租户'
}

if (id == '' && method == 'POST') {
    id = text(payload.entityId)
    var table = db.camel().table('db_table_t').columns(
        'tid', 'datasource_id', 'table_name', 'table_name_cn', 'resource_domain_id', 'resource_state', 'resource_revision_no'
    ).where().eq('tenant_id', tenantId).eq('tid', id).eq('is_del', 0).selectOne()
    if (table == null) {
        exit 404, '技术实体不存在'
    }
    if (text(table.resourceDomainId) != '' && table.resourceState == 'ACTIVE') {
        exit 409, '该技术实体已有有效资源登记'
    }
    var domain = find(readPlanning({}).databases, text(payload.databaseId))
    if (domain.state != 'ACTIVE') {
        exit 409, '规划分库已归档'
    }
    var owner = text(payload.owner)
    if (owner == '' || owner.length() > 128) {
        exit 400, '请填写责任方，最多128字'
    }
    var name = text(payload.name) == '' ? table.tableNameCn : text(payload.name)
    if (name == null || text(name) == '') {
        name = table.tableName
    }
    if (text(name).length() > 512 || text(payload.domain).length() > 64) {
        exit 400, '名称或业务域超过长度限制'
    }
    var expected = table.resourceRevisionNo
    // 同时核对登记条件和旧版本，审计写入失败时回滚登记。
    db.transaction(() => {
        var changed = db.table('db_table_t').withBlank().where().eq('tenant_id', tenantId)
            .eq('tid', id).eq('is_del', 0).eq('resource_revision_no', expected)
            .and(available => available.isNull('resource_domain_id').or().eq('resource_state', 'ARCHIVED'))
            .update({
                resource_domain_id: domain.id, resource_owner: owner, resource_state: 'ACTIVE',
                resource_revision_no: expected + 1, table_name_cn: name, business_type: text(payload.domain),
                asset_desc: text(payload.description), asset_status: 2, flow_status: 2, reg_time: now,
                updated_time: now, updated_by: actorId
            })
        if (changed != 1) {
            exit 409, '该技术实体已由另一请求登记'
        }
        db.table('res_resource_operation_log').primary('tid').insert({
            tid: IdUtil.fastSimpleUUID(), tenant_id: tenantId, actor_id: actorId,
            action_name: '登记仓库资源', object_id: id, object_name: name,
            detail_text: '沿用共享技术实体，未复制数据表', created_time: now
        })
    })
    if (text(table.datasourceId) != '') {
        metadataAsset.refreshRegistrationStatus(table.datasourceId, false)
    }
    return find(readModels({}).resources, id)
}

if (id == '') {
    exit 404, '对象不存在或不属于当前租户'
}
var resource = find(readModels({}).resources, id)
if (method == 'GET') {
    var technical = technicalRead({entityIds: [id]})
    var record = resource
    record.source = find(technical.sources, resource.structure.sourceId)
    var allowed = []
    var masked = []
    var fields = db.lower().table('db_table_column_t').columns('column_name', 'is_masking').where()
        .eq('tenant_id', tenantId).eq('table_id', id).eq('is_del', 0).orderBy('ordinal_position').select()
    // 凭据字段不进入预览请求，个人信息和显式脱敏字段在响应中统一掩码。
    for (field in fields) {
        var name = text(field.column_name)
        var normalized = name.toLowerCase()
        if (normalized.matches('.*(password|secret|token|credential|private_key).*')) {
            continue
        }
        allowed.add(name)
        if (field.is_masking == 1 || normalized.matches('.*(phone|mobile|email|identity|certificate_no).*')) {
            masked.add(name)
        }
    }
    record.records = []
    if (allowed.size() > 0) {
        var previousBody = body
        try {
            body = {tableId: id, sampleSize: 10, columns: allowed}
            var sample = sampleData()
            var rows = sample.data == null ? (sample.rows == null ? [] : sample.rows) : sample.data
            for (row in rows) {
                for (fieldName in masked) {
                    if (row[fieldName] != null) {
                        row[fieldName] = '***'
                    }
                }
                record.records.add(row)
            }
        } catch (e) {
            record.previewError = '读取数据预览失败，请稍后重试'
        } finally {
            body = previousBody
        }
    }
    return record
}
if (action != 'archive') {
    exit 400, '不支持的资源操作'
}
if (text(input.version) != text(resource.version)) {
    exit 409, '资源已更新，请刷新重试'
}
// 只检查当前租户仍有效的发布引用，找到一项就拒绝归档。
var column = (alias, name) => tableRead.column(alias, name, 'TEXT')
var numeric = (alias, name) => tableRead.column(alias, name, 'NUMBER')
var published = tableRead.from('res_catalog_publish_request', 'p', tenantId)
published.leftJoin('da_catalog_t', 'c', 'tid', 'catalog_id', [tableRead.equal(numeric('c', 'is_del'), 0)])
published.where(tableRead.equal(numeric('p', 'is_del'), 0))
published.where(tableRead.equal(column('p', 'request_status'), 'PUBLISHED'))
published.where(tableRead.equal(column('c', 'source_table_id'), id))
var publishedId = column('p', 'tid')
if (published.rows({id: publishedId}, publishedId, 'asc', 1).size() > 0) {
    exit 409, '仍有已发布目录引用该资源，请先下线目录'
}
var expected = resource.version
var changed = db.table('db_table_t').where().eq('tenant_id', tenantId).eq('tid', id).eq('is_del', 0)
    .eq('resource_revision_no', expected).update({
        resource_state: 'ARCHIVED', resource_revision_no: expected + 1, updated_time: now, updated_by: actorId
    })
if (changed != 1) {
    exit 409, '资源已更新，请刷新重试'
}
return {id: id, state: 'ARCHIVED', version: expected + 1}

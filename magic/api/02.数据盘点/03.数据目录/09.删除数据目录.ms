{
  "properties": {},
  "id": "datapd_catalog_delete_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "09.删除数据目录",
  "createTime": null,
  "updateTime": 1790644837589,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/delete",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2086383506325794816\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\"tid\":\"2086383506325794816\",\"deleted\":true}",
  "description": "按当前租户软删除目录和关联；兼容未安装历史关联表的租户库，保留已采集表及字段；使用请求参数 tid、id、dryRun；涉及 da_catalog_t、da_catalog_item_t、db_table_t、da_prop_t、da_app_catalog_rela 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.删除数据目录
// 路由：POST /dst/catalog/delete
// 业务说明：按当前租户软删除目录和关联；兼容未安装历史关联表的租户库，保留已采集表及字段；使用请求参数 tid、id、dryRun；涉及 da_catalog_t、da_catalog_item_t、db_table_t、da_prop_t、da_app_catalog_rela 等业务表。
import tenantRuntime
import identityRuntime
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import log

// Shared catalog soft-delete. Read/write: da_catalog_t, da_prop_t,
// da_catalog_item_t and table-to-catalog references; never drop collected tables.
// dryRun checks ownership and reports affected counts without changing data.
var tenantId = tenantRuntime.id()
var tid = body == null ? null : (body.tid == null ? body.id : body.tid)
if (tid == null || (tid + '').trim() == '') { exit 400, '目录标识不能为空' }
var catalog = db.table('da_catalog_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).selectOne()
if (catalog == null) { exit 404, '数据目录不存在或无访问权限' }
// Historical restores need not contain the retired app/catalog association.
// Inspect the CURRENT TENANT database before opening the write transaction.
// Other SQL/schema errors are not swallowed; they must roll back the delete.
var hasLegacyAssociation = identityRuntime.hasTable('da_app_catalog_rela')
if (body.dryRun == true) {
    return { tid: tid, dryRun: true, catalogName: catalog.catalogName,
        dataItems: db.table('da_catalog_item_t').where().eq('tenant_id', tenantId).eq('catalog_id', tid).eq('is_del', 0).count(),
        associatedTables: db.table('db_table_t').where().eq('tenant_id', tenantId).eq('source_catalog_id', tid).eq('is_del', 0).count() }
}
db.transaction(() => {
    var updatedTime = new Date()
    var affected = db.table('da_catalog_t').where().eq('tenant_id', tenantId).eq('tid', tid).eq('is_del', 0).update({is_del: 1, updated_time: updatedTime})
    if (affected != 1) { exit 409, '目录状态已变化，请刷新后重试' }
    db.table('da_prop_t').where().eq('tenant_id', tenantId).eq('parent_id', tid).eq('is_del', 0).update({is_del: 1, updated_time: updatedTime})
    db.table('da_catalog_item_t').where().eq('tenant_id', tenantId).eq('catalog_id', tid).eq('is_del', 0).update({is_del: 1, updated_time: updatedTime})
    if (hasLegacyAssociation) {
        db.table('da_app_catalog_rela').where().eq('tenant_id', tenantId).eq('data_catalog_id', tid).eq('is_del', 0).update({is_del: 1, updated_time: updatedTime})
    }
    db.table('db_table_t').where().eq('tenant_id', tenantId).eq('source_catalog_id', tid)
        .update({source_catalog_id: null, catalog_name: null, catalog_name_en: null, updated_time: updatedTime}, true)
})
var indexRefreshPending = false
try { esCommonService.deleteById('dataassets', tid) }
catch (error) {
    indexRefreshPending = true
    log.warn('Catalog deleted; ES projection cleanup pending: id={}, message={}', tid, error.message)
}
return { tid: tid, deleted: true, indexRefreshPending: indexRefreshPending }

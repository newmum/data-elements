{
  "properties": {},
  "id": "datapd_db_table_save_01",
  "script": null,
  "groupId": "datapd_db_table_group_01",
  "name": "04.保存数据表",
  "createTime": 1784454007655,
  "updateTime": 1787153759051,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/saveOrUpdate",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"可选\",\n    \"propList\": {\n        \"dbId\": \"8b0a98de552b4bc68a203293fadfff33\",\n        \"tableName\": \"表名\"\n    },\n    \"tenantId\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "数据表管理 04：新增或修改 db_table_t；使用请求参数 propList；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.保存数据表
// 路由：POST /dst/database/table/saveOrUpdate
// 业务说明：数据表管理 04：新增或修改 db_table_t；使用请求参数 propList；执行数据库写入并返回处理结果。
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import log
import '@post:/dst/maintenance/refresh' as refreshAssetIndex
import tenantRuntime

/*
 * 数据表管理 04：新增或修改 db_table_t。
 *
 * 全部表属性写入 db_table_t 物理字段，并同步 dataassets ES 文档。表 ID 沿用
 * 调用方传入的 tid；不写 da_prop_t、da_asset_t 或审批关系。
 */

var textOf = (value) => value == null ? null : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var newId = () => IdUtil.getSnowflakeNextIdStr()
var nowTime = () => new Date()
var rowValue = (row, keys) => {
    if (row == null) {
        // 返回当前业务分支的标准结果。
        return null
    }
    for (key in keys) {
        var value = row instanceof Map ? row.get(key) : row[key]
        if (value != null) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}
var firstBody = (keys) => {
    for (key in keys) {
        var value = body == null ? null : body[key]
        if (!blank(value)) {
            // 返回当前业务分支的标准结果。
            return value
        }
    }
    // 返回当前业务分支的标准结果。
    return null
}

// 解析请求体并为可选参数设置默认值。
var props = body == null || body.propList == null ? {} : body.propList
var tableId = firstBody(['tid', 'tableId', 'id'])
if (blank(tableId)) {
    tableId = newId()
}
var datasourceId = props.datasourceId == null ? props.dbId : props.datasourceId
if (blank(datasourceId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'datasourceId 不能为空'
}
// 从 MySQL 业务表读取当前有效数据。
var existing = db.table('db_table_t').where().eq('tid', tableId).selectOne()
var tableName = props.tableName == null ? props.tableNameEn : props.tableName
if (blank(tableName) && existing == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'tableName 不能为空'
}
// 数据表资产必须继承当前登录租户，不能信任调用方携带的 tenantId。
var tenantId = textOf(tenantRuntime.id()).trim()
if (blank(tenantId)) {
    exit 401, '当前登录租户无效，请重新登录后再保存数据表'
}
var values = {
    tid: tableId,
    datasource_id: datasourceId,
    table_name: tableName,
    table_name_en: tableName,
    table_name_cn: props.tableNameCn,
    table_comment: props.tableComment == null ? props.tableNameCn : props.tableComment,
    table_type: props.tableType,
    business_type: props.businessType,
    business_type_reason: props.businessTypeReason,
    annotated: props.annotated,
    record_count: props.recordCount,
    field_count: props.fieldCount,
    storage_size: props.storageSize,
    total_size_formatted: props.totalSizeFormatted == null ? props.storageSize : props.totalSizeFormatted,
    total_size_bytes: props.totalSizeBytes == null ? props.totalSizeBytes_num : props.totalSizeBytes,
    org_id: props.orgId,
    org_path: props.orgPath,
    related_directory: props.relatedDirectory,
    data_source_type: props.dataSourceType,
    source_table_id: tableId,
    source_table_name: tableName,
    manage_unit: props.manageUnit == null ? props.orgId : props.manageUnit,
    asset_desc: props.assetDesc == null ? props.tableComment : props.assetDesc,
    tenant_id: tenantId,
    asset_status: 2,
    flow_status: 2,
    flow_order_id: null,
    is_del: 0,
    updated_time: nowTime()
}
if (existing == null) {
    values.created_time = nowTime()
    values.reg_time = nowTime()
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('db_table_t').primary('tid').insert(values)
} else {
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('db_table_t').primary('tid').update(values, true)
}
var doc = {
    tid: tableId,
    id: tableId,
    assetType: 'table',
    dbId: datasourceId,
    datasourceId: datasourceId,
    tableName: tableName,
    tableNameEn: tableName,
    tableNameCn: props.tableNameCn,
    tableComment: props.tableComment == null ? props.tableNameCn : props.tableComment,
    tableType: props.tableType,
    businessType: props.businessType,
    recordCount: props.recordCount,
    fieldCount: props.fieldCount,
    storageSize: props.storageSize,
    orgId: props.orgId,
    orgPath: props.orgPath,
    dataSourceType: props.dataSourceType,
    assetStatus: 2,
    flowStatus: 2,
    updatedTime: date_format(now)
}
var excludedIndexProps = {
    fieldGovernanceConfig: true,
    dictionaryProfiles: true,
    dictionaryProfile: true,
    dictionaryCategories: true,
    columns: true
}
for (key in props.keySet()) {
    if (excludedIndexProps[key] == null) {
        doc[key] = props[key]
    }
}
try {
    // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
    esCommonService.saveOrUpdate('dataassets', tableId, jsons.toJSONStr(doc), false)
} catch (e) {
    log.warn('同步数据表 ES 失败, tid: {}, error: {}', tableId, e.message)
}
// 返回当前业务分支的标准结果。
try {
    body = { tid: datasourceId }
    refreshAssetIndex()
} catch (e) {
    log.warn('sync datasource ES stats failed, tid: {}, error: {}', datasourceId, e.message)
}
return { tid: tableId, id: tableId, assetType: 'table' }

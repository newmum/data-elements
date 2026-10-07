{
  "properties": {},
  "id": "datapd_catalog_detail_compat_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "04.数据目录详情",
  "createTime": null,
  "updateTime": 1787153759311,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2086383506325794816\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"tid\": \"2086383506325794816\",\n  \"catalogName\": \"公安案件登记业务表\"\n}",
  "description": "数据目录详情兼容接口（POST /dst/catalog/detail）。统一从 da_catalog_t 与 da_prop_t 读取登记完成的源头数据目录；使用请求参数 tid、id。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.数据目录详情
// 路由：POST /dst/catalog/detail
// 业务说明：数据目录详情兼容接口（POST /dst/catalog/detail）。统一从 da_catalog_t 与 da_prop_t 读取登记完成的源头数据目录；使用请求参数 tid、id。
// 数据目录详情：处理 POST /detail 的核心查询和组装返回数据逻辑。
var text = value => value == null ? '' : value + ''
var blank = value => value == null || text(value).trim() == ''
var sqlValue = value => "'" + text(value).replace("'", "''") + "'"
var rowValue = (row, keys) => {
    for (key in keys) {
        if (row != null && row[key] != null) {
            return row[key]
        }
    }
    return null
}

var tid = body == null ? null : (blank(body.tid) ? body.id : body.tid)
if (blank(tid)) {
    exit 400, '目录标识不能为空'
}

var rows = db.select(
    "select c.tid,c.tenant_id tenantId,c.asset_status assetStatus,c.flow_status flowStatus," +
    "c.reg_time regTime,c.created_time createdTime,c.updated_time updatedTime," +
    "c.catalog_name catalogName,c.catalog_name_en catalogNameEn,c.asset_desc assetDesc," +
    "c.app_id appId,c.app_name appName,c.db_id dbId,c.db_name dbName," +
    "c.source_table_id sourceTableId,c.source_table_name sourceTableName," +
    "c.source_db_id sourceDbId,c.source_db_name sourceDbName," +
    "c.target_table_id targetTableId,c.target_table_name targetTableName,c.target_db_id targetDbId,c.target_db_type targetDbType," +
    "c.org_id orgId,c.manage_unit manageUnit,c.business_type businessType,c.data_source_type dataSourceType," +
    "c.timestamp_field timestampField,c.dictionary_table_flag dictionaryTableFlag,c.field_governance_config fieldGovernanceConfig " +
    "from da_catalog_t c where ifnull(c.is_del,0)=0 and c.tid=" + sqlValue(tid) + " limit 1")
if (rows == null || rows.size() == 0) {
    exit 404, '数据目录不存在或已删除'
}

var row = rows[0]
var item = {
    tid: rowValue(row, ['tid']), id: rowValue(row, ['tid']), tenantId: rowValue(row, ['tenantId', 'tenantid']),
    assetType: 'catalog', assetStatus: rowValue(row, ['assetStatus', 'assetstatus']), flowStatus: rowValue(row, ['flowStatus', 'flowstatus']),
    regTime: rowValue(row, ['regTime', 'regtime']), createdTime: rowValue(row, ['createdTime', 'createdtime']), updatedTime: rowValue(row, ['updatedTime', 'updatedtime']),
    catalogName: rowValue(row, ['catalogName', 'catalogname']), catalogNameEn: rowValue(row, ['catalogNameEn', 'catalognameen']), assetDesc: rowValue(row, ['assetDesc', 'assetdesc']),
    appId: rowValue(row, ['appId', 'appid']), appName: rowValue(row, ['appName', 'appname']), dbId: rowValue(row, ['dbId', 'dbid']), dbName: rowValue(row, ['dbName', 'dbname']),
    sourceTableId: rowValue(row, ['sourceTableId', 'sourcetableid']), sourceTableName: rowValue(row, ['sourceTableName', 'sourcetablename']),
    sourceDbId: rowValue(row, ['sourceDbId', 'sourcedbid']), sourceDbName: rowValue(row, ['sourceDbName', 'sourcedbname']),
    targetTableId: rowValue(row, ['targetTableId', 'targettableid']), targetTableName: rowValue(row, ['targetTableName', 'targettablename']),
    targetDbId: rowValue(row, ['targetDbId', 'targetdbid']), targetDbType: rowValue(row, ['targetDbType', 'targetdbtype']),
    orgId: rowValue(row, ['orgId', 'orgid']), manageUnit: rowValue(row, ['manageUnit', 'manageunit']), businessType: rowValue(row, ['businessType', 'businesstype']),
    dataSourceType: rowValue(row, ['dataSourceType', 'datasourcetype']), timestampField: rowValue(row, ['timestampField', 'timestampfield']),
    dictionaryTableFlag: rowValue(row, ['dictionaryTableFlag', 'dictionarytableflag']), fieldGovernanceConfig: rowValue(row, ['fieldGovernanceConfig', 'fieldgovernanceconfig'])
}
item.asset_type = item.assetType
item.asset_status = item.assetStatus
item.flow_status = item.flowStatus

var props = {}
var propRows = db.select("select prop_name propName,prop_value propValue from da_prop_t where ifnull(is_del,0)=0 and data_type='catalog' and parent_id=" + sqlValue(tid))
for (prop in propRows) {
    var name = rowValue(prop, ['propName', 'propname'])
    var value = rowValue(prop, ['propValue', 'propvalue'])
    if (!blank(name)) {
        props[name] = value
        if (item[name] == null || item[name] == '') {
            item[name] = value
        }
    }
}
item.props = props
return item

{
  "properties": {},
  "id": "datapd_catalog_items_list_01",
  "script": null,
  "groupId": "datapd_catalog_group_01",
  "name": "07.数据目录数据项列表",
  "path": "/catalog-items/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"tid\": \"2086383506325794816\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "[]",
  "description": "数据目录数据项列表（POST /dst/catalog/catalog-items/list）。读取 da_catalog_item_t，供目录详情、登记和文件目录兼容调用；使用请求参数 catalogId、tid、id。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.数据目录数据项列表
// 路由：POST /dst/catalog/catalog-items/list
// 业务说明：数据目录数据项列表（POST /dst/catalog/catalog-items/list）。读取 da_catalog_item_t，供目录详情、登记和文件目录兼容调用；使用请求参数 catalogId、tid、id。
// 数据目录数据项列表：处理 POST /catalog-items/list 的核心查询和组装返回数据逻辑。
import cn.hutool.json.JSONUtil as jsons

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

var catalogId = body == null ? null : (blank(body.catalogId) ? (blank(body.tid) ? body.id : body.tid) : body.catalogId)
if (blank(catalogId)) {
    exit 400, '目录标识不能为空'
}
var rows = db.select(
    "select tid,catalog_id catalogId,tenant_id tenantId,col_name colName,col_en colEn,col_type colType,col_comment colComment," +
    "col_length colLength,is_pk isPk,is_masking isMasking,is_fk isFk,is_nullable isNullable,date_format dateFormat," +
    "mark_lvl markLvl,col_unit colUnit,col_precision colPrecision,masking_id maskingId,sort_no sortNo,share_type shareType," +
    "share_condition shareCondition,is_dict isDict,dict_name dictName,col_max_length colMaxLength,default_value defaultValue," +
    "data_standard_id dataStandardId,quality_rule qualityRule,enable_code_table enableCodeTable,code_table_id codeTableId," +
    "source_table_column_id sourceTableColumnId,target_table_column_id targetTableColumnId " +
    "from da_catalog_item_t where ifnull(is_del,0)=0 and catalog_id=" + sqlValue(catalogId) + " order by ifnull(sort_no,2147483647),created_time,tid")
var items = []
for (row in rows) {
    items.add({
        tid: rowValue(row, ['tid']), catalogId: rowValue(row, ['catalogId', 'catalogid']), tenantId: rowValue(row, ['tenantId', 'tenantid']),
        colName: rowValue(row, ['colName', 'colname']), colEn: rowValue(row, ['colEn', 'colen']), colType: rowValue(row, ['colType', 'coltype']),
        colComment: rowValue(row, ['colComment', 'colcomment']), colLength: rowValue(row, ['colLength', 'collength']), isPk: rowValue(row, ['isPk', 'ispk']),
        isMasking: rowValue(row, ['isMasking', 'ismasking']), isFk: rowValue(row, ['isFk', 'isfk']), isNullable: rowValue(row, ['isNullable', 'isnullable']),
        dateFormat: rowValue(row, ['dateFormat', 'dateformat']), markLvl: rowValue(row, ['markLvl', 'marklvl']), colUnit: rowValue(row, ['colUnit', 'colunit']),
        colPrecision: rowValue(row, ['colPrecision', 'colprecision']), maskingId: rowValue(row, ['maskingId', 'maskingid']), sortNo: rowValue(row, ['sortNo', 'sortno']),
        shareType: rowValue(row, ['shareType', 'sharetype']), shareCondition: rowValue(row, ['shareCondition', 'sharecondition']),
        isDict: rowValue(row, ['isDict', 'isdict']), dictName: rowValue(row, ['dictName', 'dictname']), colMaxLength: rowValue(row, ['colMaxLength', 'colmaxlength']),
        defaultValue: rowValue(row, ['defaultValue', 'defaultvalue']), dataStandardId: rowValue(row, ['dataStandardId', 'datastandardid']),
        qualityRule: rowValue(row, ['qualityRule', 'qualityrule']), enableCodeTable: rowValue(row, ['enableCodeTable', 'enablecodetable']),
        codeTableId: rowValue(row, ['codeTableId', 'codetableid']), sourceTableColumnId: rowValue(row, ['sourceTableColumnId', 'sourcetablecolumnid']),
        targetTableColumnId: rowValue(row, ['targetTableColumnId', 'targettablecolumnid'])
    })
}
// Some previously registered tables retained governance fields only. Use that
// snapshot as a read-only fallback so historical directories still show items.
if (items.size() == 0) {
    var catalogRows = db.select("select field_governance_config fieldGovernanceConfig from da_catalog_t where ifnull(is_del,0)=0 and tid=" + sqlValue(catalogId) + " limit 1")
    if (catalogRows != null && catalogRows.size() > 0) {
        try {
            var configText = rowValue(catalogRows[0], ['fieldGovernanceConfig', 'fieldgovernanceconfig'])
            var config = blank(configText) ? null : jsons.parseObj(configText)
            var fields = config == null ? null : config.get('fields')
            if (fields != null) {
                var sortNo = 0
                for (field in fields) {
                    sortNo = sortNo + 1
                    items.add({
                        catalogId: catalogId,
                        colName: rowValue(field, ['columnComment', 'colName', 'catalogItemName']),
                        colEn: rowValue(field, ['columnName', 'colEn', 'catalogItemEn']),
                        colType: rowValue(field, ['columnType', 'colType', 'dataType']),
                        colLength: rowValue(field, ['length', 'columnLength']),
                        colComment: rowValue(field, ['columnComment', 'colName']),
                        isPk: rowValue(field, ['primaryKey', 'isPk']) ? '1' : '0',
                        isNullable: rowValue(field, ['nullable', 'isNullable']) == false ? '0' : '1',
                        sourceTableColumnId: rowValue(field, ['tid', 'sourceTableColumnId']),
                        sortNo: sortNo,
                        enableCodeTable: 0
                    })
                }
            }
        } catch (e) {
            log.warn('read catalog governance snapshot failed, catalogId: {}, error: {}', catalogId, e.message)
        }
    }
}
return items

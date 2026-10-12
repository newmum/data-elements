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
import tenantRuntime
import dataScope
import tableSort
import cn.hutool.core.util.NumberUtil

var text = value => value == null ? '' : value + ''
var blank = value => value == null || text(value).trim() == ''
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
var tenantId = tenantRuntime.id()
var scope = dataScope.dataSource()
var scopeMode = scope.scope + ''; var scopeUserId = scope.userId + ''
var scopeOrgIds = scope.organizationIds == null ? [] : scope.organizationIds
var scopeOrgCount = scopeOrgIds.size(); if (scopeOrgCount == 0) { scopeOrgIds = [''] }
if (!['ALL','OWNER','ORG'].contains(scopeMode) || scopeUserId == '') { exit 403,'无法确定当前数据范围' }
var parent = db.selectOne("""SELECT c.tid,c.field_governance_config AS config FROM da_catalog_t c
 WHERE c.tenant_id=#{tenantId} AND c.tid=#{catalogId} AND COALESCE(c.is_del,0)=0
   AND (#{scopeMode}='ALL' OR
      EXISTS (SELECT 1 FROM db_datasource_t d WHERE d.tid=c.db_id AND d.tenant_id=c.tenant_id AND d.is_del=0
        AND ((#{scopeMode}='OWNER' AND d.created_by=#{scopeUserId}) OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND d.org_id IN
          <foreach collection="scopeOrgIds" item="oid" open="(" close=")" separator=",">#{oid}</foreach>))) OR
      ((c.db_id IS NULL OR c.db_id='') AND ((#{scopeMode}='OWNER' AND c.created_by=#{scopeUserId}) OR (#{scopeMode}='ORG' AND #{scopeOrgCount}>0 AND c.org_id IN
          <foreach collection="scopeOrgIds" item="oid" open="(" close=")" separator=",">#{oid}</foreach>))))""")
if (parent == null) { exit 404,'目录不存在或无权读取' }
var paging = body.pageNo != null || body.page != null
var page = NumberUtil.parseInt((body.pageNo == null ? body.page == null ? 1 : body.page : body.pageNo) + '')
var size = NumberUtil.parseInt((body.pageSize == null ? body.size == null ? 20 : body.size : body.pageSize) + '')
if (page < 1 || page > 100000 || size < 1 || size > 100) { exit 400,'分页参数无效，每页最多100项' }
var order = tableSort.field(body,{colName:'col_name',colEn:'col_en',colLength:'col_length',sortNo:'sort_no',createdTime:'created_time',updatedTime:'updated_time'},'sortNo')
var dir = tableSort.direction(body)
var sql = (
    "select tid,catalog_id catalogId,tenant_id tenantId,col_name colName,col_en colEn,col_type colType,col_comment colComment," +
    "col_length colLength,is_pk isPk,is_masking isMasking,is_fk isFk,is_nullable isNullable,date_format dateFormat," +
    "mark_lvl markLvl,col_unit colUnit,col_precision colPrecision,masking_id maskingId,sort_no sortNo,share_type shareType," +
    "share_condition shareCondition,is_dict isDict,dict_name dictName,col_max_length colMaxLength,default_value defaultValue," +
    "data_standard_id dataStandardId,quality_rule qualityRule,enable_code_table enableCodeTable,code_table_id codeTableId," +
    "source_table_column_id sourceTableColumnId,target_table_column_id targetTableColumnId " +
    "from da_catalog_item_t where COALESCE(is_del,0)=0 and tenant_id=#{tenantId} and catalog_id=#{catalogId}" +
    " ORDER BY (" + order + ") IS NULL," + order + " " + dir + ",tid ASC")
var result = paging ? db.page(sql,size,(page-1)*size) : {list:db.select(sql + ' LIMIT 1001')}
var rows = result.list
if (!paging && rows.size() > 1000) { exit 400,'数据项超过1000项，请使用分页查询' }
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
if (items.size() == 0 && (!paging || result.total == 0)) {
    var catalogRows = [{fieldGovernanceConfig:parent.config}]
    if (catalogRows != null && catalogRows.size() > 0) {
        try {
            var configText = rowValue(catalogRows[0], ['fieldGovernanceConfig', 'fieldgovernanceconfig'])
            var config = blank(configText) ? null : jsons.parseObj(configText)
            var fields = config == null ? null : config.get('fields')
            if (fields != null) {
                if (fields.size() > 1000) { exit 400,'历史配置超过1000项，请分批整理' }
                var sortNo = 0
                for (field in fields) {
                    sortNo = sortNo + 1
                    items.add({
                        catalogId: catalogId, tid:catalogId + ':' + sortNo,
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
if (rows.size() == 0 && items.size() > 0) {
    items = tableSort.rows(items,body,{colName:'colName',colEn:'colEn',colLength:'colLength',sortNo:'sortNo'},'sortNo','tid')
    if (paging) { var total = items.size(); var start = Math.min((page-1)*size,total); return {list:items.subList(start,Math.min(start+size,total)),total:total} }
}
return paging ? {list:items,total:result.total} : items

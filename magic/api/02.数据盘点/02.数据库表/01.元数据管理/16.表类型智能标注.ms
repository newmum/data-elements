{
  "properties": {},
  "id": "e715b6141da34c4ca9ebd5cc62379c5b",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "16.表类型智能标注",
  "createTime": 1782955344324,
  "updateTime": 1787153758888,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tableTypeAnnocation",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"dbId\": \"aa912e396efb4e5daad7f3a9f383ce4f\",\n    \"dryRun\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "元数据管理 16：智能标注 db_table_t 中的数据表业务类型；使用请求参数 dbId、dryRun；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：16.表类型智能标注
// 路由：POST /dst/database/metadata/tableTypeAnnocation
// 业务说明：元数据管理 16：智能标注 db_table_t 中的数据表业务类型；使用请求参数 dbId、dryRun；执行数据库写入并返回处理结果。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import java.util.Map
import log

/*
 * 元数据管理 16：智能标注 db_table_t 中的数据表业务类型。
 *
 * dryRun=true 只返回建议；否则直接更新 db_table_t.business_type、
 * business_type_reason、annotated 并同步 ES，不再写 da_prop_t。
 */

var textOf = (value) => value == null ? '' : value + ''
var blank = (value) => value == null || textOf(value).trim() == ''
var lower = (value) => textOf(value).toLowerCase()
var first = (row, keys) => {
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
var containsAny = (value, words) => {
    var source = lower(value)
    for (word in words) {
        if (source.indexOf(word) >= 0) {
            // 返回当前业务分支的标准结果。
            return true
        }
    }
    // 返回当前业务分支的标准结果。
    return false
}
var inferType = (table) => {
    var value = lower(first(table, ['tableName', 'table_name'])) + ' ' +
        lower(first(table, ['tableComment', 'table_comment', 'tableNameCn', 'table_name_cn']))
    if (containsAny(value, ['log', 'audit', 'trace', '日志', '审计', '轨迹'])) {
        // 返回当前业务分支的标准结果。
        return { businessType: '日志表', reason: '命中日志或审计关键字' }
    }
    if (containsAny(value, ['dict', 'dictionary', 'code', 'enum', '字典', '码表', '枚举'])) {
        // 返回当前业务分支的标准结果。
        return { businessType: '字典表', reason: '命中字典或码表关键字' }
    }
    if (containsAny(value, ['tmp', 'temp', '临时'])) {
        // 返回当前业务分支的标准结果。
        return { businessType: '临时表', reason: '命中临时表关键字' }
    }
    if (containsAny(value, ['bak', 'backup', 'history', 'archive', '备份', '历史', '归档'])) {
        // 返回当前业务分支的标准结果。
        return { businessType: '备份表', reason: '命中备份或历史关键字' }
    }
    // 返回当前业务分支的标准结果。
    return { businessType: '业务表', reason: '未命中特殊类型关键字，默认业务表' }
}
// 解析请求体并为可选参数设置默认值。
var dbId = body == null ? null : body.dbId
if (blank(dbId)) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '数据源dbId不能为空'
}
var dryRun = body != null && body.dryRun == true
// 从 MySQL 业务表读取当前有效数据。
var tables = db.table('db_table_t').where()
    .eq('datasource_id', dbId).eq('is_del', 0).select()
var rows = []
for (table in tables) {
    var tableId = first(table, ['tid'])
    var decision = inferType(table)
    var item = {
        tid: tableId,
        id: tableId,
        assetType: 'table',
        dbId: dbId,
        tableName: first(table, ['tableName', 'table_name']),
        tableNameEn: first(table, ['tableNameEn', 'table_name_en', 'tableName', 'table_name']),
        tableNameCn: first(table, ['tableNameCn', 'table_name_cn']),
        suggestedBusinessType: decision.businessType,
        annotationReason: decision.reason
    }
    if (!dryRun) {
        // 将校验后的业务数据写入 MySQL 主表。
        db.table('db_table_t').primary('tid').update({
            tid: tableId,
            business_type: decision.businessType,
            business_type_reason: decision.reason,
            annotated: 1,
            updated_time: date_format(now)
        }, true)
        item.businessType = decision.businessType
        item.annotated = 1
        try {
            // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
            esCommonService.saveOrUpdate('dataassets', tableId, jsons.toJSONStr(item), false)
        } catch (e) {
            log.warn('同步表类型标注到 ES 失败, tid: {}, error: {}', tableId, e.message)
        }
    }
    rows.add(item)
}
// 返回当前业务分支的标准结果。
return { dbId: dbId, dryRun: dryRun, total: rows.size(), rows: rows }

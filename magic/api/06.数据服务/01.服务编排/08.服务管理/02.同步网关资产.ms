{
  "properties": {},
  "id": "d28067686c114ad1aca3b65902443832",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "02.同步网关资产",
  "createTime": 1784437770685,
  "updateTime": 1787153758011,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/sync",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"enforce\": \"测试\",\n    \"dryRun\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "同步云梯网关 API 到本地资产；使用请求参数 enforce、dryRun、catalogId；涉及 da_asset_t、da_asset_api_rela、da_prop_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：02.同步网关资产
// 路由：POST /dws/flowserve/gateway/sync
// 业务说明：同步云梯网关 API 到本地资产；使用请求参数 enforce、dryRun、catalogId；涉及 da_asset_t、da_asset_api_rela、da_prop_t 等业务表；执行数据库写入并返回处理结果。
/*
 * 同步云梯网关 API 到本地资产
 *
 * GatewayUtils 只负责签名、令牌和 HTTP 重试；Magic 负责 API 详情转换、
 * 资产/目录关系、动态属性持久化和 ES 投影。body.enforce=true 时覆盖已有
 * 资产；body.dryRun=true 时只返回待同步 ID。
 */
import cn.hutool.core.util.IdUtil
import cn.hutool.core.util.StrUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils
import jsons
import java.util.List
import java.util.Map

var toCamel
toCamel = value => {
    if (value instanceof Map) {
        var result = {}
        for (keyName in value.keySet()) {
            result[StrUtil.toCamelCase(keyName + '')] = toCamel(value[keyName])
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    if (value instanceof List) {
        var result = []
        for (item in value) {
            result.add(toCamel(item))
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    // 返回当前业务分支的标准结果。
    return value
}
// 调用外部网关并统一处理返回结果与异常。
var apiIds = gatewayUtils.callGatewayApiForList('V2DescribeApiIdList', {})
// 解析请求体并为可选参数设置默认值。
var enforce = body?.enforce == true || body?.enforce == 1 || body?.enforce == '1'
var pending = []
for (apiId in apiIds) {
    // 从 MySQL 业务表读取当前有效数据。
    if (enforce || db.table('da_asset_t').where()
        .eq('tid', apiId).eq('asset_type', 'api').eq('is_del', 0).count() == 0) {
        pending.add(apiId)
    }
}
if (body?.dryRun == true) {
    // 返回当前业务分支的标准结果。
    return {total: apiIds.size(), pending: pending.size(), apiIds: pending, dryRun: true}
}

var now = new Date()
var synced = 0
var skipped = 0
var failed = []
for (apiId in pending) {
    try {
        // 调用外部网关并统一处理返回结果与异常。
        var detail = toCamel(gatewayUtils.callGatewayApi('V2DescribeApi', {api_id: apiId}))
        if (detail.errorCode == 'data_not_found') {
            skipped++
            continue
        }
        detail.tid = apiId
        detail.assetType = 'api'
        detail.serviceName = detail.name || ''
        detail.serviceCreateType = detail.serviceBuildId ? 'apiBuild' : 'api'
        detail.updatedTime = date_format(now)

        var catalogId = detail.catalogId || body?.catalogId || '1'
        // 从 MySQL 业务表读取当前有效数据。
        if (catalogId != null && catalogId != '' && db.table('da_asset_api_rela').where()
            .eq('api_id', apiId).eq('catalog_id', catalogId).eq('is_del', 0).count() == 0) {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_asset_api_rela').primary('tid').insert({
                tid: IdUtil.getSnowflakeNextIdStr(),
                api_id: apiId,
                catalog_id: catalogId,
                is_del: 0,
                created_time: now,
                updated_time: now
            })
        }

        // 从 MySQL 业务表读取当前有效数据。
        var current = db.table('da_asset_t').where().eq('tid', apiId).selectOne()
        if (current == null) {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_asset_t').primary('tid').insert({
                tid: apiId,
                asset_type: 'api',
                flow_status: 0,
                asset_status: 2,
                is_del: 0,
                created_time: now,
                updated_time: now,
                reg_time: now
            })
        } else {
            // 将校验后的业务数据写入 MySQL 主表。
            db.table('da_asset_t').where().eq('tid', apiId).update({
                asset_type: 'api',
                flow_status: 0,
                asset_status: 2,
                is_del: 0,
                updated_time: now
            })
        }

        // 调用外部网关并统一处理返回结果与异常。
        var overview = toCamel(gatewayUtils.callGatewayApi('V2ApiCallOverview', {api_id: apiId}))
        if (overview != null) {
            overview.successPercent = 100 - ((overview.errorPercent || '0')::double)
            for (entry in overview.entrySet()) {
                detail[keyName] = value[keyName]
            }
        }

        for (entry in detail.entrySet()) {
            var propName = keyName + ''
            var rawValue = value[keyName]
            var propValue = rawValue instanceof Map || rawValue instanceof List
                ? jsons.toJSONStr(rawValue)
                : rawValue == null ? null : rawValue + ''
            // 从 MySQL 业务表读取当前有效数据。
            var prop = db.table('da_prop_t').where()
                .eq('parent_id', apiId).eq('prop_name', propName).selectOne()
            if (prop == null) {
                // 将校验后的业务数据写入 MySQL 主表。
                db.table('da_prop_t').primary('tid').insert({
                    tid: IdUtil.getSnowflakeNextIdStr(),
                    parent_id: apiId,
                    prop_name: propName,
                    prop_value: propValue,
                    prop_type: '0',
                    data_type: 'api',
                    is_del: 0,
                    created_time: now,
                    updated_time: now
                })
            } else {
                // 将校验后的业务数据写入 MySQL 主表。
                db.table('da_prop_t').where().eq('tid', prop.tid).update({
                    prop_value: propValue,
                    data_type: 'api',
                    is_del: 0,
                    updated_time: now
                })
            }
        }
        // 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
        esCommonService.saveOrUpdate('dataassets', apiId + '', jsons.toJSONStr(detail), false)
        synced++
    } catch (e) {
        failed.add({apiId: apiId, message: e.message})
    }
}

// 返回当前业务分支的标准结果。
return {total: apiIds.size(), pending: pending.size(), synced: synced, skipped: skipped, failed: failed}

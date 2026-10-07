{
  "properties": {},
  "id": "d6a7019f5e4a4a92a34543c1f5290006",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "06.API调用概览",
  "createTime": 1784450000000,
  "updateTime": 1784450000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/apiCallOverview",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按 API 标识读取网关调用概览，将网关字段转换为页面字段并更新对应的接口检索投影。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：06.API调用概览
// 路由：POST /dws/flowserve/gateway/apiCallOverview
// 业务说明：按 API 标识读取网关调用概览，将网关字段转换为页面字段并更新对应的接口检索投影。
/*
 * API 调用概览
 *
 * 网关参数使用下划线格式，页面响应使用驼峰格式。统计结果合并到现有
 * ES API 文档后统一保存，避免只写统计字段导致详情投影丢失。
 */
import cn.hutool.core.util.StrUtil
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils
import '@/common/saveGatewayAsset' as saveGatewayAsset
import java.util.List
import java.util.Map

var convert
convert = (value, underline) => {
    if (value instanceof Map) {
        var result = {}
        for (keyName in value.keySet()) {
            var key = underline
                ? StrUtil.toUnderlineCase(keyName + '')
                : StrUtil.toCamelCase(keyName + '')
            result[key] = convert(value[keyName], underline)
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    if (value instanceof List) {
        var result = []
        for (item in value) {
            result.add(convert(item, underline))
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    // 返回当前业务分支的标准结果。
    return value
}

// 解析请求体并为可选参数设置默认值。
var apiId = body?.apiId
if (apiId == null || apiId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'apiId不能为空'
}
// 调用外部网关并统一处理返回结果与异常。
var overview = convert(gatewayUtils.callGatewayApi('V2ApiCallOverview', convert(body, true)), false)
overview.successPercent = 100 - ((overview.errorPercent || '0')::double)

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
var document = esCommonService.getDocument('dataassets', apiId + '') || {}
for (entry in overview.entrySet()) {
    document[keyName] = value[keyName]
}
saveGatewayAsset(apiId, document, document.catalogId)
// 返回当前业务分支的标准结果。
return overview

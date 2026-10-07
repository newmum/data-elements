{
  "properties": {},
  "id": "daa7019f5e4a4a92a34543c1f5290010",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "10.构建网关服务",
  "createTime": 1784450000000,
  "updateTime": 1784450000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/buildApi",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "Magic 负责构建平台参数转换、响应校验、网关详情补齐和本地资产持久化；使用请求参数 isEmpty。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：10.构建网关服务
// 路由：POST /dws/flowserve/gateway/buildApi
// 业务说明：Magic 负责构建平台参数转换、响应校验、网关详情补齐和本地资产持久化；使用请求参数 isEmpty。
/*
 * 构建网关服务
 *
 * Magic 负责构建平台参数转换、响应校验、网关详情补齐和本地资产持久化。
 * HTTP 模块与 GatewayUtils 分别承担构建平台和网关的运行时调用。
 */
import cn.hutool.core.util.StrUtil
import http
import jsons
import '@/common/gatewayApiDetail' as gatewayApiDetail
import '@/common/saveGatewayAsset' as saveGatewayAsset
import java.util.List
import java.util.Map
import org.springframework.core.env.Environment as environment

var toUnderline
toUnderline = value => {
    if (value instanceof Map) {
        var result = {}
        for (keyName in value.keySet()) {
            result[StrUtil.toUnderlineCase(keyName + '')] = toUnderline(value[keyName])
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    if (value instanceof List) {
        var result = []
        for (item in value) {
            result.add(toUnderline(item))
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    // 返回当前业务分支的标准结果。
    return value
}

// 解析请求体并为可选参数设置默认值。
if (body == null || body.isEmpty()) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '请求参数不能为空'
}
var requestBody = toUnderline(body)
var extendInfo = requestBody.extend_info
var serviceInfo = requestBody.service_info
var catalogId = extendInfo?.catalog_id
if (catalogId == null || catalogId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, '未选择服务目录'
}
requestBody.remove('extend_info')

var raw = http.connect(environment.getProperty('gateway.buildapi.url') + '/openapi/api/sync')
    .contentType('application/json')
    .body(jsons.toJSONStr(requestBody))
    .post()
    .getBody()
var response = raw instanceof Map ? raw : jsons.parseObj(raw + '')
if (response.result_code != 'sys-200' || response.data == null || response.data.size() == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, response.result_code || '服务构建失败'
}
var apiId = response.data[0].api_id
if (apiId == null || apiId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '服务构建未返回API ID'
}

var detail = gatewayApiDetail(apiId)
if (detail == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '资源不存在，无法获取API详情'
}
detail.catalogId = catalogId
detail.serviceName = serviceInfo?.name || detail.name || ''
detail.serviceCreateType = 'apiBuild'
detail.buildApiInfo = body
saveGatewayAsset(apiId, detail, catalogId)
// 返回当前业务分支的标准结果。
return '服务构建成功'

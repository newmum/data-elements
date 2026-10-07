{
  "properties": {},
  "id": "7f2bf7ddf58448f7ae07e3345e7a8b0e",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "03.统一代理网关接口",
  "createTime": 1784437770682,
  "updateTime": 1784437772125,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/proxy",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"action\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取 body.action 作为网关动作，其余请求参数转换为下划线命名后透传。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.统一代理网关接口
// 路由：POST /dws/flowserve/gateway/proxy
// 业务说明：读取 body.action 作为网关动作，其余请求参数转换为下划线命名后透传。
/*
 * 云梯网关统一代理
 *
 * 读取 body.action 作为网关动作，其余请求参数转换为下划线命名后透传。
 * GatewayUtils 只承担签名、令牌缓存、HTTP 调用和失效重试；业务参数编排在本脚本完成。
 * 本接口不写本地数据库。
 */
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils
import cn.hutool.core.util.StrUtil
import java.util.Map
import java.util.List

var toUnderline = value => {
    if (value instanceof Map) {
        var result = {}
        for (keyName in value.keySet()) {
            result[StrUtil.toUnderlineCase(keyName + '')] = toUnderline(value[keyName])
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    if (value instanceof List) {
        // 返回当前业务分支的标准结果。
        return value.map(item => toUnderline(item))
    }
    // 返回当前业务分支的标准结果。
    return value
}
var toCamel = value => {
    if (value instanceof Map) {
        var result = {}
        for (keyName in value.keySet()) {
            result[StrUtil.toCamelCase(keyName + '')] = toCamel(value[keyName])
        }
        // 返回当前业务分支的标准结果。
        return result
    }
    if (value instanceof List) {
        // 返回当前业务分支的标准结果。
        return value.map(item => toCamel(item))
    }
    // 返回当前业务分支的标准结果。
    return value
}

var requestBody = toUnderline(body || {})
var action = requestBody.remove('action')
if (action == null || (action + '').trim() == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 400, 'action不能为空'
}
// 调用外部网关并统一处理返回结果与异常。
return toCamel(gatewayUtils.callGatewayApiForAny(action + '', requestBody))

{
  "properties": {},
  "id": "d7a7019f5e4a4a92a34543c1f5290007",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "07.API调用统计",
  "createTime": 1784450000000,
  "updateTime": 1784450000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/callStatistics",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "将页面筛选参数转换为网关参数，调用网关统计接口，再把返回数据转换为页面使用的字段结构。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.API调用统计
// 路由：POST /dws/flowserve/gateway/callStatistics
// 业务说明：将页面筛选参数转换为网关参数，调用网关统计接口，再把返回数据转换为页面使用的字段结构。
/*
 * API 调用统计列表
 *
 * Magic 负责页面参数到网关下划线参数的递归转换；
 * GatewayUtils 只执行签名后的网关调用。
 */
import cn.hutool.core.util.StrUtil
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils
import java.util.List
import java.util.Map

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
try {
    // 调用外部网关并统一处理返回结果与异常。
    var response = gatewayUtils.callGatewayApiForAny('V2CallStatistics', toUnderline(body || {}))
    if (response instanceof Map && response.result_code != null && response.result_code != '') {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 502, '网关调用统计返回异常: ' + response.result_code
    }
    // 返回当前业务分支的标准结果。
    return response
} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 502, '查询网关调用统计失败: ' + e.message
}

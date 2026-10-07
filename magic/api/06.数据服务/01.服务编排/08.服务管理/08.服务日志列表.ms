{
  "properties": {},
  "id": "d8a7019f5e4a4a92a34543c1f5290008",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "08.服务日志列表",
  "createTime": 1784450000000,
  "updateTime": 1784450000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/serviceLogList",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "将页面查询条件转换为网关参数，读取服务调用日志并转换字段格式；不修改本地资产。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.服务日志列表
// 路由：POST /dws/flowserve/gateway/serviceLogList
// 业务说明：将页面查询条件转换为网关参数，读取服务调用日志并转换字段格式；不修改本地资产。
/*
 * 服务日志列表
 *
 * 页面请求转下划线，网关响应转驼峰；只做查询，不修改本地资产。
 */
import cn.hutool.core.util.StrUtil
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils
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
try {
    // 调用外部网关并统一处理返回结果与异常。
    return convert(gatewayUtils.callGatewayApi('V2ServiceLogList', convert(body || {}, true)), false)
} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 502, '查询网关服务日志失败: ' + e.message
}

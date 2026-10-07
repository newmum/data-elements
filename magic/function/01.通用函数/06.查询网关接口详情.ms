{
  "properties": {},
  "id": "c2a7019f5e4a4a92a34543c1f5290016",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "06.查询网关接口详情",
  "createTime": 1784450000000,
  "updateTime": 1784450000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/gatewayApiDetail",
  "description": "按网关接口标识读取对外接口详情，组装协议和展示字段供资产页面使用。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "apiId",
      "value": null,
      "description": "网关 API ID",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "requestBody": "{\n}"
}
================================
// 共享函数：06.查询网关接口详情
// 函数路径：/common/gatewayApiDetail
// 业务说明：按网关接口标识读取对外接口详情，组装协议和展示字段供资产页面使用。
// 函数输入：apiId：网关 API ID。
import cn.hutool.core.util.StrUtil
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils
import http
import jsons
import java.util.List
import java.util.Map
import org.springframework.core.env.Environment as environment
import log

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

if (apiId == null || (apiId + '').trim() == '') {
    // 返回当前业务分支的标准结果。
    return null
}
// 调用外部网关并统一处理返回结果与异常。
var detail = toCamel(gatewayUtils.callGatewayApi('V2DescribeApi', {api_id: apiId}))
if (detail.errorCode == 'data_not_found') {
    // 返回当前业务分支的标准结果。
    return null
}
detail.serviceCreateType = 'api'
if (detail.serviceBuildId != null && detail.serviceBuildId != '') {
    detail.serviceCreateType = 'apiBuild'
    try {
        var response = http.connect(environment.getProperty('gateway.buildapi.url') + '/openapi/api/getServiceById')
            .contentType('application/json')
            .body(jsons.toJSONStr({id: detail.serviceBuildId}))
            .post()
        detail.buildApiInfo = toCamel(response.getBody()?.data)
    } catch (e) {
        log.warn('获取服务构建详情失败, serviceBuildId={}, message={}', detail.serviceBuildId, e.message)
    }
}
detail.serviceName = detail.name || ''
// 返回当前业务分支的标准结果。
return detail

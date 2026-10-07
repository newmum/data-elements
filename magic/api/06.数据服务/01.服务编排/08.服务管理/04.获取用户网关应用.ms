{
  "properties": {},
  "id": "d4a7019f5e4a4a92a34543c1f5290004",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "04.获取用户网关应用",
  "createTime": 1784450000000,
  "updateTime": 1784450000000,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/userAppInfo",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "Magic 负责当前用户识别、关联查询、首次创建和关系持久化；涉及 gateway_user_app_rela 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.获取用户网关应用
// 路由：POST /dws/flowserve/gateway/userAppInfo
// 业务说明：Magic 负责当前用户识别、关联查询、首次创建和关系持久化；涉及 gateway_user_app_rela 等业务表。
/*
 * 获取当前用户的网关应用
 *
 * Magic 负责当前用户识别、关联查询、首次创建和关系持久化；
 * GatewayUtils 仅负责签名后的网关调用。
 */
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import com.linewell.dataelement.integration.sso.service.SsoUserLookupService as userService
import com.linewell.dataelement.utils.GatewayUtils as gatewayUtils

var userId = StpUtil.getLoginId() + ''
if (userId == null || userId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 401, '未获取到登录用户信息'
}
var user = userService.getUserById(userId)
if (user == null) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 404, '当前用户不存在'
}
var applicationName = user.realName + '-' + userId
// 从 MySQL 业务表读取当前有效数据。
var relation = db.table('gateway_user_app_rela').where()
    .eq('user_id', userId).eq('is_del', 0).selectOne()
var applicationId = relation?.appId
if (applicationId == null || applicationId == '') {
    var created
    try {
        // 调用外部网关并统一处理返回结果与异常。
        created = gatewayUtils.callGatewayApi('V2CreateApplicationInfo', {
            application_name: applicationName
        })
    } catch (e) {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 502, '创建网关应用失败: ' + e.message
    }
    if (created.error_message != null && created.error_message != '') {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, created.error_message
    }
    applicationId = created.id
    if (applicationId == null || applicationId == '') {
        // 参数或业务状态不满足要求时立即终止请求。
        exit 500, '创建网关应用返回ID为空'
    }
    var now = new Date()
    // 将校验后的业务数据写入 MySQL 主表。
    db.table('gateway_user_app_rela').primary('tid').insert({
        tid: IdUtil.getSnowflakeNextIdStr(),
        app_id: applicationId,
        user_id: userId,
        is_del: 0,
        created_time: now,
        updated_time: now
    })
}

var result
try {
    // 调用外部网关并统一处理返回结果与异常。
    result = gatewayUtils.callGatewayApi('V2QueryApplicationInfo', {
        application_id: applicationId,
        application_name: applicationName
    })
} catch (e) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 502, '查询网关应用失败: ' + e.message
}
if (result.error_message != null && result.error_message != '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, result.error_message
}
// 返回当前业务分支的标准结果。
return result

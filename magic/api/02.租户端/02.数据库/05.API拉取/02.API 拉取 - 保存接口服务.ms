{
  "properties": {},
  "id": "api_pull_service_save_01",
  "script": null,
  "groupId": "tenant_magic_api_pull_20261003",
  "name": "02.API 拉取 - 保存接口服务",
  "path": "/api-pull/services/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "校验当前租户的数据源、服务名称/编码、Base URL 和认证引用，再保存接口服务；只存凭据引用，不存认证明文。"
}
================================
// 接口：保存 API 拉取服务
// 业务说明：校验当前租户的数据源、服务名称/编码、Base URL 和认证引用，再保存接口服务；只存凭据引用，不存认证明文。
import tenantRuntime
import apiPullConfig

var tenantId = tenantRuntime.id()
if (body?.datasourceId == null || ('' + body.datasourceId).trim() == '') { exit 400, '请选择数据源' }
if (body?.serviceName == null || ('' + body.serviceName).trim() == '') { exit 400, '请填写接口服务名称' }
var code = body?.serviceCode == null ? '' : ('' + body.serviceCode).trim().toLowerCase()
if (!code.matches('^[a-z][a-z0-9_-]{1,95}$')) { exit 400, '接口服务编码只能使用小写字母、数字、-、_' }
var baseUrl = body?.baseUrl == null ? '' : ('' + body.baseUrl).trim()
if (baseUrl == '') { exit 400, '请填写 Base URL' }
var authType = body?.authType == null ? 'NONE' : ('' + body.authType).trim().toUpperCase()
if (authType != 'NONE' && (body?.credentialRef == null || ('' + body.credentialRef).trim() == '')) {
    exit 400, '认证方式启用后必须填写凭据引用'
}

// URL 主机、协议、敏感请求头和目标租户归属由 Java 安全边界再次验证。
return apiPullConfig.saveService(body)

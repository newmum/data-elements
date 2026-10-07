{
  "properties": {},
  "id": "ga_ztgw_config_01",
  "script": null,
  "groupId": "ga_ztgw_group_02",
  "name": "01.网关配置检查",
  "createTime": 1789560000000,
  "updateTime": 1789608368078,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/config-check",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "零信任网关调试配置检查。配置暂时写在脚本顶部的变量中，不读取系统配置、Redis 或环境变量。GET /ga/gateway/config-check。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.网关配置检查
// 路由：GET /gateway/config-check
// 业务说明：零信任网关调试配置检查。配置暂时写在脚本顶部的变量中，不读取系统配置、Redis 或环境变量。GET /ga/gateway/config-check。
// 零信任网关调试配置检查。
// 调试阶段：所有待调参数集中放在 gatewayConfig；不读取 sym_config_t、Redis 或服务端环境变量。
// 投产前：将这些变量迁移到部署配置，并把 clientSecret 从脚本中移除。
import tenantRuntime

var gatewayConfig = {
  // OAuth2 Token 完整地址，例如 https://<gateway-host>/sso/oauth2/token
  tokenUrl: '',
  // 服务网关已挂载的统一推送完整地址，例如 https://<gateway-host>/api/v1/data-push
  pushUrl: '',
  // 仅用于本地联调。请自行填入网关分配的值，禁止提交真实生产密钥。
  clientId: '__PRIVATE_GATEWAY_CLIENT_ID__',
  clientSecret: '__PRIVATE_GATEWAY_CLIENT_SECRET__',
  tokenRefreshAheadSeconds: 300,
  // 调试时固定填写；正式接入时可按调用人上下文动态补充。
  requesterIp: '127.0.0.1',
  auditDefaults: {
    Reg_Role: '002',
    Data_Usage: '001',
    User_Name: '',
    User_Id_Card: '',
    User_Type: '001',
    User_Dept: '',
    User_Terminal_Network: '001',
    User_Terminal_Type: '001',
    Requester_Organization: '',
    Requester_Organization_ID: '',
    Requester_Name: '数据元素平台',
    Requester_ID: '',
    Service_No: '',
    Service_Name: '统一数据推送服务',
    Final_Organization_Name: '',
    Final_Organization_ID: '',
    Final_Organization_Sys_Id: '',
    Final_Organization_Sys_Name: ''
  }
}

var tenantId = tenantRuntime.id()
var missing = []
if ((gatewayConfig.tokenUrl || '').trim() == '') { missing.add('gatewayConfig.tokenUrl') }
if ((gatewayConfig.pushUrl || '').trim() == '') { missing.add('gatewayConfig.pushUrl') }
if ((gatewayConfig.clientId || '').trim() == '') { missing.add('gatewayConfig.clientId') }
if ((gatewayConfig.clientSecret || '').trim() == '') { missing.add('gatewayConfig.clientSecret') }

var requiredAuditFields = ['Reg_Role', 'Data_Usage', 'User_Name', 'User_Id_Card', 'User_Type', 'User_Dept', 'User_Terminal_Network', 'User_Terminal_Type', 'Requester_Organization', 'Requester_Organization_ID', 'Requester_Name', 'Requester_ID', 'Service_No', 'Service_Name', 'Final_Organization_Name', 'Final_Organization_ID', 'Final_Organization_Sys_Id', 'Final_Organization_Sys_Name']
var missingAuditFields = []
for (field in requiredAuditFields) {
  if (gatewayConfig.auditDefaults[field] == null || (gatewayConfig.auditDefaults[field] + '').trim() == '') { missingAuditFields.add(field) }
}

return {
  tenantId: tenantId,
  configured: missing.size() == 0 && missingAuditFields.size() == 0,
  mode: '脚本内调试变量',
  missingVariables: missing,
  missingAuditFields: missingAuditFields,
  tokenCacheEnabled: false,
  nextStep: '填写 gatewayConfig 后，调用 POST /ga/gateway/token-status，先传 {"dryRun":true} 检查请求构造。'
}

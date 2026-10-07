{
  "properties": {},
  "id": "ga_ztgw_push_03",
  "script": null,
  "groupId": "ga_ztgw_group_02",
  "name": "03.统一数据推送",
  "createTime": 1789560000000,
  "updateTime": 1789560000000,
  "path": "/data-push",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [
    {
      "name": "X-Api-Key",
      "required": true,
      "dataType": "String",
      "description": "登记数据源签发的业务调用密钥；透传至统一数据推送服务。"
    },
    {
      "name": "Idempotency-Key",
      "required": true,
      "dataType": "String",
      "description": "表批次幂等标识；透传至统一数据推送服务。"
    }
  ],
  "paths": [],
  "description": "零信任网关统一数据推送调试代理。支持 dryRun；调试变量写在脚本顶部，不读取系统配置、Redis 或环境变量。POST /ga/gateway/data-push。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.统一数据推送
// 路由：POST /gateway/data-push
// 业务说明：零信任网关统一数据推送调试代理。支持 dryRun；调试变量写在脚本顶部，不读取系统配置、Redis 或环境变量。POST /ga/gateway/data-push。
// 零信任网关统一数据推送调试代理。
// 调试阶段：所有配置写在 gatewayConfig；每次真实调用都申请一个 Token，不依赖 Redis。
// 正式投产前：把 gatewayConfig 迁移到受控配置，clientSecret 迁移到服务端密钥，并启用 Token 缓存。
import tenantRuntime
import cn.hutool.core.codec.Base64
import cn.hutool.core.date.DateUtil
import cn.hutool.core.util.IdUtil
import cn.hutool.core.util.RandomUtil
import http
import request

var gatewayConfig = {
  tokenUrl: '',
  pushUrl: '',
  clientId: '',
  clientSecret: '',
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
if (body == null) { exit 400, '请求体不能为空' }
var dryRun = body.dryRun == true

var requiredBusinessFields = ['deliveryType', 'datasourceId', 'tableName', 'records']
var missingBusiness = []
for (field in requiredBusinessFields) {
  if (body[field] == null || (field != 'records' && (body[field] + '').trim() == '')) { missingBusiness.add(field) }
}
if (missingBusiness.size() > 0) { exit 400, '统一数据推送请求缺少字段：' + missingBusiness.join(', ') }
if (body.records == null || body.records.size() == 0) { exit 400, 'records 至少需要一条数据' }

var apiKeyValues = request.getHeaders('X-Api-Key')
var idempotencyKeyValues = request.getHeaders('Idempotency-Key')
var apiKey = apiKeyValues == null || apiKeyValues.size() == 0 ? '' : (apiKeyValues[0] + '').trim()
var idempotencyKey = idempotencyKeyValues == null || idempotencyKeyValues.size() == 0 ? '' : (idempotencyKeyValues[0] + '').trim()
var missingHeaders = []
if (apiKey == '') { missingHeaders.add('X-Api-Key') }
if (idempotencyKey == '') { missingHeaders.add('Idempotency-Key') }
if (missingHeaders.size() > 0) { exit 400, '请求头必填：' + missingHeaders.join(', ') }

var missingConfig = []
if ((gatewayConfig.tokenUrl || '').trim() == '') { missingConfig.add('gatewayConfig.tokenUrl') }
if ((gatewayConfig.pushUrl || '').trim() == '') { missingConfig.add('gatewayConfig.pushUrl') }
if ((gatewayConfig.clientId || '').trim() == '') { missingConfig.add('gatewayConfig.clientId') }
if ((gatewayConfig.clientSecret || '').trim() == '') { missingConfig.add('gatewayConfig.clientSecret') }

var payload = {}
body.each((key, value) => { if (key != 'gatewayAudit' && key != 'dryRun') { payload[key] = value } })
gatewayConfig.auditDefaults.each((key, value) => { payload[key] = value })

var inputAudit = body.gatewayAudit == null ? {} : body.gatewayAudit
var allowedAuditFields = ['User_Name', 'User_Id_Card', 'User_Cert_Id', 'User_Type', 'User_Dept', 'User_Terminal_Network', 'User_Terminal_Mac', 'User_Mobile', 'User_Terminal_Type', 'User_Phone_IMEI', 'Query_Resume', 'Interface_Condition']
for (field in allowedAuditFields) {
  if (inputAudit[field] != null && (inputAudit[field] + '').trim() != '') { payload[field] = inputAudit[field] }
}

var requestId = `${DateUtil.current()}${RandomUtil.randomString(6)}`
payload.Num_ID = IdUtil.getSnowflakeNextIdStr()
payload.Request_ID = requestId
payload.Requester_IP = gatewayConfig.requesterIp
payload.Query_Datetime = DateUtil.now()
payload.Request_Type = '003'
payload.Record_Type = 'interface'
payload.Log_Seq_Id = requestId
if (payload.Interface_Condition == null || (payload.Interface_Condition + '').trim() == '') {
  payload.Interface_Condition = `datasourceId=${body.datasourceId};tableName=${body.tableName};records=${body.records.size()}`
}

var requiredAuditFields = ['Reg_Role', 'Data_Usage', 'User_Name', 'User_Id_Card', 'User_Type', 'User_Dept', 'User_Terminal_Network', 'User_Terminal_Type', 'Requester_Organization', 'Requester_Organization_ID', 'Requester_Name', 'Requester_ID', 'Service_No', 'Service_Name', 'Final_Organization_Name', 'Final_Organization_ID', 'Final_Organization_Sys_Id', 'Final_Organization_Sys_Name', 'Query_Resume']
var missingAudit = []
for (field in requiredAuditFields) {
  if (payload[field] == null || (payload[field] + '').trim() == '') { missingAudit.add(field) }
}

if (dryRun) {
  return {
    tenantId: tenantId,
    dryRun: true,
    businessValid: true,
    headerValid: true,
    configValid: missingConfig.size() == 0,
    auditValid: missingAudit.size() == 0,
    missingVariables: missingConfig,
    missingAuditFields: missingAudit,
    requestId: requestId,
    outbound: {
      method: 'POST',
      urlConfigured: (gatewayConfig.pushUrl || '').trim() != '',
      authorizationScheme: 'Bearer access_token（不返回）',
      headersForwarded: ['X-Api-Key', 'Idempotency-Key'],
      recordCount: body.records.size()
    }
  }
}

if (missingConfig.size() > 0) { exit 503, '零信任网关调试变量未填写：' + missingConfig.join(', ') }
if (missingAudit.size() > 0) { exit 400, '零信任网关审计字段未填写：' + missingAudit.join(', ') }

var separator = gatewayConfig.tokenUrl.indexOf('?') >= 0 ? '&' : '?'
var tokenResponse = null
try {
  tokenResponse = http.connect(`${gatewayConfig.tokenUrl}${separator}grant_type=client_credentials`)
    .contentType('application/json;charset=utf-8')
    .header('Authorization', 'Basic ' + Base64.encode(`${gatewayConfig.clientId}:${gatewayConfig.clientSecret}`))
    .post()
    .getBody()
} catch (error) {
  exit 502, '零信任认证服务请求失败：' + (error.message || error)
}

var accessToken = tokenResponse == null ? '' : (tokenResponse.access_token || '').trim()
if (accessToken == '') {
  var tokenMessage = tokenResponse == null ? '认证服务未返回响应' : (tokenResponse.message || tokenResponse.msg || '认证服务未返回 access_token')
  exit 502, '零信任认证失败：' + tokenMessage
}

var gatewayResponse = null
try {
  gatewayResponse = http.connect(gatewayConfig.pushUrl)
    .contentType('application/json;charset=utf-8')
    .header('Authorization', 'Bearer ' + accessToken)
    .header('X-Api-Key', apiKey)
    .header('Idempotency-Key', idempotencyKey)
    .body(payload)
    .post()
    .getBody()
} catch (error) {
  exit 502, '统一数据推送网关请求失败：' + (error.message || error)
}

return {
  tenantId: tenantId,
  requestId: requestId,
  deliveryType: body.deliveryType,
  tableName: body.tableName,
  recordCount: body.records.size(),
  downstream: gatewayResponse
}

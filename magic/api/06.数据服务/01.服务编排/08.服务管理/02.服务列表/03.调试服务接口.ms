{
  "properties": {},
  "id": "c7a2c918325a452c84ac391e3e2c6a11",
  "script": null,
  "groupId": "45b082c7d94f4ff08e3b9bf367521723",
  "name": "03.调试服务接口",
  "path": "/debug",
  "method": "POST",
  "description": "按当前租户及角色范围调试服务登记草稿。执行一次HTTP请求并返回脱敏结果。",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceHttp' as serviceHttp
import '@/common/serviceHttpPreview' as preview
import '@post:/sym/user/me' as me
import tenantRuntime
import dataScope
import java.util.Map

// 接口：调试登记中的服务接口
// 路由：POST /dws/flowserve/gateway/api/debug
var tenantId = tenantRuntime.id()
var user = me()
if (tenantId == null || tenantId == '' || user?.orgId == null || user.orgId == '') { exit 403,'当前账号无权调试服务' }
if (!['ALL','ORG','OWNER'].contains(dataScope.dataSource().scope)) { exit 403,'当前角色无权调试服务' }
if (!(body instanceof Map)) { exit 400,'请求配置必须为对象' }
var apiId = (body.apiId || '') + ''
var datasourceId = (body.datasourceId || '') + ''
if (apiId.length() > 32 || datasourceId.length() > 32) { exit 400,'服务或数据源标识无效' }
if (apiId != '') {
    var service = db.table('api_info_t').where().eq('tenant_id',tenantId).eq('tid',apiId).eq('is_del',0).selectOne()
    if (service == null || !dataScope.canReadDataSource(service)) { exit 404,'服务不存在或不在当前数据范围内' }
    datasourceId = (service.datasource_id || service.datasourceId || '') + ''
}
if (datasourceId != '') {
    var source = db.table('db_datasource_t').where().eq('tenant_id',tenantId).eq('tid',datasourceId).eq('is_del',0).selectOne()
    if (source == null || !dataScope.canReadDataSourceFor('MENU:2081000000000000002',source)) {
        exit 404,'数据源不存在或不在当前数据范围内'
    }
}
var headers = body.headers == null ? {} : body.headers
if (!(headers instanceof Map)) { exit 400,'请求头必须为对象' }
var credentialHeader = (body.credentialHeader || '') + ''
var credential = (body.credential || '') + ''
if ((credentialHeader == '') != (credential == '')) { exit 400,'请同时填写认证请求头和值' }
if (credentialHeader != '') { headers[credentialHeader] = credential }
var options = {address:body.address,method:body.method,query:body.query,headers:headers,payload:body.payload,credential:credential}
return preview({response:serviceHttp(options),request:options})

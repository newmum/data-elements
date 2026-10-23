{
  "properties": {},
  "id": "d5a7019f5e4a4a92a34543c1f5290005",
  "script": null,
  "groupId": "2baeb325734a4f348df335661b205952",
  "name": "05.保存网关服务",
  "createTime": 1784450000000,
  "updateTime": 1787153758086,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/save",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"id\": \"2361887157562377213\",\n  \"name\": \"获取人员审核列表\",\n  \"version\": \"1.0\",\n  \"providerConfig\": {\"protocol\": \"HTTP\", \"address\": \"/userPro/planList\", \"httpMethod\": \"POST\"},\n  \"publishConfig\": {\"publishAddress\": \"/userPro/planList\"},\n  \"publishParams\": [{\"paramName\": \"userId\", \"paramType\": \"string\", \"paramPosition\": \"query\", \"required\": \"1\"}]\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"id\": \"2361887157562377213\",\n  \"message\": \"保存成功\"\n}",
  "description": "保存服务编排服务（POST /dws/flowserve/gateway/save）。写入 api_info_t 服务主表和 api_param_t 参数表；如请求包含 apiFileId/fileContent，则同步 api_file_t.api_id 关联服务主表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime
import dataScope
import "@post:/sym/user/me" as me
import magicResourceLink
import cn.hutool.core.util.IdUtil
import cn.hutool.json.JSONUtil
import java.util.Map
import java.util.List

var text=value=>value==null?'':value+''
var blank=value=>text(value).trim()==''
var json=value=>value==null?null:JSONUtil.toJsonStr(value)
var tenantId=tenantRuntime.id()
var user=me()
var userId=text(user?.userId || user?.id || user?.tid)
var userOrgId=text(user?.orgId)
if (blank(tenantId) || blank(userId)) { exit 401,'当前登录身份无效，请重新登录后登记服务' }
if (!(body instanceof Map)) { exit 400,'服务登记内容必须为对象' }
var apiId=text(body.id || body.apiId || body.tid).trim()
var creating=blank(apiId)
var name=text(body.serviceName || body.name).trim()
if (blank(name) || name.length()>255) { exit 400,'请填写255个字符以内的服务名称' }
var old=creating?null:db.table('api_info_t').where().eq('tenant_id',tenantId).eq('tid',apiId).eq('is_del',0).selectOne()
if (!creating && old==null) { exit 404,'服务不存在或无权访问' }
if (old!=null && !dataScope.canReadDataSource(old)) { exit 403,'当前角色无权修改此服务' }
if (creating && blank(userOrgId)) { exit 403,'当前账号未关联部门，请先完善部门信息' }
if (creating) { apiId=IdUtil.getSnowflakeNextIdStr() }
var provider=body.providerConfig==null?{}:body.providerConfig
var publish=body.publishConfig==null?{}:body.publishConfig
var ext=body.extendInfo==null?{}:body.extendInfo
var params=body.publishParams || body.params || body.paramList || []
if (!(provider instanceof Map) || !(publish instanceof Map) || !(ext instanceof Map) || !(params instanceof List)) { exit 400,'服务配置或参数格式不正确' }
if (params.size()>500 || ext.size()>64 || JSONUtil.toJsonStr(body).length()>1048576) { exit 413,'服务登记最多500个参数、64项扩展配置及1MiB内容' }
var method=text(body.method || body.httpMethod || provider.httpMethod || publish.publishMethod || 'GET').toUpperCase()
if (!['GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS'].contains(method)) { exit 400,'请选择有效的HTTP请求方式' }
var uri=text(body.uri || body.address || provider.address).trim()
var publishAddress=text(body.publishAddress || publish.publishAddress || uri).trim()
if (blank(uri) || uri.length()>1000 || publishAddress.length()>1000) { exit 400,'请填写1000个字符以内的服务地址和发布地址' }
if (!uri.matches('^(?i)https?://[^\\s]+$') && !uri.startsWith('/')) { exit 400,'服务地址须为HTTP(S)地址或已有服务相对路径' }
var orgId=creating?userOrgId:text(old.orgId || old.org_id)
var now=new Date()
var groupId=text(body.groupId || ext.groupId || old?.groupId || old?.group_id || 'api_service_group_default')
if (groupId!='api_service_group_default' && db.table('sym_dict_t').where().eq('tenant_id',tenantId).eq('tid',groupId).eq('biz_type','apiServiceGroup').eq('is_del',0).selectOne()==null) { exit 400,'服务分组不存在或无权使用' }
ext.orgId=orgId
var seen={}
for (item in params) {
    if (!(item instanceof Map) || blank(item.paramName || item.param_name) || text(item.paramName || item.param_name).length()>255) { exit 400,'每个参数均须填写255个字符以内的名称' }
    var paramScope=item.res==1 || item.paramScope=='response'?'response':'request'
    var key=paramScope+':'+text(item.paramPosition || item.param_position)+':'+text(item.paramName || item.param_name)
    if (seen[key]==true) { exit 400,'相同位置的参数名称不能重复' }
    seen[key]=true
}
// 扩展配置一次预取；循环只组装与写入，不逐字段回查。
var oldProps=creating?[]:db.table('da_prop_t').where().eq('tenant_id',tenantId).eq('parent_id',apiId).eq('data_type','api').eq('is_del',0).select()
var propsByName={}
for (prop in oldProps) { propsByName[text(prop.propName || prop.prop_name)]=prop }
var row={tid:apiId,resource_id:body.resourceId || old?.resourceId || old?.resource_id || apiId,
    service_name:name,service_code:body.serviceCode || body.code || old?.serviceCode || old?.service_code || apiId,
    service_type:body.serviceType || body.type || old?.serviceType || old?.service_type || '数据服务',
    service_desc:body.serviceDesc || body.description || ext.apiDescription || '',uri:uri,method:method,
    service_sql:body.serviceSql || body.sql || old?.serviceSql || old?.service_sql || '',
    is_use:body.isUse==null?(old?.isUse==null?(old?.is_use==null?1:old.is_use):old.isUse):body.isUse,
    group_id:groupId,version:body.version || old?.version || '1.0',status:body.status || old?.status || 'draft',
    protocol:body.protocol || provider.protocol || 'HTTP',address:provider.address || uri,
    publish_address:publishAddress,publish_address_full:publishAddress,provider_config:json(provider),publish_config:json(publish),
    labels:body.labels || '',return_type:ext.returnType || body.returnType || '',share_type:ext.shareType || body.shareType || '',
    data_domain:ext.dataDomain || '',data_category:ext.dataCategory || '',data_level:ext.dataLevel || '',
    catalog_id:ext.catalogId || body.catalogId || '',org_id:orgId,source_business_name:ext.sourceBusinessName || '',
    source_business_item:ext.sourceBusinessItem || '',request_example:ext.requestExample || '',response_example:ext.responseExample || '',
    service_create_type:body.serviceCreateType || old?.serviceCreateType || old?.service_create_type || 'api',
    flow_id:body.flowId || old?.flowId || old?.flow_id || '',tenant_id:tenantId,updated_time:now,updated_by:userId}
db.transaction(()=>{
    if (creating) { row.created_time=now;row.created_by=userId;row.is_del=0;db.table('api_info_t').primary('tid').insert(row) }
    else { db.table('api_info_t').where().eq('tenant_id',tenantId).eq('tid',apiId).eq('is_del',0).update(row) }
    for (key in ext.keySet()) {
        var value=ext[key]
        var existing=propsByName[text(key)]
        var prop={parent_id:apiId,tenant_id:tenantId,prop_name:key,prop_value:value==null?null:((value instanceof Map || value instanceof List)?json(value):text(value)),prop_type:'0',data_type:'api',is_del:0,updated_time:now}
        if (existing==null) { prop.tid=IdUtil.getSnowflakeNextIdStr();prop.created_time=now;db.table('da_prop_t').primary('tid').insert(prop) }
        else { db.table('da_prop_t').where().eq('tenant_id',tenantId).eq('tid',existing.tid).update(prop) }
    }
    db.table('api_param_t').where().eq('tenant_id',tenantId).eq('api_id',apiId).eq('is_del',0).update({is_del:1,updated_time:now})
    for (index,item in params) {
        db.table('api_param_t').primary('tid').insert({tid:IdUtil.getSnowflakeNextIdStr(),api_id:apiId,
            param_name:item.paramName || item.param_name,param_desc:item.description || item.paramDesc || '',param_type:item.paramType || item.param_type || '',
            param_position:item.paramPosition || item.param_position || '',required_flag:item.required==true || text(item.required)=='1'?'1':'0',
            default_value:item.defaultValue || '',showcase_value:item.showcaseValue || '',param_scope:item.res==1 || item.paramScope=='response'?'response':'request',
            sort_no:index+1,created_time:now,tenant_id:tenantId,updated_time:now,is_del:0})
    }
})
if (!blank(body.apiFileId)) { magicResourceLink.link(text(body.apiFileId),apiId) }
return {id:apiId,tid:apiId,orgId:orgId,status:row.status,message:'服务登记已保存'}

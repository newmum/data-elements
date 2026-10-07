{
  "properties": {},
  "id": "707c5e79a9a23ce1a96b0a3f84bb058e",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "13.后台运行状态",
  "path": "/admin/performance",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "智能搜索中心运行状态：以达梦 linewell_ods 的实时数据为准。"
}
================================
// 接口：13.后台运行状态
// 路由：GET /search/admin/performance
// 业务说明：智能搜索中心运行状态：以达梦 linewell_ods 的实时数据为准。
import tenantRuntime
import flowDataSource
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var dramas=ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_DRAMA d on d.ID=e.ID
where lower(e.entity_type)='drama'
    and e.publication_status='PUBLISHED'
""")
var orgs=ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_INSTITUTION i on i.ID=e.ID
where lower(e.entity_type) in ('org','institution')
    and e.publication_status='PUBLISHED'
""")
return {portal:{readSource:'达梦 linewell_ods 实时查询',revision:'当前数据库数据',generation:'不使用静态演示投影',requestCount:'未启用请求计数',pageCacheHits:'未启用页面缓存',pageCacheCapacity:0,manifestCacheSeconds:0,sqlBusinessReadsPerPortalRequest:'按页面数据范围查询'},projection:{running:false,lastSuccess:'数据库连接正常：剧目 '+dramas+' 条，机构 '+orgs+' 家',lastError:'',lastResult:{source:'linewell_ods',dramaCount:dramas,orgCount:orgs,mode:'实时数据库读取'}}}

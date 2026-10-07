{
  "properties": {},
  "id": "1ec65b448e683ecfb8829d9fa84c9392",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "10.后台概览",
  "path": "/admin/dashboard",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "智能搜索中心后台工作台：直接读取达梦 linewell_ods。"
}
================================
// 接口：10.后台概览
// 路由：GET /search/admin/dashboard
// 业务说明：智能搜索中心后台工作台：直接读取达梦 linewell_ods。
import tenantRuntime
import flowDataSource
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var orgRows=ods.select("""
select lower(i.org_type) org_type,count(1) total
from JC_ENTITY e
join JC_INSTITUTION i on i.ID=e.ID
where lower(e.entity_type) in ('org','institution')
    and e.publication_status='PUBLISHED'
group by lower(i.org_type)
""")
var orgCounts={production:0,transmission:0,broadcast:0,social:0};for(row in orgRows){if(row.orgType!=null){orgCounts[row.orgType]=row.total}}
var orgCount=ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_INSTITUTION i on i.ID=e.ID
where lower(e.entity_type) in ('org','institution')
    and e.publication_status='PUBLISHED'
""")
var dramaCount=ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_DRAMA d on d.ID=e.ID
where lower(e.entity_type)='drama'
    and e.publication_status='PUBLISHED'
""")
var awardRecordCount=ods.selectInt("""
select count(1)
from JC_AWARD_RECORD
where publication_status='PUBLISHED'
""")
var relationCount=ods.selectInt("""
select count(1)
from JC_DRAMA_ORG
""")+ods.selectInt("""
select count(1)
from JC_AWARD_RECORD
""")
return {engine:'DAMENG_LINEWELL_ODS',stats:{orgCount:orgCount,dramaCount:dramaCount,awardRecordCount:awardRecordCount,relationCount:relationCount,orgCounts:orgCounts},pendingReviews:0,drafts:0,sync:{indexed_security_epoch:'数据库实时读取',required_security_epoch:'达梦 linewell_ods'},lastProjectionError:'',recentChanges:[]}

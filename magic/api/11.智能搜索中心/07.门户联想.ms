{
  "properties": {},
  "id": "a153768b92a93407984c700348294a51",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "07.门户联想",
  "path": "/portal-suggestions",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "读取达梦业务库的门户搜索联想。"
}
================================
// 接口：07.门户联想
// 路由：GET /search/portal-suggestions
// 业务说明：读取达梦业务库的门户搜索联想。
import flowDataSource
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var keyword=(q==null?'':q).trim()
if(keyword==''){return {items:[]}}
var category=category==null?'drama':category
var type=category=='org'?'org':category=='award'?'award':'drama'
var rows=ods.select("""
select id,name,short_name
from JC_ENTITY
where lower(entity_type)=#{type}
    and publication_status='PUBLISHED'
    and lower(name) like lower('%'||#{keyword}||'%') fetch first 5 rows only
""")
for(row in rows){row.entityType=type;row.title=row.name;row.shortName=row.shortName==null?row.name:row.shortName}
return {items:rows}

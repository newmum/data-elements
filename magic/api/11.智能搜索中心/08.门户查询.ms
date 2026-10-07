{
  "properties": {},
  "id": "db78c52fd4b93c8b912260e3219b3dbb",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "08.门户查询",
  "createTime": null,
  "updateTime": 1789857609185,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/query",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "按原门户查询契约从当前租户 Elasticsearch 投影读取；索引未就绪时明确提示执行全量重建。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.门户查询
// 路由：POST /search/query
// 业务说明：按原门户查询契约从当前租户 Elasticsearch 投影读取；索引未就绪时明确提示执行全量重建。
import tenantRuntime
import searchEs
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
if(tenantId==null||(''+tenantId).trim()==''){throw new IllegalStateException('登录会话缺少租户信息，请重新登录')}
var tenantKey=(''+tenantId).toLowerCase()
var catalogRead='jcc-search-catalog-'+tenantKey+'-read'
if(!searchEs.exists(catalogRead)){throw new IllegalStateException('搜索索引尚未就绪，请先执行“搜索索引全量重建”')}
var request=body==null?{}:body
var category=request.category==null?'drama':request.category
var type=category=='org'?'org':category=='award'?'award':'drama'
var keyword=request.q==null?'':request.q.trim()
var pageSize=request.pageSize==null?40:Integer.parseInt(''+request.pageSize)
if(pageSize<1){pageSize=1};if(pageSize>100){pageSize=100}
var offset=request.cursor==null||request.cursor==''?0:Integer.parseInt(''+request.cursor)
if(offset<0){offset=0}
var boolQuery={filter:[{term:{entityType:type}}]}
if(keyword!=''){boolQuery.must=[{multi_match:{query:keyword,fields:['name^3','title^3','shortName^2','synopsis','description'],type:'best_fields'}}]}
var result=searchEs.search(catalogRead,{from:offset,size:pageSize,track_total_hits:true,query:{bool:boolQuery}})
var hits=result.hits==null?[]:result.hits.hits
var items=[]
for(hit in hits){var item=hit._source;item.entityType=type;item.title=item.title==null||item.title==''?item.name:item.title;item.shortName=item.shortName==null||item.shortName==''?item.name:item.shortName;items.add(item)}
var totalRaw=result.hits==null?0:result.hits.total
var total=totalRaw instanceof Map?totalRaw.value:totalRaw
var hasNext=offset+items.size()<total
return {items:items,total:total,facets:{},facetLabels:{},hasNext:hasNext,nextCursor:hasNext?''+(offset+items.size()):null,snapshot:{institutions:type=='org'?items:[],dramas:type=='drama'?items:[],awards:type=='award'?items:[],stats:{source:'elasticsearch',tenantId:tenantId,index:catalogRead}}}

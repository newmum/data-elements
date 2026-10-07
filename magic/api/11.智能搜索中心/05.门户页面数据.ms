{
  "properties": {},
  "id": "a6d5af9c71f53a8382fb954d5da47d26",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "05.门户页面数据",
  "createTime": null,
  "updateTime": 1789857884530,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/portal-view",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "兼容原门户页面数据契约，从当前租户 Elasticsearch 门户读模型读取；达梦 linewell_ods 是其唯一投影来源。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.门户页面数据
// 路由：GET /search/portal-view
// 业务说明：兼容原门户页面数据契约，从当前租户 Elasticsearch 门户读模型读取；达梦 linewell_ods 是其唯一投影来源。
import tenantRuntime
import searchEs
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
if(tenantId==null||(''+tenantId).trim()==''){throw new IllegalStateException('登录会话缺少租户信息，请重新登录')}
var tenantKey=(''+tenantId).toLowerCase()
var portalRead='jcc-search-portal-'+tenantKey+'-read'
if(!searchEs.exists(portalRead)){throw new IllegalStateException('门户搜索索引尚未就绪，请先执行“搜索索引全量重建”')}
var response=searchEs.search(portalRead,{size:10000,track_total_hits:true,query:{match_all:{}}})
var hits=response.hits==null?[]:response.hits.hits
var institutions=[];var dramas=[];var awards=[];var records=[]
for(hit in hits){
 var doc=hit._source
 if(doc.entityType=='org'){
  var orgType=doc.orgType==null?'social':(''+doc.orgType).toLowerCase();if(['production','transmission','broadcast','social'].indexOf(orgType)<0){orgType='social'}
  institutions.add({id:doc.id,name:doc.name==null?'':doc.name,shortName:doc.shortName==null||doc.shortName==''?doc.name:doc.shortName,entityType:'org',type:orgType,level:doc.level==null?'待确认':doc.level,establishedYear:doc.establishedYear==null?0:doc.establishedYear,legalPerson:doc.legalPerson==null?'':doc.legalPerson,creditCode:doc.creditCode==null?'':doc.creditCode,scope:doc.scope==null?'':doc.scope,region:doc.region==null?'':doc.region,tags:doc.tags==null?[]:doc.tags,dataQuality:0,activeYears:doc.activeYears==null?0:doc.activeYears,transmissionMode:[],updatedAt:doc.updatedAt})
 }else if(doc.entityType=='drama'){
  var links=doc.awardLinks==null?[]:doc.awardLinks;for(link in links){records.add(link)}
  dramas.add({id:doc.id,title:doc.title==null||doc.title==''?doc.name:doc.title,entityType:'drama',genre:doc.genre==null?'':doc.genre,episodes:doc.episodes==null?0:doc.episodes,year:doc.year==null?0:doc.year,region:doc.region==null?'':doc.region,status:doc.status==null?'待播':doc.status,synopsis:doc.synopsis==null?'':doc.synopsis,tags:doc.tags==null?[]:doc.tags,doubanScore:0,rating:0,productionOrgIds:doc.productionOrgIds==null?[]:doc.productionOrgIds,broadcastOrgIds:doc.broadcastOrgIds==null?[]:doc.broadcastOrgIds,transmissionOrgIds:doc.transmissionOrgIds==null?[]:doc.transmissionOrgIds,awards:links,credits:[],license:{distributionNo:'',filingNo:'',filingDate:'',productionNo:'',copyright:doc.copyright==null?'':doc.copyright,duration:doc.duration==null?'':doc.duration,verified:false},dataQuality:0,updatedAt:doc.updatedAt})
 }else if(doc.entityType=='award'){
  var hosts=doc.hostOrgIds==null?[]:doc.hostOrgIds
  awards.add({id:doc.id,name:doc.name==null?'':doc.name,shortName:doc.shortName==null||doc.shortName==''?doc.name:doc.shortName,entityType:'award',level:doc.level==null?'行业级':doc.level,hostOrgId:hosts.size()==0?'':hosts[0],hostOrgIds:hosts,founded:doc.founded==null?0:doc.founded,cycle:doc.cycle==null?'':doc.cycle,desc:doc.description==null?'':doc.description,region:doc.region==null?'':doc.region,dataQuality:0,updatedAt:doc.updatedAt})
 }
}
var stats={dramaCount:dramas.size(),orgCount:institutions.size(),awardCount:awards.size(),reviewRecordCount:records.size(),wonRecordCount:records.filter(item=>item.result=='获奖').size(),orgCounts:{production:institutions.filter(item=>item.type=='production').size(),transmission:institutions.filter(item=>item.type=='transmission').size(),broadcast:institutions.filter(item=>item.type=='broadcast').size(),social:institutions.filter(item=>item.type=='social').size()},source:'elasticsearch',tenantId:tenantId,index:portalRead}
var libraries={};for(id in ['production','transmission','broadcast','social','drama','award']){libraries[id]={id:id,title:id=='drama'?'剧目档案库':id=='award'?'评审评奖库':'机构资料库',desc:'Elasticsearch 门户读模型，来源为达梦 linewell_ods',coverage:'原业务库',updatedAt:new Date()+'',recordLabel:id=='drama'?'剧目':id=='award'?'奖项':'机构',recordCount:id=='drama'?dramas.size():id=='award'?awards.size():institutions.filter(item=>item.type==id).size(),tables:[]}}
var hotOrgs=institutions.subList(0,institutions.size()>8?8:institutions.size());var hotDramas=dramas.subList(0,dramas.size()>8?8:dramas.size());var hotAwards=awards.subList(0,awards.size()>8?8:awards.size())
return {snapshot:{institutions:institutions,dramas:dramas,awards:awards,stats:stats},home:{hot:{all:[],org:hotOrgs.map(item=>item.name),drama:hotDramas.map(item=>item.title),award:hotAwards.map(item=>item.name)},changes:[]},graphs:{},libraries:libraries,analysis:{recordCount:records.size(),records:records}}

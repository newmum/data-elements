{
  "properties": {},
  "id": "search_es_index_rebuild_01",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "18.搜索索引全量重建",
  "createTime": 1789857473761,
  "updateTime": 1789857884468,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/admin/index-rebuild",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "从达梦 linewell_ods 全量重建当前租户智能搜索 ES 投影，校验成功后原子切换读取与写入别名；使用请求参数 dryRun。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：18.搜索索引全量重建
// 路由：POST /search/admin/index-rebuild
// 业务说明：从达梦 linewell_ods 全量重建当前租户智能搜索 ES 投影，校验成功后原子切换读取与写入别名；使用请求参数 dryRun。
import tenantRuntime
import flowDataSource
import searchEs
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
if(tenantId==null||(''+tenantId).trim()==''){throw new IllegalStateException('登录会话缺少租户信息，请重新登录')}
var tenantKey=(''+tenantId).toLowerCase()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var catalogRead='jcc-search-catalog-'+tenantKey+'-read'
var catalogWrite='jcc-search-catalog-'+tenantKey+'-write'
var portalRead='jcc-search-portal-'+tenantKey+'-read'
var rows=ods.select("""
select e.id id,e.entity_type entity_type,e.name name,e.short_name short_name,
       e.publication_status publication_status,e.data_changed_at data_changed_at,e.updated_at updated_at,i.org_type org_type,
       i.level_code org_level,i.established_year established_year,i.region_display org_region,i.legal_person legal_person,
       i.credit_code_display credit_code,i.scope_text scope_text,i.active_years active_years,d.genre_display genre_display,
       d.episodes episodes,d.premiere_year premiere_year,d.region_display drama_region,
       d.broadcast_status broadcast_status,d.synopsis synopsis,d.duration_display duration_display,
       d.copyright_display copyright_display,a.level_code award_level,a.founded_year founded_year,a.cycle_display cycle_display,
       a.description award_description,a.region_display award_region
from JC_ENTITY e
left join JC_INSTITUTION i on i.ID=e.ID
left join JC_DRAMA d on d.ID=e.ID
left join JC_AWARD a on a.ID=e.ID
where lower(e.entity_type) in ('org','institution','drama','award')
    and e.publication_status='PUBLISHED'
""")
if(body!=null&&body.dryRun==true){return {dryRun:true,tenantId:tenantId,source:'linewell_ods',candidateDocuments:rows.size(),aliases:{catalogRead:catalogRead,catalogWrite:catalogWrite,portalRead:portalRead}}}
var documents=[]
var dramaById={}
for(row in rows){
 var type=(''+row.entityType).toLowerCase()
 if(type=='institution'){type='org'}
 var doc={id:''+row.id,entityType:type,name:row.name==null?'':row.name,shortName:row.shortName==null?'':row.shortName,publicationStatus:'PUBLISHED',sourceSystem:'linewell_ods',updatedAt:row.updatedAt==null?null:''+row.updatedAt,dataChangedAt:row.dataChangedAt==null?null:''+row.dataChangedAt,tags:[],hostOrgIds:[],awardIds:[],productionOrgIds:[],broadcastOrgIds:[],transmissionOrgIds:[],awardLinks:[]}
 if(type=='org'){
  doc.orgType=row.orgType==null?'social':(''+row.orgType).toLowerCase();doc.level=row.orgLevel==null?'待确认':row.orgLevel;doc.establishedYear=row.establishedYear==null?0:row.establishedYear;doc.region=row.orgRegion==null?'':row.orgRegion;doc.legalPerson=row.legalPerson==null?'':row.legalPerson;doc.creditCode=row.creditCode==null?'':row.creditCode;doc.scope=row.scope==null?'':row.scope;doc.activeYears=row.activeYears==null?0:row.activeYears
 }else if(type=='drama'){
  doc.title=doc.name;doc.genre=row.genreDisplay==null?'':row.genreDisplay;doc.episodes=row.episodes==null?0:row.episodes;doc.year=row.premiereYear==null?0:row.premiereYear;doc.region=row.dramaRegion==null?'':row.dramaRegion;doc.status=row.broadcastStatus==null?'UNKNOWN':row.broadcastStatus;doc.synopsis=row.synopsis==null?'':row.synopsis;doc.duration=row.durationDisplay==null?'':row.durationDisplay;doc.copyright=row.copyrightDisplay==null?'':row.copyrightDisplay;dramaById[doc.id]=doc
 }else if(type=='award'){
  doc.level=row.awardLevel==null?'行业级':row.awardLevel;doc.founded=row.foundedYear==null?0:row.foundedYear;doc.cycle=row.cycleDisplay==null?'':row.cycleDisplay;doc.description=row.awardDescription==null?'':row.awardDescription;doc.region=row.awardRegion==null?'':row.awardRegion
 }
 documents.add(doc)
}
var orgRelations=ods.select("""
select drama_id,institution_id,relation_type
from JC_DRAMA_ORG
""")
for(relation in orgRelations){var drama=dramaById[''+relation.dramaId];if(drama!=null){var relationType=relation.relationType==null?'':(''+relation.relationType).toLowerCase();if(relationType.indexOf('production')>=0||relationType.indexOf('make')>=0){drama.productionOrgIds.add(''+relation.institutionId)}else if(relationType.indexOf('broadcast')>=0){drama.broadcastOrgIds.add(''+relation.institutionId)}else if(relationType.indexOf('transmission')>=0||relationType.indexOf('communication')>=0){drama.transmissionOrgIds.add(''+relation.institutionId)}}}
var hosts=ods.select("""
select award_id,institution_id
from JC_AWARD_ORG
where is_primary=1
""")
var awardById={};for(doc in documents){if(doc.entityType=='award'){awardById[doc.id]=doc}}
for(host in hosts){var award=awardById[''+host.awardId];if(award!=null){award.hostOrgIds.add(''+host.institutionId)}}
var awardRecords=ods.select("""
select r.id id,r.drama_id drama_id,e.award_id award_id,e.award_year year,c.name category,r.result_label result
from JC_AWARD_RECORD r
join JC_AWARD_EDITION e on e.id=r.edition_id
left join JC_AWARD_CATEGORY c on c.id=r.category_id
where r.publication_status='PUBLISHED'
""")
for(record in awardRecords){var drama=dramaById[''+record.dramaId];if(drama!=null&&record.awardId!=null){drama.awardIds.add(''+record.awardId);drama.awardLinks.add({recordId:''+record.id,awardId:''+record.awardId,year:record.year==null?0:record.year,category:record.category==null?'':record.category,result:record.result==null?'':record.result,reviewOrgId:'',reviewOrgIds:[]})}}var newIndex='jcc-search-catalog-'+tenantKey+'-v'+new Date().getTime()
var definition={settings:{number_of_shards:1,number_of_replicas:0},mappings:{dynamic:false,properties:{id:{type:'keyword'},entityType:{type:'keyword'},name:{type:'text',fields:{keyword:{type:'keyword',ignore_above:512}}},title:{type:'text',fields:{keyword:{type:'keyword',ignore_above:512}}},shortName:{type:'text'},publicationStatus:{type:'keyword'},sourceSystem:{type:'keyword'},updatedAt:{type:'date',format:'yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis'},dataChangedAt:{type:'date',format:'yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis'},orgType:{type:'keyword'},level:{type:'keyword'},establishedYear:{type:'integer'},founded:{type:'integer'},year:{type:'integer'},episodes:{type:'integer'},genre:{type:'keyword'},region:{type:'keyword'},status:{type:'keyword'},synopsis:{type:'text'},description:{type:'text'},tags:{type:'keyword'},hostOrgIds:{type:'keyword'},awardIds:{type:'keyword'},productionOrgIds:{type:'keyword'},broadcastOrgIds:{type:'keyword'},transmissionOrgIds:{type:'keyword'}}}}
searchEs.createIndex(newIndex,definition)
var writeResult=searchEs.bulkUpsert(newIndex,documents)
searchEs.refresh(newIndex)
var indexed=searchEs.count(newIndex).count
if(indexed!=documents.size()){throw new IllegalStateException('ES 索引计数校验失败：期望 '+documents.size()+'，实际 '+indexed)}
searchEs.replaceAlias(catalogRead,newIndex)
searchEs.replaceAlias(catalogWrite,newIndex)
searchEs.replaceAlias(portalRead,newIndex)
return {success:true,tenantId:tenantId,source:'linewell_ods',index:newIndex,indexed:indexed,aliases:{catalogRead:catalogRead,catalogWrite:catalogWrite,portalRead:portalRead},write:{requested:writeResult.requested,indexed:writeResult.indexed,took:writeResult.took}}

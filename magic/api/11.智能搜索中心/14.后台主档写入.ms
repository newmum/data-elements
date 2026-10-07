{
  "properties": {},
  "id": "9837965cac99319f8cfb9775f21017bb",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "14.后台主档写入",
  "createTime": null,
  "updateTime": 1789860511735,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/admin/resource-write",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "后台业务资料写入：主档同步 Elasticsearch，其余现有表直接写入达梦 linewell_ods。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：14.后台主档写入
// 路由：POST /search/admin/resource-write
// 业务说明：后台业务资料写入：主档同步 Elasticsearch，其余现有表直接写入达梦 linewell_ods。
import tenantRuntime
import flowDataSource
import searchEs
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var catalog=[{"key":"institutions","table":"jc_institution","title":"机构档案","group":"业务资料","readonly":false,"entityType":"org"},{"key":"dramas","table":"jc_drama","title":"剧目档案","group":"业务资料","readonly":false,"entityType":"drama"},{"key":"awards","table":"jc_award","title":"奖项档案","group":"业务资料","readonly":false,"entityType":"award"},{"key":"institution-licenses","table":"jc_institution_license","title":"机构证照","group":"业务资料","readonly":false,"entityType":null},{"key":"institution-stats","table":"jc_institution_stat","title":"机构统计","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-licenses","table":"jc_drama_license","title":"剧目许可","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-credits","table":"jc_drama_credit","title":"主创条目","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-organizations","table":"jc_drama_org","title":"剧目机构关系","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-metrics","table":"jc_drama_metric","title":"剧目指标","group":"业务资料","readonly":false,"entityType":null},{"key":"award-editions","table":"jc_award_edition","title":"奖项届次","group":"业务资料","readonly":false,"entityType":null},{"key":"award-categories","table":"jc_award_category","title":"奖项类别","group":"业务资料","readonly":false,"entityType":null},{"key":"award-organizations","table":"jc_award_org","title":"奖项组织关系","group":"业务资料","readonly":false,"entityType":null},{"key":"award-records","table":"jc_award_record","title":"评审记录","group":"业务资料","readonly":false,"entityType":null},{"key":"record-organizations","table":"jc_award_record_org","title":"评审机构","group":"业务资料","readonly":false,"entityType":null},{"key":"process-instances","table":"jc_process_instance","title":"实际流程记录","group":"业务资料","readonly":false,"entityType":null},{"key":"process-steps","table":"jc_process_step","title":"实际流程步骤","group":"业务资料","readonly":false,"entityType":null},{"key":"libraries","table":"jc_library","title":"六库配置","group":"基础配置","readonly":false,"entityType":null},{"key":"library-tables","table":"jc_library_table","title":"资料集说明","group":"基础配置","readonly":false,"entityType":null},{"key":"dictionary-items","table":"jc_dictionary_item","title":"业务字典","group":"基础配置","readonly":false,"entityType":null},{"key":"tags","table":"jc_tag","title":"业务标签","group":"基础配置","readonly":false,"entityType":null},{"key":"hot-keywords","table":"jc_hot_keyword","title":"推荐词","group":"基础配置","readonly":false,"entityType":null},{"key":"contents","table":"jc_content_item","title":"门户内容","group":"基础配置","readonly":false,"entityType":null},{"key":"process-templates","table":"jc_process_template","title":"流程模板","group":"基础配置","readonly":false,"entityType":null},{"key":"process-template-steps","table":"jc_process_template_step","title":"模板节点","group":"基础配置","readonly":false,"entityType":null},{"key":"sources","table":"jc_source_system","title":"数据来源","group":"数据管理","readonly":false,"entityType":null},{"key":"source-records","table":"jc_source_record","title":"来源记录","group":"数据管理","readonly":false,"entityType":null},{"key":"services","table":"jc_service","title":"共享服务目录","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"service-versions","table":"jc_service_version","title":"服务版本","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"service-bindings","table":"jc_service_binding","title":"前台服务绑定","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-collections","table":"jc_search_collection","title":"检索资源","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-fields","table":"jc_search_field","title":"查询字段","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-lexicons","table":"jc_search_lexicon","title":"搜索词库","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"lexicon-terms","table":"jc_search_lexicon_term","title":"词条","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"query-models","table":"jc_query_model","title":"组合检索模型","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"detail-sections","table":"jc_detail_section","title":"详情扩展","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"portal-pages","table":"jc_portal_page","title":"门户配置","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"portal-topics","table":"jc_portal_topic","title":"应用专题","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"hot-policies","table":"jc_hot_policy","title":"热门策略","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-eval-cases","table":"jc_search_eval_case","title":"评测用例","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-places","table":"jc_geo_place","title":"业务地点","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-events","table":"jc_geo_event","title":"时空事件","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-fences","table":"jc_geo_fence","title":"电子围栏","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"kg-datasets","table":"jc_kg_dataset","title":"图谱数据集","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-schemas","table":"jc_kg_schema","title":"图谱模式","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-mappings","table":"jc_kg_mapping","title":"图谱映射","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-rules","table":"jc_kg_inference_rule","title":"推导规则","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-themes","table":"jc_kg_theme","title":"图谱主题","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"profile-templates","table":"jc_profile_template","title":"实体画像模板","group":"画像与群组","readonly":false,"entityType":null},{"key":"label-rules","table":"jc_label_rule","title":"标签规则","group":"画像与群组","readonly":false,"entityType":null},{"key":"groups","table":"jc_group_definition","title":"群组定义","group":"画像与群组","readonly":false,"entityType":null},{"key":"subscription-apps","table":"jc_subscription_app","title":"调用应用","group":"服务订阅","readonly":false,"entityType":null},{"key":"subscriptions","table":"jc_subscription","title":"服务订阅","group":"服务订阅","readonly":false,"entityType":null},{"key":"outbox-events","table":"jc_outbox_event","title":"同步任务","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"ingest-jobs","table":"jc_ingest_job","title":"导入任务","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"ingest-items","table":"jc_ingest_item","title":"逐行导入结果","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"service-call-logs","table":"jc_service_call_log","title":"服务调用日志","group":"任务与运行记录","readonly":true,"entityType":null}]
var genericSpec=catalog.filter(item=>item.key==resource)[0]
if(genericSpec==null){throw new IllegalArgumentException('不支持的业务资源')}
if(['institutions','dramas','awards'].indexOf(resource)<0){
 if(genericSpec.readonly){throw new IllegalArgumentException('运行记录为只读资料，不能直接修改')}
 var genericPayload=body==null?{}:body;var genericData=genericPayload.data==null?genericPayload:genericPayload.data
 var genericId=id==null||id==''?genericData.id:id
 if(genericId==null||(''+genericId).trim()==''){throw new IllegalArgumentException('新增资料必须提供编号')}
 var dbColumns=ods.select("""
select lower(column_name) name
from user_tab_columns
where upper(table_name)=upper(#{genericSpec.table})
""")
 var allowed=[];for(column in dbColumns){allowed.add(column.name)}
 var writeData={};for(key in genericData){if(allowed.indexOf((''+key).toLowerCase())>=0 && key!='tenant_id' && key!='created_at' && key!='updated_at' && key!='is_del'){writeData[key]=genericData[key]}}
 writeData.id=genericId
 if(genericPayload.dryRun==true){return {dryRun:true,operation:id==null||id==''?'CREATE':'UPDATE',resource:resource,id:genericId}}
 var exists=ods.selectInt(`select count(1) from ${genericSpec.table} where ID=#{genericId}`)>0
 if(id==null||id==''){if(exists){throw new IllegalArgumentException('业务编号已存在')};ods.table(genericSpec.table).insert(writeData)}else{if(!exists){throw new IllegalArgumentException('未找到待修改的业务资料')};ods.table(genericSpec.table).where().eq('id',genericId).update(writeData)}
 return {operation:id==null||id==''?'CREATE':'UPDATE',resource:resource,id:genericId,message:'已写入达梦 linewell_ods'}
}
var payload=body==null?{}:body
var profile=payload.profile==null?{}:payload.profile
var data=payload.data==null?{}:payload.data
var entityId=id==null||id==''?profile.id:id
var creating=id==null||id==''
if(entityId==null || entityId.length()<8 || entityId.length()>64){throw new IllegalArgumentException('业务编号不合法')}
var name=profile.name==null?'':profile.name.trim()
var shortName=profile.short_name==null?null:profile.short_name.trim()
if(name==''){throw new IllegalArgumentException('名称不能为空')}
var entityType=resource=='institutions'?'org':resource=='dramas'?'drama':'award'
var orgType=data.org_type==null?null:data.org_type
var levelCode=data.level_code==null?null:data.level_code
var establishedYear=data.established_year==null||data.established_year==''?null:Integer.parseInt(''+data.established_year)
var genreDisplay=data.genre_display==null?null:data.genre_display
var premiereYear=data.premiere_year==null||data.premiere_year==''?null:Integer.parseInt(''+data.premiere_year)
var episodes=data.episodes==null||data.episodes==''?null:Integer.parseInt(''+data.episodes)
var regionDisplay=data.region_display==null?null:data.region_display
var broadcastStatus=data.broadcast_status==null||data.broadcast_status==''?'UNKNOWN':data.broadcast_status
var synopsis=data.synopsis==null?null:data.synopsis
var foundedYear=data.founded_year==null||data.founded_year==''?null:Integer.parseInt(''+data.founded_year)
var cycleDisplay=data.cycle_display==null?null:data.cycle_display
var description=data.description==null?null:data.description
if(resource=='institutions' && (['production','transmission','broadcast','social'].indexOf(orgType)<0 || levelCode==null || levelCode=='')){throw new IllegalArgumentException('机构类型和机构层级不能为空且必须为有效值')}
if(resource=='dramas' && episodes!=null && episodes<=0){throw new IllegalArgumentException('剧目集数必须大于 0')}
if(resource=='awards' && (levelCode==null || levelCode=='')){throw new IllegalArgumentException('奖项级别不能为空')}
var exists=ods.selectInt("""
select count(1)
from JC_ENTITY
where id=#{entityId}
""")>0
if(creating && exists){throw new IllegalArgumentException('业务编号已存在')}
if(!creating && !exists){throw new IllegalArgumentException('未找到待修改的业务资料')}
if(payload.dryRun==true){return {dryRun:true,operation:creating?'CREATE':'UPDATE',resource:resource,id:entityId,name:name}}
if(creating){
 ods.update("""
insert into JC_ENTITY(ID,
                ENTITY_TYPE, NAME, SHORT_NAME, PUBLICATION_STATUS, VISIBILITY, PUBLISHED_VERSION, IS_DEMO,
                CREATED_BY, PUBLISHED_AT, DATA_CHANGED_AT, CREATED_AT, UPDATED_AT)
values(#{entityId},#{entityType},#{name},#{shortName},'PUBLISHED','INTERNAL',1,0,'magic-api',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
""")
 if(resource=='institutions'){ods.update("""
insert into JC_INSTITUTION(ID,
                ORG_TYPE, LEVEL_CODE, ESTABLISHED_YEAR, REGION_DISPLAY, VERIFICATION_STATUS, CREATED_AT,
                UPDATED_AT)
values(#{entityId},#{orgType},#{levelCode},#{establishedYear},#{regionDisplay},'UNVERIFIED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
""")}
 if(resource=='dramas'){ods.update("""
insert into JC_DRAMA(ID,
                GENRE_DISPLAY, EPISODES, PREMIERE_YEAR, REGION_DISPLAY, BROADCAST_STATUS, SYNOPSIS,
                CREATED_AT, UPDATED_AT)
values(#{entityId},#{genreDisplay},#{episodes},#{premiereYear},#{regionDisplay},#{broadcastStatus},#{synopsis},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
""")}
 if(resource=='awards'){ods.update("""
insert into JC_AWARD(ID,
                LEVEL_CODE, FOUNDED_YEAR, CYCLE_DISPLAY, CYCLE_CODE, DESCRIPTION, REGION_DISPLAY, CREATED_AT,
                UPDATED_AT)
values(#{entityId},#{levelCode},#{foundedYear},#{cycleDisplay},'UNKNOWN',#{description},#{regionDisplay},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
""")}
}else{
 ods.update("""
update JC_ENTITY
set NAME=#{name},SHORT_NAME=#{shortName},PUBLICATION_STATUS='PUBLISHED',DATA_CHANGED_AT=CURRENT_TIMESTAMP,
        UPDATED_AT=CURRENT_TIMESTAMP
where ID=#{entityId}
    and ENTITY_TYPE=#{entityType}
""")
 if(resource=='institutions'){ods.update("""
update JC_INSTITUTION
set ORG_TYPE=#{orgType},LEVEL_CODE=#{levelCode},ESTABLISHED_YEAR=#{establishedYear},
    REGION_DISPLAY=#{regionDisplay},UPDATED_AT=CURRENT_TIMESTAMP
where ID=#{entityId}
""")}
 if(resource=='dramas'){ods.update("""
update JC_DRAMA
set GENRE_DISPLAY=#{genreDisplay},EPISODES=#{episodes},PREMIERE_YEAR=#{premiereYear},
    REGION_DISPLAY=#{regionDisplay},BROADCAST_STATUS=#{broadcastStatus},SYNOPSIS=#{synopsis},UPDATED_AT=CURRENT_TIMESTAMP
where ID=#{entityId}
""")}
 if(resource=='awards'){ods.update("""
update JC_AWARD
set LEVEL_CODE=#{levelCode},FOUNDED_YEAR=#{foundedYear},CYCLE_DISPLAY=#{cycleDisplay},
    DESCRIPTION=#{description},REGION_DISPLAY=#{regionDisplay},UPDATED_AT=CURRENT_TIMESTAMP
where ID=#{entityId}
""")}
}
var indexAlias='jcc-search-catalog-'+(''+tenantId).toLowerCase()+'-write'
if(!searchEs.exists(indexAlias)){throw new IllegalStateException('搜索索引尚未就绪，请先执行“搜索索引全量重建”')}
var indexDoc={id:''+entityId,entityType:entityType,name:name,shortName:shortName==null?'':shortName,publicationStatus:'PUBLISHED',sourceSystem:'linewell_ods',updatedAt:''+new Date(),dataChangedAt:''+new Date(),tags:[],hostOrgIds:[],awardIds:[],productionOrgIds:[],broadcastOrgIds:[],transmissionOrgIds:[]}
if(resource=='institutions'){indexDoc.orgType=orgType;indexDoc.level=levelCode;indexDoc.establishedYear=establishedYear==null?0:establishedYear;indexDoc.region=regionDisplay==null?'':regionDisplay}
if(resource=='dramas'){indexDoc.title=name;indexDoc.genre=genreDisplay==null?'':genreDisplay;indexDoc.episodes=episodes==null?0:episodes;indexDoc.year=premiereYear==null?0:premiereYear;indexDoc.region=regionDisplay==null?'':regionDisplay;indexDoc.status=broadcastStatus;indexDoc.synopsis=synopsis==null?'':synopsis;var relatedOrgs=ods.select("""
select institution_id,relation_type
from JC_DRAMA_ORG
where drama_id=#{entityId}
""");for(relation in relatedOrgs){var relationType=relation.relationType==null?'':(''+relation.relationType).toLowerCase();if(relationType.indexOf('production')>=0||relationType.indexOf('make')>=0){indexDoc.productionOrgIds.add(''+relation.institutionId)}else if(relationType.indexOf('broadcast')>=0){indexDoc.broadcastOrgIds.add(''+relation.institutionId)}else if(relationType.indexOf('transmission')>=0||relationType.indexOf('communication')>=0){indexDoc.transmissionOrgIds.add(''+relation.institutionId)}};var relatedAwards=ods.select("""
select e.award_id award_id
from JC_AWARD_RECORD r
join JC_AWARD_EDITION e on e.id=r.edition_id
where r.drama_id=#{entityId}
    and r.publication_status='PUBLISHED'
""");for(record in relatedAwards){if(record.awardId!=null){indexDoc.awardIds.add(''+record.awardId)}}}
if(resource=='awards'){indexDoc.level=levelCode;indexDoc.founded=foundedYear==null?0:foundedYear;indexDoc.cycle=cycleDisplay==null?'':cycleDisplay;indexDoc.description=description==null?'':description;indexDoc.region=regionDisplay==null?'':regionDisplay;var relatedHosts=ods.select("""
select institution_id
from JC_AWARD_ORG
where award_id=#{entityId}
    and is_primary=1
""");for(host in relatedHosts){indexDoc.hostOrgIds.add(''+host.institutionId)}}
var indexResult=searchEs.upsert(indexAlias,indexDoc)
searchEs.refresh(indexAlias)
return {operation:creating?'CREATE':'UPDATE',resource:resource,id:entityId,name:name,message:'已写入达梦 linewell_ods 并同步 Elasticsearch',indexSync:{status:'SYNCED',index:indexAlias,result:indexResult.result}}

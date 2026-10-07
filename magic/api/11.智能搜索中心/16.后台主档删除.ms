{
  "properties": {},
  "id": "0beab44c9055338abc9a5d57a37270e4",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "16.后台主档删除",
  "createTime": null,
  "updateTime": 1789860511831,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/admin/resource-delete",
  "method": "DELETE",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "后台业务资料删除：主档同步 Elasticsearch，其余现有表直接删除。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：16.后台主档删除
// 路由：DELETE /search/admin/resource-delete
// 业务说明：后台业务资料删除：主档同步 Elasticsearch，其余现有表直接删除。
import tenantRuntime
import flowDataSource
import searchEs
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var catalog=[{"key":"institutions","table":"jc_institution","title":"机构档案","group":"业务资料","readonly":false,"entityType":"org"},{"key":"dramas","table":"jc_drama","title":"剧目档案","group":"业务资料","readonly":false,"entityType":"drama"},{"key":"awards","table":"jc_award","title":"奖项档案","group":"业务资料","readonly":false,"entityType":"award"},{"key":"institution-licenses","table":"jc_institution_license","title":"机构证照","group":"业务资料","readonly":false,"entityType":null},{"key":"institution-stats","table":"jc_institution_stat","title":"机构统计","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-licenses","table":"jc_drama_license","title":"剧目许可","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-credits","table":"jc_drama_credit","title":"主创条目","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-organizations","table":"jc_drama_org","title":"剧目机构关系","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-metrics","table":"jc_drama_metric","title":"剧目指标","group":"业务资料","readonly":false,"entityType":null},{"key":"award-editions","table":"jc_award_edition","title":"奖项届次","group":"业务资料","readonly":false,"entityType":null},{"key":"award-categories","table":"jc_award_category","title":"奖项类别","group":"业务资料","readonly":false,"entityType":null},{"key":"award-organizations","table":"jc_award_org","title":"奖项组织关系","group":"业务资料","readonly":false,"entityType":null},{"key":"award-records","table":"jc_award_record","title":"评审记录","group":"业务资料","readonly":false,"entityType":null},{"key":"record-organizations","table":"jc_award_record_org","title":"评审机构","group":"业务资料","readonly":false,"entityType":null},{"key":"process-instances","table":"jc_process_instance","title":"实际流程记录","group":"业务资料","readonly":false,"entityType":null},{"key":"process-steps","table":"jc_process_step","title":"实际流程步骤","group":"业务资料","readonly":false,"entityType":null},{"key":"libraries","table":"jc_library","title":"六库配置","group":"基础配置","readonly":false,"entityType":null},{"key":"library-tables","table":"jc_library_table","title":"资料集说明","group":"基础配置","readonly":false,"entityType":null},{"key":"dictionary-items","table":"jc_dictionary_item","title":"业务字典","group":"基础配置","readonly":false,"entityType":null},{"key":"tags","table":"jc_tag","title":"业务标签","group":"基础配置","readonly":false,"entityType":null},{"key":"hot-keywords","table":"jc_hot_keyword","title":"推荐词","group":"基础配置","readonly":false,"entityType":null},{"key":"contents","table":"jc_content_item","title":"门户内容","group":"基础配置","readonly":false,"entityType":null},{"key":"process-templates","table":"jc_process_template","title":"流程模板","group":"基础配置","readonly":false,"entityType":null},{"key":"process-template-steps","table":"jc_process_template_step","title":"模板节点","group":"基础配置","readonly":false,"entityType":null},{"key":"sources","table":"jc_source_system","title":"数据来源","group":"数据管理","readonly":false,"entityType":null},{"key":"source-records","table":"jc_source_record","title":"来源记录","group":"数据管理","readonly":false,"entityType":null},{"key":"services","table":"jc_service","title":"共享服务目录","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"service-versions","table":"jc_service_version","title":"服务版本","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"service-bindings","table":"jc_service_binding","title":"前台服务绑定","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-collections","table":"jc_search_collection","title":"检索资源","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-fields","table":"jc_search_field","title":"查询字段","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-lexicons","table":"jc_search_lexicon","title":"搜索词库","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"lexicon-terms","table":"jc_search_lexicon_term","title":"词条","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"query-models","table":"jc_query_model","title":"组合检索模型","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"detail-sections","table":"jc_detail_section","title":"详情扩展","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"portal-pages","table":"jc_portal_page","title":"门户配置","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"portal-topics","table":"jc_portal_topic","title":"应用专题","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"hot-policies","table":"jc_hot_policy","title":"热门策略","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-eval-cases","table":"jc_search_eval_case","title":"评测用例","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-places","table":"jc_geo_place","title":"业务地点","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-events","table":"jc_geo_event","title":"时空事件","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-fences","table":"jc_geo_fence","title":"电子围栏","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"kg-datasets","table":"jc_kg_dataset","title":"图谱数据集","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-schemas","table":"jc_kg_schema","title":"图谱模式","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-mappings","table":"jc_kg_mapping","title":"图谱映射","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-rules","table":"jc_kg_inference_rule","title":"推导规则","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-themes","table":"jc_kg_theme","title":"图谱主题","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"profile-templates","table":"jc_profile_template","title":"实体画像模板","group":"画像与群组","readonly":false,"entityType":null},{"key":"label-rules","table":"jc_label_rule","title":"标签规则","group":"画像与群组","readonly":false,"entityType":null},{"key":"groups","table":"jc_group_definition","title":"群组定义","group":"画像与群组","readonly":false,"entityType":null},{"key":"subscription-apps","table":"jc_subscription_app","title":"调用应用","group":"服务订阅","readonly":false,"entityType":null},{"key":"subscriptions","table":"jc_subscription","title":"服务订阅","group":"服务订阅","readonly":false,"entityType":null},{"key":"outbox-events","table":"jc_outbox_event","title":"同步任务","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"ingest-jobs","table":"jc_ingest_job","title":"导入任务","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"ingest-items","table":"jc_ingest_item","title":"逐行导入结果","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"service-call-logs","table":"jc_service_call_log","title":"服务调用日志","group":"任务与运行记录","readonly":true,"entityType":null}]
var genericSpec=catalog.filter(item=>item.key==resource)[0]
if(genericSpec==null||id==null||id==''){throw new IllegalArgumentException('缺少有效的业务资源或编号')}
if(['institutions','dramas','awards'].indexOf(resource)<0){
 if(genericSpec.readonly){throw new IllegalArgumentException('运行记录为只读资料，不能直接删除')}
 var exists=ods.selectInt('select count(1) from '+genericSpec.table+' where ID=#{id}')>0
 if(!exists){throw new IllegalArgumentException('未找到待删除的业务资料')}
 if(dryRun==true){return {dryRun:true,operation:'DELETE',resource:resource,id:id}}
 ods.table(genericSpec.table).where().eq('id',id).delete()
 return {operation:'DELETE',resource:resource,id:id,message:'已从达梦 linewell_ods 删除'}
}
var exists=ods.selectInt("""
select count(1)
from JC_ENTITY
where id=#{id}
""")>0
if(!exists){throw new IllegalArgumentException('未找到待删除的业务资料')}
var relationCount=0
if(resource=='institutions'){relationCount=ods.selectInt("""
select count(1)
from JC_DRAMA_ORG
where institution_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_AWARD_ORG
where institution_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_AWARD_RECORD_ORG
where institution_id=#{id}
""")}
if(resource=='dramas'){relationCount=ods.selectInt("""
select count(1)
from JC_DRAMA_ORG
where drama_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_DRAMA_LICENSE
where drama_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_DRAMA_CREDIT
where drama_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_AWARD_RECORD
where drama_id=#{id}
""")}
if(resource=='awards'){relationCount=ods.selectInt("""
select count(1)
from JC_AWARD_EDITION
where award_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_AWARD_CATEGORY
where award_id=#{id}
""")+ods.selectInt("""
select count(1)
from JC_AWARD_ORG
where award_id=#{id}
""")}
if(relationCount>0){throw new IllegalArgumentException('该资料仍被 '+relationCount+' 条业务关联引用，不能直接删除；请先处理关联记录')}
if(dryRun==true){return {dryRun:true,operation:'DELETE',resource:resource,id:id}}
if(resource=='institutions'){ods.update("""
delete
from JC_INSTITUTION
where id=#{id}
""")}
if(resource=='dramas'){ods.update("""
delete
from JC_DRAMA
where id=#{id}
""")}
if(resource=='awards'){ods.update("""
delete
from JC_AWARD
where id=#{id}
""")}
ods.update("""
delete
from JC_ENTITY
where id=#{id}
""")
var indexAlias='jcc-search-catalog-'+(''+tenantId).toLowerCase()+'-write'
if(!searchEs.exists(indexAlias)){throw new IllegalStateException('搜索索引尚未就绪，请先执行“搜索索引全量重建”')}
var indexResult=searchEs.delete(indexAlias,''+id)
searchEs.refresh(indexAlias)
return {operation:'DELETE',resource:resource,id:id,message:'已从达梦 linewell_ods 删除并同步 Elasticsearch',indexSync:{status:'SYNCED',index:indexAlias,result:indexResult.result}}

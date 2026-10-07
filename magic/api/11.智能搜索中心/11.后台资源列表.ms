{
  "properties": {},
  "id": "5e6f96b9586f36d3a85447f4fff98a09",
  "script": null,
  "groupId": "31808ca3357c3f359b69a090101ffcdc",
  "name": "11.后台资源列表",
  "createTime": null,
  "updateTime": 1789860043566,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/admin/resource-list",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": "",
  "description": "完整后台资源列表：达梦 linewell_ods 真实查询。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.后台资源列表
// 路由：GET /search/admin/resource-list
// 业务说明：完整后台资源列表：达梦 linewell_ods 真实查询。
import tenantRuntime
import flowDataSource
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId=tenantRuntime.id()
var sourceKey=flowDataSource.ensureRuntime('linewell_ods')
var ods=db[sourceKey]
var catalog=[{"key":"institutions","table":"jc_institution","title":"机构档案","group":"业务资料","readonly":false,"entityType":"org"},{"key":"dramas","table":"jc_drama","title":"剧目档案","group":"业务资料","readonly":false,"entityType":"drama"},{"key":"awards","table":"jc_award","title":"奖项档案","group":"业务资料","readonly":false,"entityType":"award"},{"key":"institution-licenses","table":"jc_institution_license","title":"机构证照","group":"业务资料","readonly":false,"entityType":null},{"key":"institution-stats","table":"jc_institution_stat","title":"机构统计","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-licenses","table":"jc_drama_license","title":"剧目许可","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-credits","table":"jc_drama_credit","title":"主创条目","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-organizations","table":"jc_drama_org","title":"剧目机构关系","group":"业务资料","readonly":false,"entityType":null},{"key":"drama-metrics","table":"jc_drama_metric","title":"剧目指标","group":"业务资料","readonly":false,"entityType":null},{"key":"award-editions","table":"jc_award_edition","title":"奖项届次","group":"业务资料","readonly":false,"entityType":null},{"key":"award-categories","table":"jc_award_category","title":"奖项类别","group":"业务资料","readonly":false,"entityType":null},{"key":"award-organizations","table":"jc_award_org","title":"奖项组织关系","group":"业务资料","readonly":false,"entityType":null},{"key":"award-records","table":"jc_award_record","title":"评审记录","group":"业务资料","readonly":false,"entityType":null},{"key":"record-organizations","table":"jc_award_record_org","title":"评审机构","group":"业务资料","readonly":false,"entityType":null},{"key":"process-instances","table":"jc_process_instance","title":"实际流程记录","group":"业务资料","readonly":false,"entityType":null},{"key":"process-steps","table":"jc_process_step","title":"实际流程步骤","group":"业务资料","readonly":false,"entityType":null},{"key":"libraries","table":"jc_library","title":"六库配置","group":"基础配置","readonly":false,"entityType":null},{"key":"library-tables","table":"jc_library_table","title":"资料集说明","group":"基础配置","readonly":false,"entityType":null},{"key":"dictionary-items","table":"jc_dictionary_item","title":"业务字典","group":"基础配置","readonly":false,"entityType":null},{"key":"tags","table":"jc_tag","title":"业务标签","group":"基础配置","readonly":false,"entityType":null},{"key":"hot-keywords","table":"jc_hot_keyword","title":"推荐词","group":"基础配置","readonly":false,"entityType":null},{"key":"contents","table":"jc_content_item","title":"门户内容","group":"基础配置","readonly":false,"entityType":null},{"key":"process-templates","table":"jc_process_template","title":"流程模板","group":"基础配置","readonly":false,"entityType":null},{"key":"process-template-steps","table":"jc_process_template_step","title":"模板节点","group":"基础配置","readonly":false,"entityType":null},{"key":"sources","table":"jc_source_system","title":"数据来源","group":"数据管理","readonly":false,"entityType":null},{"key":"source-records","table":"jc_source_record","title":"来源记录","group":"数据管理","readonly":false,"entityType":null},{"key":"services","table":"jc_service","title":"共享服务目录","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"service-versions","table":"jc_service_version","title":"服务版本","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"service-bindings","table":"jc_service_binding","title":"前台服务绑定","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-collections","table":"jc_search_collection","title":"检索资源","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-fields","table":"jc_search_field","title":"查询字段","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-lexicons","table":"jc_search_lexicon","title":"搜索词库","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"lexicon-terms","table":"jc_search_lexicon_term","title":"词条","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"query-models","table":"jc_query_model","title":"组合检索模型","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"detail-sections","table":"jc_detail_section","title":"详情扩展","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"portal-pages","table":"jc_portal_page","title":"门户配置","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"portal-topics","table":"jc_portal_topic","title":"应用专题","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"hot-policies","table":"jc_hot_policy","title":"热门策略","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"search-eval-cases","table":"jc_search_eval_case","title":"评测用例","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-places","table":"jc_geo_place","title":"业务地点","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-events","table":"jc_geo_event","title":"时空事件","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"geo-fences","table":"jc_geo_fence","title":"电子围栏","group":"智能搜索中心","readonly":false,"entityType":null},{"key":"kg-datasets","table":"jc_kg_dataset","title":"图谱数据集","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-schemas","table":"jc_kg_schema","title":"图谱模式","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-mappings","table":"jc_kg_mapping","title":"图谱映射","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-rules","table":"jc_kg_inference_rule","title":"推导规则","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"kg-themes","table":"jc_kg_theme","title":"图谱主题","group":"知识图谱中心","readonly":false,"entityType":null},{"key":"profile-templates","table":"jc_profile_template","title":"实体画像模板","group":"画像与群组","readonly":false,"entityType":null},{"key":"label-rules","table":"jc_label_rule","title":"标签规则","group":"画像与群组","readonly":false,"entityType":null},{"key":"groups","table":"jc_group_definition","title":"群组定义","group":"画像与群组","readonly":false,"entityType":null},{"key":"subscription-apps","table":"jc_subscription_app","title":"调用应用","group":"服务订阅","readonly":false,"entityType":null},{"key":"subscriptions","table":"jc_subscription","title":"服务订阅","group":"服务订阅","readonly":false,"entityType":null},{"key":"outbox-events","table":"jc_outbox_event","title":"同步任务","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"ingest-jobs","table":"jc_ingest_job","title":"导入任务","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"ingest-items","table":"jc_ingest_item","title":"逐行导入结果","group":"任务与运行记录","readonly":true,"entityType":null},{"key":"service-call-logs","table":"jc_service_call_log","title":"服务调用日志","group":"任务与运行记录","readonly":true,"entityType":null}]
var spec=catalog.filter(item=>item.key==resource)[0]
if(spec==null){throw new IllegalArgumentException('不支持的后台资源')}
if(['institutions','dramas','awards'].indexOf(resource)<0){
 var allRows=ods.select('select * from '+spec.table)
 var keyword=q==null?'':(''+q).trim().toLowerCase()
 if(keyword!=''){allRows=allRows.filter(row=>JSON.stringify(row).toLowerCase().indexOf(keyword)>=0)}
 var requestedPage=page==null?1:Integer.parseInt(''+page);var requestedSize=pageSize==null?25:Integer.parseInt(''+pageSize);if(requestedPage<1){requestedPage=1};if(requestedSize<1){requestedSize=25};if(requestedSize>100){requestedSize=100}
 var total=allRows.size();var start=(requestedPage-1)*requestedSize;var end=start+requestedSize;var resultRows=start>=total?[]:allRows.subList(start,end>total?total:end)
 return {items:resultRows,total:total,page:requestedPage,pageSize:requestedSize,source:'linewell_ods',table:spec.table}
}
var keyword=q==null?'':q.trim()
var rows=[]
if(resource=='institutions'){rows=ods.select("""
select e.id id,e.name name,e.short_name short_name,i.org_type org_type,i.level_code level_code,
       i.region_display region_display,i.established_year established_year,i.verification_status verification_status,
       e.publication_status publication_status,e.updated_at updated_at
from JC_ENTITY e
join JC_INSTITUTION i on i.ID=e.ID
where lower(e.entity_type) in ('org','institution')
    and e.publication_status='PUBLISHED'
    and (#{keyword}=''
         or lower(e.name) like lower('%'||#{keyword}||'%')
         or lower(e.short_name) like lower('%'||#{keyword}||'%'))
order by e.updated_at desc
""")}
if(resource=='dramas'){rows=ods.select("""
select e.id id,e.name name,e.short_name short_name,d.genre_display genre_display,
       d.premiere_year premiere_year,d.episodes episodes,d.region_display region_display,d.broadcast_status broadcast_status,
       e.publication_status publication_status,e.updated_at updated_at
from JC_ENTITY e
join JC_DRAMA d on d.ID=e.ID
where lower(e.entity_type)='drama'
    and e.publication_status='PUBLISHED'
    and (#{keyword}=''
         or lower(e.name) like lower('%'||#{keyword}||'%')
         or lower(e.short_name) like lower('%'||#{keyword}||'%'))
order by e.updated_at desc
""")}
if(resource=='awards'){rows=ods.select("""
select e.id id,e.name name,e.short_name short_name,a.level_code level_code,a.founded_year founded_year,
       a.cycle_display cycle_display,a.region_display region_display,e.publication_status publication_status,
       e.updated_at updated_at
from JC_ENTITY e
join JC_AWARD a on a.ID=e.ID
where lower(e.entity_type)='award'
    and e.publication_status='PUBLISHED'
    and (#{keyword}=''
         or lower(e.name) like lower('%'||#{keyword}||'%')
         or lower(e.short_name) like lower('%'||#{keyword}||'%'))
order by e.updated_at desc
""")}
var requestedPage=page==null?1:Integer.parseInt(page);var requestedSize=pageSize==null?25:Integer.parseInt(pageSize);var start=(requestedPage-1)*requestedSize;var end=start+requestedSize;if(start>=rows.size()){rows=[]}else{rows=rows.subList(start,end>rows.size()?rows.size():end)}
return {items:rows,total:resource=='institutions'?ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_INSTITUTION i on i.ID=e.ID
where lower(e.entity_type) in ('org','institution')
    and e.publication_status='PUBLISHED'
    and (#{keyword}=''
         or lower(e.name) like lower('%'||#{keyword}||'%')
         or lower(e.short_name) like lower('%'||#{keyword}||'%'))
"""):resource=='dramas'?ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_DRAMA d on d.ID=e.ID
where lower(e.entity_type)='drama'
    and e.publication_status='PUBLISHED'
    and (#{keyword}=''
         or lower(e.name) like lower('%'||#{keyword}||'%')
         or lower(e.short_name) like lower('%'||#{keyword}||'%'))
"""):ods.selectInt("""
select count(1)
from JC_ENTITY e
join JC_AWARD a on a.ID=e.ID
where lower(e.entity_type)='award'
    and e.publication_status='PUBLISHED'
    and (#{keyword}=''
         or lower(e.name) like lower('%'||#{keyword}||'%')
         or lower(e.short_name) like lower('%'||#{keyword}||'%'))
"""),page:requestedPage,pageSize:requestedSize}

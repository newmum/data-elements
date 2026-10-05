package com.linewell.dataelement.feature.assetcenter.application;

import static com.linewell.dataelement.feature.assetcenter.application.AssetCenterStore.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Read models over the shared inventory. No shadow application, source, catalog or API master data. */
@Service
public class AssetCenterQueryService {
    final AssetCenterStore db;
    private static final Map<String,String> TABLES=Map.of("APPLICATION","sym_application_t","DATASOURCE","db_datasource_t","TABLE","db_table_t","CATALOG","da_catalog_t","API","api_info_t");
    private static final Map<String,String> NAMES=Map.of("APPLICATION","app_name","DATASOURCE","db_name","TABLE","COALESCE(NULLIF(table_name_cn,''),table_name)","CATALOG","catalog_name","API","service_name");
    private static final Map<String,String> CODES=Map.of("APPLICATION","client_id","DATASOURCE","db_type","TABLE","table_name","CATALOG","catalog_name_en","API","service_code");
    public AssetCenterQueryService(AssetCenterStore db){this.db=db;}
    public String table(String kind){String table=TABLES.get(kind);if(table==null)throw error(400,"INVALID_ARGUMENT","不支持的资产类型");return table;}
    public Map<String,Object> page(int pageNo,int pageSize,long total,String sort,String order){return map("pageNo",pageNo,"pageSize",pageSize,"total",total,"hasMore",(long)pageNo*pageSize<total,"sortBy",sort,"sortOrder",order);}
    public Map<String,Object> search(Map<String,String> q){
        db.tenant();db.user();
        int pn=Math.max(1,number(q.get("pageNo"),1)),ps=Math.min(100,Math.max(1,number(q.get("pageSize"),15)));
        String scene=q.getOrDefault("view",q.getOrDefault("scene","MAP"));
        boolean market="MARKET".equals(scene);
        List<String> kinds=Arrays.stream(q.getOrDefault("kinds",market?"CATALOG":"APPLICATION,DATASOURCE,TABLE,CATALOG,API").split(",")).filter(k->!k.isBlank()).toList();
        if(kinds.isEmpty())throw error(400,"INVALID_ARGUMENT","请选择资产类型");
        String sort=q.getOrDefault("sortBy","updatedAt"),order=q.getOrDefault("sortOrder","desc");
        if(!Set.of("name","updatedAt","createdAt","relevance","heat").contains(sort)||!Set.of("asc","desc").contains(order))throw error(400,"INVALID_ARGUMENT","排序字段不正确");
        if(market)db.require("da_catalog_publication_t","da_catalog_publication_head_t");
        List<Object> args=new ArrayList<>(); List<String> queries=new ArrayList<>();
        for(String kind:kinds){
            table(kind); if(market&&!kind.equals("CATALOG"))continue;
            String name=NAMES.get(kind),code=CODES.get(kind),table=TABLES.get(kind);
            String app=Set.of("DATASOURCE","CATALOG").contains(kind)?"app_id":"NULL";
            String sql="SELECT tid AS id,'"+kind+"' AS kind,"+name+" AS name,"+code+" AS code,org_id AS department_id,"+app+" AS application_id,"+(kind.equals("CATALOG")?"business_type":"NULL")+" AS topic_code,created_by,created_time,updated_time FROM "+table+" WHERE tenant_id=? AND COALESCE(is_del,0)=0";
            args.add(db.tenant());
            if(market){
                var orgs=db.organizations();
                sql+=" AND EXISTS (SELECT 1 FROM da_catalog_publication_head_t h JOIN da_catalog_publication_t p ON p.tenant_id=h.tenant_id AND p.tid=h.current_publication_id WHERE h.tenant_id="+table+".tenant_id AND h.catalog_id="+table+".tid AND h.availability='PUBLISHED' AND p.status='PUBLISHED' AND (JSON_UNQUOTE(JSON_EXTRACT(p.visibility_scope,'$.visibility'))='TENANT'";
                if(!orgs.isEmpty()){
                    sql+=" OR (JSON_UNQUOTE(JSON_EXTRACT(p.visibility_scope,'$.visibility'))='DEPARTMENT' AND "+table+".org_id IN ("+String.join(",",Collections.nCopies(orgs.size(),"?"))+"))";
                    args.addAll(orgs);
                    sql+=" OR (JSON_UNQUOTE(JSON_EXTRACT(p.visibility_scope,'$.visibility'))='AUTHORIZED_ORGS' AND JSON_OVERLAPS(JSON_EXTRACT(p.visibility_scope,'$.allowedDepartmentIds'),CAST(? AS JSON)))";
                    args.add(db.encode(orgs));
                }
                sql+="))";
                String channel=q.getOrDefault("channel","").trim();
                if(!channel.isBlank()){
                    if(!Set.of("API","FILE","TABLE_DISTRIBUTION","RESTRICTED_QUERY").contains(channel))throw error(400,"INVALID_ARGUMENT","交付方式不正确");
                    sql+=" AND EXISTS (SELECT 1 FROM da_catalog_publication_head_t fh JOIN da_catalog_publication_t fp ON fp.tenant_id=fh.tenant_id AND fp.tid=fh.current_publication_id WHERE fh.tenant_id="+table+".tenant_id AND fh.catalog_id="+table+".tid AND JSON_CONTAINS(JSON_EXTRACT(fp.metadata_snapshot,'$.channels'),JSON_QUOTE(?)))";
                    args.add(channel);
                }
            }
            String topic=q.getOrDefault("topicCode","").trim();if(kind.equals("CATALOG")&&!topic.isBlank()){sql+=" AND business_type LIKE ?";args.add("%"+topic+"%");}
            String keyword=q.getOrDefault("keyword","").trim();
            if(!keyword.isEmpty()){sql+=" AND ("+name+" LIKE ? OR "+code+" LIKE ?"+(kind.equals("CATALOG")?" OR asset_desc LIKE ?":"")+")";args.add("%"+keyword+"%");args.add("%"+keyword+"%");if(kind.equals("CATALOG"))args.add("%"+keyword+"%");}
            if(!q.getOrDefault("departmentId","").isBlank()){sql+=" AND org_id=?";args.add(q.get("departmentId"));}
            if(!q.getOrDefault("applicationId","").isBlank()){
                if(kind.equals("APPLICATION")){sql+=" AND tid=?";}else if(Set.of("DATASOURCE","CATALOG").contains(kind)){sql+=" AND app_id=?";}else if(kind.equals("TABLE")){sql+=" AND datasource_id IN(SELECT tid FROM db_datasource_t WHERE tenant_id=? AND app_id=?)";args.add(db.tenant());}else{sql+=" AND 1=0";queries.add(sql);continue;}args.add(q.get("applicationId"));
            }
            queries.add(sql);
        }
        if(queries.isEmpty())return map("items",List.of(),"page",page(pn,ps,0,sort,order),"projectionStatus","CURRENT");
        String union=String.join(" UNION ALL ",queries);
        long total=db.jdbc.queryForObject("SELECT COUNT(*) FROM ("+union+") a",Long.class,args.toArray());
        String sortCol=switch(sort){case "name"->"name";case "createdAt"->"created_time";default->"updated_time";};
        List<Object> limitArgs=new ArrayList<>(args);limitArgs.add(ps);limitArgs.add((long)(pn-1)*ps);
        List<Map<String,Object>> rows=db.rows("SELECT * FROM ("+union+") a ORDER BY "+sortCol+" "+order+",id "+order+" LIMIT ? OFFSET ?",limitArgs.toArray());
        List<Map<String,Object>> items=summaries(rows,market);
        return map("items",items,"page",page(pn,ps,total,sort,order),"projectionStatus","CURRENT");
    }
    public Map<String,Object> summary(Map<String,Object> row,boolean market){
        return summaries(List.of(row),market).get(0);
    }
    /** Resolve all supplemental fields for one page with a bounded number of tenant-scoped queries. */
    List<Map<String,Object>> summaries(List<Map<String,Object>> rows,boolean market){
        if(rows.isEmpty())return List.of();
        String tenant=db.tenant(),user=db.user();
        Set<String> organizations=new HashSet<>(db.organizations());
        Set<String> departmentIds=new LinkedHashSet<>(),applicationIds=new LinkedHashSet<>(),catalogIds=new LinkedHashSet<>(),assetIds=new LinkedHashSet<>();
        for(var row:rows){
            addIfPresent(departmentIds,text(row,"department_id"));
            addIfPresent(applicationIds,text(row,"application_id"));
            addIfPresent(assetIds,text(row,"id"));
            if("CATALOG".equals(text(row,"kind")))addIfPresent(catalogIds,text(row,"id"));
        }
        Map<String,String> departmentNames=new HashMap<>(),applicationNames=new HashMap<>();
        for(var row:fetchIds("rm_org_t","id","id,name",departmentIds,tenant,"COALESCE(deleted,0)=0"))
            departmentNames.put(text(row,"id"),text(row,"name"));
        for(var row:fetchIds("sym_application_t","tid","tid,app_name",applicationIds,tenant,"COALESCE(is_del,0)=0"))
            applicationNames.put(text(row,"tid"),text(row,"app_name"));
        Set<String> favorites=new HashSet<>();
        if(db.column("data_market_collect_t","asset_type"))
            for(var row:fetchIds("data_market_collect_t","asset_id","asset_type,asset_id",assetIds,tenant,"apply_user_id=?",user))
                favorites.add(text(row,"asset_type")+"\u0000"+text(row,"asset_id"));
        Map<String,Map<String,Object>> heads=new HashMap<>(),versions=new HashMap<>();
        if(!catalogIds.isEmpty()&&db.table("da_catalog_publication_head_t")){
            Set<String> versionIds=new LinkedHashSet<>();
            for(var row:fetchIds("da_catalog_publication_head_t","catalog_id","catalog_id,current_publication_id,availability",catalogIds,tenant,null)){
                heads.put(text(row,"catalog_id"),row);
                addIfPresent(versionIds,text(row,"current_publication_id"));
            }
            for(var row:fetchIds("da_catalog_publication_t","tid","tid,title_snapshot,metadata_snapshot,visibility_scope",versionIds,tenant,null))
                versions.put(text(row,"tid"),row);
        }
        SummaryLookups lookups=new SummaryLookups(user,organizations,departmentNames,applicationNames,favorites,heads,versions);
        return rows.stream().map(row->summary(row,market,lookups)).toList();
    }
    private record SummaryLookups(String user,Set<String> organizations,Map<String,String> departmentNames,
            Map<String,String> applicationNames,Set<String> favorites,Map<String,Map<String,Object>> heads,
            Map<String,Map<String,Object>> versions){}
    private static void addIfPresent(Set<String> ids,String id){if(!id.isBlank())ids.add(id);}
    private List<Map<String,Object>> fetchIds(String table,String key,String columns,Set<String> ids,String tenant,String extra,Object... extraArgs){
        if(ids.isEmpty())return List.of();
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
        List<Object> args=new ArrayList<>();args.add(tenant);args.addAll(ids);args.addAll(Arrays.asList(extraArgs));
        return db.rows("SELECT "+columns+" FROM "+table+" WHERE tenant_id=? AND "+key+" IN ("+marks+")"+(extra==null?"":" AND "+extra),args.toArray());
    }
    private Map<String,Object> summary(Map<String,Object> row,boolean market,SummaryLookups lookups){
        String kind=text(row,"kind"),id=text(row,"id");
        Map<String,Object> ref=map("id",id,"kind",kind,"name",text(row,"name"),"code",text(row,"code"),"available",true);
        List<String> actions=new ArrayList<>(List.of("VIEW","FAVORITE"));
        if(lookups.user.equals(text(row,"created_by")) || lookups.organizations.contains(text(row,"department_id"))) actions.add("EDIT");
        if(kind.equals("TABLE")&&actions.contains("EDIT"))actions.add("PREVIEW");
        String topic=text(row,"topic_code");Map<String,Object> result=map("object",ref,"departmentId",text(row,"department_id"),"departmentName",lookups.departmentNames.getOrDefault(text(row,"department_id"),""),"applicationId",text(row,"application_id"),"applicationName",lookups.applicationNames.getOrDefault(text(row,"application_id"),""),"updatedAt",iso(row.get("updated_time")),"listingStatus","NOT_LISTED","channels",List.of(),"favorite",lookups.favorites.contains(kind+"\u0000"+id),"allowedActions",actions,"governance",map("qualityStatus","NOT_CHECKED"),"tags",List.of(),"topicCodes",topic.isBlank()?List.of():List.of(topic));
        if(kind.equals("CATALOG")){
            var head=lookups.heads.get(id);
            if(head!=null){result.put("listingStatus","PUBLISHED".equals(text(head,"availability"))?"LISTED":"OFFLINE");result.put("publishedVersionId",head.get("current_publication_id"));if(head.get("current_publication_id")!=null){ref.put("versionId",head.get("current_publication_id"));var version=lookups.versions.get(text(head,"current_publication_id"));if(version!=null){if(market)ref.put("name",version.get("title_snapshot"));result.put("channels",db.object(version.get("metadata_snapshot")).getOrDefault("channels",List.of()));var policy=db.object(version.get("visibility_scope"));if("PUBLISHED".equals(text(head,"availability"))&&visible(policy,text(row,"department_id"),lookups.organizations)){if(!"PROHIBITED".equals(text(policy,"shareType"))){actions.add("APPLY");actions.add("SUBSCRIBE");}if("MASKED_SAMPLE".equals(text(policy,"previewMode")))actions.add("PREVIEW");}}}}
        }
        return result;
    }
    public Map<String,Object> object(String kind,String id,Map<String,String> query){
        db.user();Map<String,Object> row=db.owned(table(kind),id,false);
        if(kind.equals("CATALOG")&&"MARKET".equals(query.get("view"))){
            db.require("da_catalog_publication_head_t","da_catalog_publication_t");
            var published=db.one("SELECT p.visibility_scope FROM da_catalog_publication_head_t h JOIN da_catalog_publication_t p ON p.tenant_id=h.tenant_id AND p.tid=h.current_publication_id WHERE h.tenant_id=? AND h.catalog_id=? AND h.availability='PUBLISHED' AND p.status='PUBLISHED'",db.tenant(),id);
            if(published==null||!visible(db.object(published.get("visibility_scope")),text(row,"org_id")))throw error(404,"NOT_FOUND","资源目录未上架或不在可见范围内");
        }
        Map<String,Object> summary=summaryRow(kind,row);
        Map<String,Object> details=new LinkedHashMap<>();
        List<Map<String,Object>> related=new ArrayList<>();
        int pn=Math.max(1,number(query.get("itemPageNo"),number(query.get("fieldsPage"),number(query.get("fieldPageNo"),1)))),ps=Math.min(100,Math.max(1,number(query.get("fieldsPageSize"),number(query.get("pageSize"),100))));
        switch(kind){
            case "APPLICATION"->{details.put("systemCode",text(row,"client_id"));details.put("technical",map());related.addAll(refs("DATASOURCE","app_id",id));}
            case "DATASOURCE"->{details.putAll(map("engine",text(row,"db_type"),"databaseName",text(row,"db_name"),"connectionStatus",connection(text(row,"connection_status")),"credentialConfigured",!text(row,"password").isBlank(),"tableCount",count("db_table_t","datasource_id",id)));related.addAll(refs("TABLE","datasource_id",id));addRef(related,"APPLICATION",text(row,"app_id"));}
            case "TABLE"->{var fields=dataItems("TABLE",id,pn,ps);long total=count("db_table_column_t","table_id",id);details.putAll(map("datasourceId",text(row,"datasource_id"),"physicalName",text(row,"table_name"),"fieldCount",total,"fieldCollectionStatus",total>0?"SUCCEEDED":"NOT_COLLECTED","fields",fields,"fieldsHasMore",(long)pn*ps<total,"fieldPage",page(pn,ps,total,"name","asc")));addRef(related,"DATASOURCE",text(row,"datasource_id"));related.addAll(refs("CATALOG","source_table_id",id));}
            case "CATALOG"->{var items=dataItems("CATALOG",id,pn,ps);long total=count("da_catalog_item_t","catalog_id",id);details.putAll(map("catalogCode",text(row,"catalog_name_en"),"items",items,"itemsHasMore",(long)pn*ps<total,"itemPage",page(pn,ps,total,"name","asc"),"attachments",attachments(id)));addRef(related,"APPLICATION",text(row,"app_id"));addRef(related,"TABLE",text(row,"source_table_id"));if(summary.get("publishedVersionId")!=null){details.put("versionId",summary.get("publishedVersionId"));details.put("versionStatus","PUBLISHED");}}
            case "API"->{List<Map<String,Object>> params=db.rows("SELECT param_name,param_desc,param_type,param_position,required_flag,param_scope FROM api_param_t WHERE tenant_id=? AND api_id=? AND COALESCE(is_del,0)=0 ORDER BY sort_no,tid LIMIT 100",db.tenant(),id).stream().map(r->map("name",text(r,"param_name"),"location",parameterLocation(r),"dataType",text(r,"param_type"),"required",truth(r.get("required_flag")),"description",text(r,"param_desc"))).toList();details.putAll(map("serviceCode",text(row,"service_code"),"version",text(row,"version"),"method",text(row,"method").toUpperCase(Locale.ROOT),"publicEndpoint",safeEndpoint(text(row,"publish_address_full")),"parameters",params,"statisticsAvailable",row.get("call_count")!=null,"calls",row.get("call_count")));addRef(related,"CATALOG",text(row,"catalog_id"));}
        }
        String description=switch(kind){case "APPLICATION"->text(row,"app_desc");case "DATASOURCE"->text(row,"remark");case "TABLE"->text(row,"table_comment");case "CATALOG"->text(row,"asset_desc");default->text(row,"service_desc");};
        return map("summary",summary,"revision",row.get("revision")==null?"0":String.valueOf(row.get("revision")),"ownerUserId",text(row,"created_by"),"description",description,"details",details,"relatedObjects",related,"relationsHasMore",related.size()>=100);
    }
    public Map<String,Object> summaryRow(String kind,Map<String,Object> row){
        return summary(summaryInput(kind,row),false);
    }
    private Map<String,Object> summaryInput(String kind,Map<String,Object> row){
        String name=switch(kind){case "APPLICATION"->text(row,"app_name");case "DATASOURCE"->text(row,"db_name");case "TABLE"->text(row,"table_name_cn").isBlank()?text(row,"table_name"):text(row,"table_name_cn");case "CATALOG"->text(row,"catalog_name");default->text(row,"service_name");};
        return map("id",text(row,"tid"),"kind",kind,"name",name,"code",text(row,CODES.get(kind)),"department_id",row.get("org_id"),"application_id",row.get("app_id"),"topic_code",row.get("business_type"),"created_by",row.get("created_by"),"updated_time",row.get("updated_time"));
    }
    public Map<String,Object> ref(String kind,String id){return (Map<String,Object>)summaryRow(kind,db.owned(table(kind),id,false)).get("object");}
    Map<String,Map<String,Object>> references(List<Map<String,Object>> selections){
        Map<String,Set<String>> idsByKind=new LinkedHashMap<>();
        for(var selection:selections){String kind=text(selection,"asset_type"),assetId=text(selection,"asset_id");if(TABLES.containsKey(kind)&&!assetId.isBlank())idsByKind.computeIfAbsent(kind,ignored->new LinkedHashSet<>()).add(assetId);}
        List<Map<String,Object>> inputs=new ArrayList<>();String tenant=db.tenant();
        for(var entry:idsByKind.entrySet())for(var row:fetchIds(table(entry.getKey()),"tid","*",entry.getValue(),tenant,"COALESCE(is_del,0)=0"))inputs.add(summaryInput(entry.getKey(),row));
        Map<String,Map<String,Object>> result=new HashMap<>();
        for(var summary:summaries(inputs,false)){Map<String,Object> ref=db.object(summary.get("object"));result.put(text(ref,"kind")+"\u0000"+text(ref,"id"),ref);}
        return result;
    }
    private void addRef(List<Map<String,Object>> list,String kind,String id){if(id.isBlank())return;try{list.add(ref(kind,id));}catch(AssetCenterException e){if(e.status()!=404)throw e;}}
    private List<Map<String,Object>> refs(String kind,String column,String id){var inputs=db.rows("SELECT * FROM "+table(kind)+" WHERE tenant_id=? AND "+column+"=? AND COALESCE(is_del,0)=0 ORDER BY updated_time DESC,tid LIMIT 100",db.tenant(),id).stream().map(r->summaryInput(kind,r)).toList();return summaries(inputs,false).stream().map(r->(Map<String,Object>)r.get("object")).toList();}
    public long count(String table,String column,String id){return db.jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND "+column+"=? AND COALESCE(is_del,0)=0",Long.class,db.tenant(),id);}
    public List<Map<String,Object>> dataItems(String kind,String id,int pn,int ps){
        boolean catalog=kind.equals("CATALOG");String table=catalog?"da_catalog_item_t":"db_table_column_t",key=catalog?"catalog_id":"table_id",sort=catalog?"sort_no":"ordinal_position";
        return db.rows("SELECT * FROM "+table+" WHERE tenant_id=? AND "+key+"=? AND COALESCE(is_del,0)=0 ORDER BY "+sort+",tid LIMIT ? OFFSET ?",db.tenant(),id,ps,(pn-1)*ps).stream().map(r->{Map<String,Object> item=map("id",text(r,"tid"),"name",text(r,catalog?"col_name":"column_comment"),"code",text(r,catalog?"col_en":"column_name"),"dataType",text(r,catalog?"col_type":"data_type"),"length",r.get(catalog?"col_length":"length"),"nullable",truth(r.get(catalog?"is_nullable":"nullable")),"primaryKey",truth(r.get(catalog?"is_pk":"primary_key")),"ordinal",Math.max(1,number(r.get(sort),1)),"definition",text(r,catalog?"col_comment":"column_comment"));if(!text(r,"data_standard_id").isBlank())item.put("standardId",text(r,"data_standard_id"));if(catalog&&!text(r,"source_table_column_id").isBlank())item.put("sourceColumnId",text(r,"source_table_column_id"));return item;}).toList();
    }
    public List<Map<String,Object>> attachments(String catalogId){
        if(!db.table("da_catalog_resource_binding_t"))return List.of();
        List<Map<String,Object>> bindings=db.rows("SELECT * FROM da_catalog_resource_binding_t WHERE tenant_id=? AND catalog_id=? AND status='ACTIVE' ORDER BY display_order,tid LIMIT 100",db.tenant(),catalogId);
        if(bindings.isEmpty())return List.of();
        List<Map<String,Object>> selections=new ArrayList<>();Set<String> bindingIds=new LinkedHashSet<>();
        for(var binding:bindings){String type=text(binding,"resource_type");selections.add(map("asset_type",type.equals("DIRECTORY")?"CATALOG":type,"asset_id",text(binding,"resource_id")));bindingIds.add(text(binding,"tid"));}
        Map<String,Map<String,Object>> references=references(selections);
        String marks=String.join(",",Collections.nCopies(bindingIds.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(bindingIds);
        Map<String,List<Map<String,Object>>> mappingsByBinding=new HashMap<>();
        for(var mapping:db.rows("SELECT m.* FROM da_catalog_item_mapping_t m JOIN da_catalog_resource_binding_t b ON b.tenant_id=m.tenant_id AND b.tid=m.binding_id AND b.mapping_version=m.mapping_version WHERE m.tenant_id=? AND m.binding_id IN ("+marks+") AND m.status='ACTIVE'",args.toArray()))
            mappingsByBinding.computeIfAbsent(text(mapping,"binding_id"),ignored->new ArrayList<>()).add(map("catalogItemId",mapping.get("catalog_item_id"),"resourceFieldId",mapping.get("resource_field_id"),"resourceFieldPath",mapping.get("resource_field_path"),"mappingKind",mapping.get("mapping_kind"),"mappingVersion",mapping.get("mapping_version"),"transformRef",mapping.get("transform_ref")));
        List<Map<String,Object>> result=new ArrayList<>();
        for(var row:bindings){
            String type=text(row,"resource_type"),kind=type.equals("DIRECTORY")?"CATALOG":type,resourceId=text(row,"resource_id");
            Map<String,Object> reference=references.get(kind+"\u0000"+resourceId);String availability=reference==null?"MISSING":"AVAILABLE";
            if(reference==null)reference=map("id",resourceId,"kind",kind,"name","资源不可用");
            var mappings=mappingsByBinding.getOrDefault(text(row,"tid"),List.of());
            result.add(map("id",text(row,"tid"),"resourceType",type,"resource",reference,"bindingRole",text(row,"binding_role"),"status",text(row,"status"),"primary",truth(row.get("primary_flag")),"ordinal",Math.max(1,number(row.get("display_order"),1)),"mappingVersion",row.get("mapping_version"),"mappings",mappings,"resourceAvailability",availability,"resourceVersionId",row.get("resource_version_id"),"channel",row.get("delivery_channel")));
        }return result;
    }
    public Map<String,Object> mapGraph(Map<String,String> query){
        String kind=query.getOrDefault("rootKind",query.getOrDefault("kind","CATALOG")),id=query.getOrDefault("rootId",query.getOrDefault("id",""));
        if(id.isBlank())throw error(400,"INVALID_ARGUMENT","请选择地图中心资产");
        var detail=object(kind,id,Map.of());var summary=(Map<String,Object>)detail.get("summary");var root=(Map<String,Object>)summary.get("object");
        List<Map<String,Object>> nodes=new ArrayList<>();nodes.add(root);
        List<Map<String,Object>> edges=new ArrayList<>();int i=0;for(var related:(List<Map<String,Object>>)detail.get("relatedObjects")){nodes.add(related);String edgeKind="CONTAINS";if(kind.equals("CATALOG"))edgeKind="ATTACHED_TO";edges.add(map("id",id+"-"+(++i),"source",root,"target",related,"kind",edgeKind,"evidenceType",kind.equals("CATALOG")?"CATALOG_ATTACHMENT":"MASTER_RELATION","label",kind.equals("CATALOG")?"挂接":"归属"));}
        if(kind.equals("CATALOG"))for(var attachment:attachments(id)){var related=db.object(attachment.get("resource"));if(nodes.stream().noneMatch(n->Objects.equals(n.get("id"),related.get("id"))&&Objects.equals(n.get("kind"),related.get("kind")))){nodes.add(related);edges.add(map("id",id+"-attachment-"+(++i),"source",root,"target",related,"kind","ATTACHED_TO","evidenceType","CATALOG_ATTACHMENT","evidenceId",attachment.get("id"),"label","挂接资源"));}}
        return map("nodes",nodes,"edges",edges,"hasMore",detail.get("relationsHasMore"),"asOf",iso(now()));
    }
    public Map<String,Object> statistics(Map<String,String> query){
        db.tenant();db.user();String department=query.getOrDefault("departmentId","").trim();List<Map<String,Object>> metrics=new ArrayList<>();
        for(var spec:List.of(List.of("APPLICATION_COUNT","应用系统","sym_application_t"),List.of("DATASOURCE_COUNT","业务库","db_datasource_t"),List.of("TABLE_COUNT","已采集表","db_table_t"),List.of("FIELD_COUNT","已采集字段","db_table_column_t"),List.of("CATALOG_COUNT","资源目录","da_catalog_t"))){
            Object value=null;String status="AVAILABLE";try{
                String table=spec.get(2),sql;List<Object> args=new ArrayList<>(List.of(db.tenant()));
                if(table.equals("db_table_column_t")){sql="SELECT COUNT(*) FROM db_table_column_t f JOIN db_table_t t ON t.tenant_id=f.tenant_id AND t.tid=f.table_id AND COALESCE(t.is_del,0)=0 WHERE f.tenant_id=? AND COALESCE(f.is_del,0)=0";if(!department.isBlank()){sql+=" AND t.org_id=?";args.add(department);}}
                else{sql="SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND COALESCE(is_del,0)=0";if(!department.isBlank()){sql+=" AND org_id=?";args.add(department);}}
                value=db.jdbc.queryForObject(sql,Long.class,args.toArray());
            }catch(org.springframework.dao.DataAccessException ex){status="UNAVAILABLE";}
            metrics.add(metric(spec.get(0),spec.get(1),value,status));
        }
        Object listed=null;try{if(db.table("da_catalog_publication_head_t")){String sql="SELECT COUNT(*) FROM da_catalog_publication_head_t h JOIN da_catalog_t c ON c.tenant_id=h.tenant_id AND c.tid=h.catalog_id AND COALESCE(c.is_del,0)=0 WHERE h.tenant_id=? AND h.availability='PUBLISHED'";listed=department.isBlank()?db.jdbc.queryForObject(sql,Long.class,db.tenant()):db.jdbc.queryForObject(sql+" AND c.org_id=?",Long.class,db.tenant(),department);}}catch(org.springframework.dao.DataAccessException ignored){}
        metrics.add(metric("LISTED_CATALOG_COUNT","已上架目录",listed,listed==null?"NOT_CONNECTED":"AVAILABLE"));
        Object subscriptions=null;try{if(db.table("da_authorization_scope_t")){String sql="SELECT COUNT(*) FROM da_authorization_scope_t a JOIN da_apply_scope_t s ON s.tenant_id=a.tenant_id AND s.tid=a.apply_scope_id JOIN da_catalog_t c ON c.tenant_id=s.tenant_id AND c.tid=s.catalog_id WHERE a.tenant_id=? AND a.status='ACTIVE' AND a.valid_from<=CURRENT_TIMESTAMP AND a.valid_until>CURRENT_TIMESTAMP";subscriptions=department.isBlank()?db.jdbc.queryForObject(sql,Long.class,db.tenant()):db.jdbc.queryForObject(sql+" AND c.org_id=?",Long.class,db.tenant(),department);}}catch(org.springframework.dao.DataAccessException ignored){}
        metrics.add(metric("ACTIVE_SUBSCRIPTION_COUNT","有效授权范围",subscriptions,subscriptions==null?"NOT_CONNECTED":"AVAILABLE"));
        Object coverage=null;long totalFields=0,boundFields=0;try{
            String sql="SELECT COUNT(*) AS total,SUM(CASE WHEN EXISTS(SELECT 1 FROM da_metadata_t s WHERE s.tenant_id=f.tenant_id AND s.tid=f.data_standard_id AND s.is_del=0 AND s.publish_status=2) THEN 1 ELSE 0 END) AS bound FROM db_table_column_t f JOIN db_table_t t ON t.tenant_id=f.tenant_id AND t.tid=f.table_id AND COALESCE(t.is_del,0)=0 WHERE f.tenant_id=? AND COALESCE(f.is_del,0)=0";
            var r=department.isBlank()?db.one(sql,db.tenant()):db.one(sql+" AND t.org_id=?",db.tenant(),department);totalFields=((Number)r.get("total")).longValue();boundFields=r.get("bound")==null?0:((Number)r.get("bound")).longValue();coverage=totalFields==0?0d:Math.round((double)boundFields/totalFields*10000)/100.0;
        }catch(org.springframework.dao.DataAccessException ignored){}
        var coverageMetric=metric("STANDARD_COVERAGE","标准关联率",coverage,coverage==null?"UNAVAILABLE":"AVAILABLE");coverageMetric.put("numerator",boundFields);coverageMetric.put("denominator",totalFields);metrics.add(coverageMetric);metrics.add(metric("QUALITY_PASS_RATE","质量通过率",null,"NOT_CONNECTED"));
        List<Map<String,Object>> departments=new ArrayList<>();List<Map<String,Object>> rankings=new ArrayList<>();
        if(db.table("da_catalog_publication_head_t"))try{
            String sql="SELECT c.org_id,COUNT(*) AS value FROM da_catalog_publication_head_t h JOIN da_catalog_t c ON c.tenant_id=h.tenant_id AND c.tid=h.catalog_id AND COALESCE(c.is_del,0)=0 WHERE h.tenant_id=? AND h.availability='PUBLISHED'";
            var rows=department.isBlank()?db.rows(sql+" GROUP BY c.org_id ORDER BY value DESC LIMIT 6",db.tenant()):db.rows(sql+" AND c.org_id=? GROUP BY c.org_id ORDER BY value DESC LIMIT 6",db.tenant(),department);
            Set<String> departmentIds=new LinkedHashSet<>();for(var row:rows)addIfPresent(departmentIds,text(row,"org_id"));
            Map<String,String> departmentNames=new HashMap<>();for(var org:fetchIds("rm_org_t","id","id,name",departmentIds,db.tenant(),"COALESCE(deleted,0)=0"))departmentNames.put(text(org,"id"),text(org,"name"));
            for(var row:rows)departments.add(map("id",text(row,"org_id"),"name",departmentNames.getOrDefault(text(row,"org_id"),""),"value",row.get("value")));
            String hot="SELECT v.asset_id,COUNT(*) AS value FROM data_market_catalog_browse_t v JOIN da_catalog_publication_head_t h ON h.tenant_id=v.tenant_id AND h.catalog_id=v.asset_id AND h.availability='PUBLISHED' WHERE v.tenant_id=? AND v.asset_type='CATALOG'";
            List<Object> hotArgs=new ArrayList<>(List.of(db.tenant()));
            for(String bound:List.of("from","to"))if(!query.getOrDefault(bound,"").isBlank()){try{hot+=" AND v.browse_time"+(bound.equals("from")?">=":"<=")+"?";hotArgs.add(java.sql.Timestamp.from(java.time.Instant.parse(query.get(bound))));}catch(java.time.format.DateTimeParseException invalid){throw error(400,"INVALID_ARGUMENT","统计时间范围不正确");}}
            if(!department.isBlank()){hot+=" AND EXISTS(SELECT 1 FROM da_catalog_t c WHERE c.tenant_id=v.tenant_id AND c.tid=v.asset_id AND c.org_id=?)";hotArgs.add(department);}hot+=" GROUP BY v.asset_id ORDER BY value DESC LIMIT 6";
            var hotRows=db.rows(hot,hotArgs.toArray());var hotRefs=references(hotRows.stream().map(row->map("asset_type","CATALOG","asset_id",text(row,"asset_id"))).toList());
            for(var row:hotRows){var catalog=hotRefs.get("CATALOG\u0000"+text(row,"asset_id"));if(catalog==null)throw error(404,"NOT_FOUND","对象不存在或无权访问");rankings.add(map("object",catalog,"value",row.get("value")));}
        }catch(org.springframework.dao.DataAccessException ignored){}
        return map("layer",query.getOrDefault("layer","OVERVIEW"),"filters",map("departmentId",department),"metrics",metrics,"departments",departments,"rankings",rankings,"partial",metrics.stream().anyMatch(m->!"AVAILABLE".equals(m.get("status"))));
    }
    private Map<String,Object> metric(String code,String label,Object value,String status){return map("code",code,"label",label,"value",value,"status",status,"unit",code.endsWith("RATE")||code.endsWith("COVERAGE")?"%":"个","definitionVersion","1","asOf",iso(now()),"drilldownFilters",map());}
    private static boolean truth(Object value){return Set.of("1","true","YES","是").contains(String.valueOf(value));}
    private static String connection(String value){return switch(value.toUpperCase(Locale.ROOT)){case "SUCCESS","CONNECTED","NORMAL","1"->"CONNECTED";case "FAILED","ERROR","0"->"FAILED";default->"UNKNOWN";};}
    private static String parameterLocation(Map<String,Object> row){if("response".equalsIgnoreCase(text(row,"param_scope")))return "RESPONSE";String pos=text(row,"param_position").toUpperCase(Locale.ROOT);return Set.of("PATH","QUERY","HEADER","BODY").contains(pos)?pos:"QUERY";}
    private static String safeEndpoint(String value){if(value.isBlank())return "";try{java.net.URI uri=java.net.URI.create(value);return new java.net.URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),uri.getPath(),null,null).toString();}catch(Exception e){return "";}}
    private boolean visible(Map<String,Object> policy,String departmentId){return visible(policy,departmentId,new HashSet<>(db.organizations()));}
    private boolean visible(Map<String,Object> policy,String departmentId,Set<String> organizations){return switch(text(policy,"visibility")){case "TENANT"->true;case "DEPARTMENT"->organizations.contains(departmentId);case "AUTHORIZED_ORGS"->policy.get("allowedDepartmentIds") instanceof List<?> ids&&organizations.stream().anyMatch(ids::contains);default->false;};}
}

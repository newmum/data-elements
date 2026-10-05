package com.linewell.dataelement.feature.assetcenter.application;

import static com.linewell.dataelement.feature.assetcenter.application.AssetCenterStore.*;
import com.linewell.dataelement.feature.approval.application.ApprovalFlowService;
import com.linewell.dataelement.model.approval.ApprovalStartRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publication is an explicit frozen business contract, separate from directly usable registration records. */
@Service
public class AssetCenterPublicationService {
    private final AssetCenterStore db;
    private final AssetCenterQueryService query;
    private final AssetCenterWorkbenchService workbench;
    private final ApprovalFlowService approvals;
    public AssetCenterPublicationService(AssetCenterStore db,AssetCenterQueryService query,AssetCenterWorkbenchService workbench,ApprovalFlowService approvals){this.db=db;this.query=query;this.workbench=workbench;this.approvals=approvals;}

    @Transactional
    public Object attachments(String catalogId,Map<String,Object> input,String key){return db.idempotent("ATTACHMENTS:"+catalogId,key,input,()->{
        db.require("da_catalog_resource_binding_t","da_catalog_item_mapping_t");var catalog=db.owned("da_catalog_t",catalogId,true);db.manage(catalog);db.revision(catalog,input);List<Map<String,Object>> attachments=db.array(input.get("attachments"));if(attachments.size()>100)throw error(400,"INVALID_ARGUMENT","最多挂接100个资源");Set<String> unique=new HashSet<>(),primaryRoles=new HashSet<>();
        Set<String> catalogItems=new HashSet<>(db.rows("SELECT tid FROM da_catalog_item_t WHERE tenant_id=? AND catalog_id=? AND COALESCE(is_del,0)=0",db.tenant(),catalogId).stream().map(r->text(r,"tid")).toList());
        Set<String> resourceIds=new LinkedHashSet<>(),directoryVersionIds=new LinkedHashSet<>(),fieldIds=new LinkedHashSet<>();
        List<Map<String,Object>> resourceSelections=new ArrayList<>();boolean hasDirectory=false;
        for(var attachment:attachments){
            var resource=db.object(attachment.get("resource"));String type=text(attachment,"resourceType"),rid=text(resource,"id");
            if(!rid.isBlank()){resourceIds.add(rid);resourceSelections.add(map("asset_type",type.equals("DIRECTORY")?"CATALOG":type,"asset_id",rid));}
            if(type.equals("DIRECTORY")){hasDirectory=true;String version=text(attachment,"resourceVersionId");if(!version.isBlank())directoryVersionIds.add(version);}
            if(type.equals("TABLE"))for(var mapping:db.array(attachment.get("mappings"))){String fieldId=text(mapping,"resourceFieldId");if(!fieldId.isBlank())fieldIds.add(fieldId);}
        }
        Map<String,Map<String,Object>> resources=query.references(resourceSelections);
        Map<String,Map<String,Object>> existingBindings=new HashMap<>();
        if(!resourceIds.isEmpty()){
            String marks=String.join(",",Collections.nCopies(resourceIds.size(),"?"));List<Object> args=new ArrayList<>(List.of(db.tenant(),catalogId));args.addAll(resourceIds);
            for(var binding:db.rows("SELECT * FROM da_catalog_resource_binding_t WHERE tenant_id=? AND catalog_id=? AND resource_id IN ("+marks+")",args.toArray()))
                existingBindings.putIfAbsent(bindingKey(text(binding,"resource_type"),text(binding,"resource_id"),text(binding,"binding_role")),binding);
        }
        Map<String,String> publishedDirectoryVersions=new HashMap<>();
        for(var version:batchRows("SELECT tid,catalog_id FROM da_catalog_publication_t WHERE tenant_id=? AND tid IN (%s) AND status='PUBLISHED'",directoryVersionIds))publishedDirectoryVersions.put(text(version,"tid"),text(version,"catalog_id"));
        Map<String,String> fieldTables=new HashMap<>();
        for(var chunk:chunks(fieldIds,500))for(var field:batchRows("SELECT tid,table_id FROM db_table_column_t WHERE tenant_id=? AND tid IN (%s) AND COALESCE(is_del,0)=0",chunk))fieldTables.put(text(field,"tid"),text(field,"table_id"));
        Map<String,List<String>> directoryEdges=new HashMap<>();
        if(hasDirectory)for(var edge:db.rows("SELECT catalog_id,resource_id FROM da_catalog_resource_binding_t WHERE tenant_id=? AND resource_type='DIRECTORY' AND status='ACTIVE'",db.tenant())){
            String source=text(edge,"catalog_id");if(!catalogId.equals(source))directoryEdges.computeIfAbsent(source,ignored->new ArrayList<>()).add(text(edge,"resource_id"));
        }
        db.jdbc.update("UPDATE da_catalog_resource_binding_t SET status='DETACHED',updated_time=?,updated_by=?,revision=revision+1 WHERE tenant_id=? AND catalog_id=? AND status='ACTIVE'",now(),db.user(),db.tenant(),catalogId);
        int order=0;for(var a:attachments){
            var resource=db.object(a.get("resource"));String kind=required(resource,"kind",32),rid=required(resource,"id",64),type=required(a,"resourceType",32),role=required(a,"bindingRole",32);if(!type.equals(kind.equals("CATALOG")?"DIRECTORY":kind)||!Set.of("TABLE","API","DIRECTORY","FILE").contains(type)||!Set.of("SOURCE","DELIVERY","REFERENCE").contains(role))throw error(400,"INVALID_ARGUMENT","资源类型或挂接角色不正确");
            if(type.equals("FILE"))throw error(422,"PRECHECK_FAILED","文件版本服务尚未配置，不能创建不可锁定版本的文件挂接");if(!resources.containsKey(kind+"\u0000"+rid))throw error(404,"NOT_FOUND","对象不存在或无权访问");
            if(type.equals("DIRECTORY")){if(!role.equals("REFERENCE")||rid.equals(catalogId))throw error(400,"INVALID_ARGUMENT","目录只能作为其他目录的业务引用");checkCycle(catalogId,rid,directoryEdges,new HashSet<>(),0);String version=required(a,"resourceVersionId",64);if(!rid.equals(publishedDirectoryVersions.get(version)))throw error(400,"INVALID_ARGUMENT","引用目录必须锁定有效的已发布版本");}
            String compound=type+":"+rid+":"+role;if(!unique.add(compound))throw error(400,"INVALID_ARGUMENT","同资源及角色不能重复挂接");boolean primary=Boolean.TRUE.equals(a.get("primary"));if(primary&&!primaryRoles.add(role))throw error(400,"INVALID_ARGUMENT","每种挂接角色只能有一个主资源");String channel=text(a,"channel");if(role.equals("DELIVERY")&&!(type.equals("API")&&channel.equals("API")||type.equals("TABLE")&&Set.of("RESTRICTED_QUERY","TABLE_DISTRIBUTION").contains(channel)))throw error(400,"INVALID_ARGUMENT","交付渠道与物理资源类型不匹配");
            var existing=existingBindings.get(bindingKey(type,rid,role));String binding=existing==null?id():text(existing,"tid");int mappingVersion=existing==null?1:number(existing.get("mapping_version"),0)+1;
            if(existing==null){var row=db.audit(binding);row.putAll(map("catalog_id",catalogId,"resource_type",type,"resource_id",rid,"binding_role",role,"status","ACTIVE","primary_flag",primary?1:0,"mapping_version",mappingVersion,"display_order",++order,"resource_version_id",a.get("resourceVersionId"),"delivery_channel",channel,"idempotency_key",hash(key+compound)));db.insert("da_catalog_resource_binding_t",row);}else db.jdbc.update("UPDATE da_catalog_resource_binding_t SET status='ACTIVE',primary_flag=?,mapping_version=?,display_order=?,resource_version_id=?,delivery_channel=?,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",primary?1:0,mappingVersion,++order,a.get("resourceVersionId"),channel,now(),db.user(),db.tenant(),binding);
            for(var mapping:db.array(a.get("mappings"))){String item=required(mapping,"catalogItemId",64),path=required(mapping,"resourceFieldPath",1000),mappingKind=required(mapping,"mappingKind",32);if(!catalogItems.contains(item))throw error(400,"INVALID_ARGUMENT","数据项不属于当前目录");if(!Set.of("DIRECT","MANUAL").contains(mappingKind))throw error(422,"PRECHECK_FAILED","尚未接入已审核转换规则，当前仅支持直接或手工映射");String fieldId=text(mapping,"resourceFieldId");if(type.equals("TABLE")){if(fieldId.isBlank()||!rid.equals(fieldTables.get(fieldId)))throw error(400,"INVALID_ARGUMENT","字段映射不属于挂接数据表");}
                var row=db.audit(id());row.putAll(map("binding_id",binding,"catalog_item_id",item,"mapping_version",mappingVersion,"resource_field_id",fieldId.isBlank()?null:fieldId,"resource_field_path",path,"source_ref_hash",hash(fieldId+"|"+path),"mapping_kind",mappingKind,"status","ACTIVE"));db.insert("da_catalog_item_mapping_t",row);}
        }
        db.jdbc.update("UPDATE da_catalog_t SET revision=COALESCE(CAST(revision AS UNSIGNED),0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",now(),db.user(),db.tenant(),catalogId);workbench.event("CATALOG",catalogId,"ATTACHMENTS_REPLACED",key,map("count",attachments.size()));return map("id",catalogId,"revision",String.valueOf(number(catalog.get("revision"),0)+1),"changed",true,"projectionPending",false);
    });}

    @Transactional
    public Object create(String catalogId,Map<String,Object> input,String key){return db.idempotent("VERSION_CREATE:"+catalogId,key,input,()->{
        db.require("da_catalog_publication_t","da_catalog_publication_head_t","da_catalog_resource_snapshot_t");var catalog=db.owned("da_catalog_t",catalogId,true);db.manage(catalog);db.revision(catalog,input);required(input,"changeReason",2000);var policy=db.object(input.get("sharingPolicy"));policy(policy);
        var head=db.one("SELECT * FROM da_catalog_publication_head_t WHERE tenant_id=? AND catalog_id=? FOR UPDATE",db.tenant(),catalogId);int versionNo=head==null?1:number(head.get("next_version_no"),1);String versionId=id();
        String current=head==null?"":text(head,"current_publication_id"),base=text(input,"baseVersionId");
        if(!current.equals(base))throw error(409,"PUBLICATION_STALE","当前发布版本已变化，请刷新目录后重试");
        long count=query.count("da_catalog_item_t","catalog_id",catalogId);if(count>5000)throw error(422,"PRECHECK_FAILED","当前目录超过5000项，需拆分目录后发布");List<Map<String,Object>> items=query.dataItems("CATALOG",catalogId,1,5000),attachments=query.attachments(catalogId);
        var metadata=map("description",text(catalog,"asset_desc"),"departmentId",text(catalog,"org_id"),"catalogCode",text(catalog,"catalog_name_en"),"sharingPolicy",policy,"baseVersionId",base,"changeReason",input.get("changeReason"),"channels",attachments.stream().filter(a->"DELIVERY".equals(a.get("bindingRole"))).map(a->a.get("channel")).filter(Objects::nonNull).distinct().toList(),"references",attachments.stream().filter(a->"DIRECTORY".equals(a.get("resourceType"))).toList());
        var version=db.audit(versionId);version.putAll(map("catalog_id",catalogId,"version_no",versionNo,"status","DRAFT","title_snapshot",text(catalog,"catalog_name"),"metadata_snapshot",db.encode(metadata),"item_snapshot",db.encode(items),"governance_snapshot",db.encode(map("qualityStatus","NOT_CHECKED")),"visibility_scope",db.encode(policy),"source_revision",catalog.get("revision"),"idempotency_key",hash(key+catalogId)));db.insert("da_catalog_publication_t",version);
        for(var attachment:attachments){if("DIRECTORY".equals(attachment.get("resourceType")))continue;String snapshotId=id();var ref=db.object(attachment.get("resource"));var contract=new LinkedHashMap<>(attachment);contract.put("resourceSnapshotId",snapshotId);var snap=map("tid",snapshotId,"tenant_id",db.tenant(),"publication_id",versionId,"binding_id",attachment.get("id"),"resource_type",attachment.get("resourceType"),"resource_id",ref.get("id"),"mapping_version",attachment.get("mappingVersion"),"contract_hash",hash(db.encode(contract)),"contract_snapshot",db.encode(contract),"captured_at",now(),"created_by",db.user(),"created_time",now());db.insert("da_catalog_resource_snapshot_t",snap);}
        if(head==null){var row=db.audit(id());row.putAll(map("catalog_id",catalogId,"latest_publication_id",versionId,"next_version_no",versionNo+1,"availability","OFFLINE"));db.insert("da_catalog_publication_head_t",row);}else db.jdbc.update("UPDATE da_catalog_publication_head_t SET latest_publication_id=?,next_version_no=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND catalog_id=?",versionId,versionNo+1,now(),db.user(),db.tenant(),catalogId);
        workbench.event("CATALOG",catalogId,"VERSION_CREATED",key,map("versionId",versionId,"versionNo",versionNo));return versionSummary(db.owned("da_catalog_publication_t",versionId,false));
    });}
    @Transactional
    public Object precheck(String versionId,Map<String,Object> input,String key){return db.idempotent("VERSION_PRECHECK:"+versionId,key,input,()->{
        var row=db.owned("da_catalog_publication_t",versionId,true);db.manage(db.owned("da_catalog_t",text(row,"catalog_id"),true));db.revision(row,input);if(!Set.of("DRAFT","VALIDATED").contains(text(row,"status")))throw error(409,"INVALID_STATE","版本不可预检");var issues=new ArrayList<Map<String,Object>>();var items=db.array(row.get("item_snapshot"));if(items.isEmpty())issues.add(issue("NO_ITEMS","目录至少需要一个数据项"));var attachments=versionAttachments(row);if(attachments.stream().noneMatch(a->"DELIVERY".equals(a.get("bindingRole"))))issues.add(issue("NO_DELIVERY","目录至少需要一个交付资源"));
        Set<String> codes=new HashSet<>();for(var item:items){if(text(item,"code").isBlank()||!codes.add(text(item,"code")))issues.add(issue("INVALID_ITEM","数据项编码为空或重复"));}
        List<Map<String,Object>> selections=new ArrayList<>();
        for(var attachment:attachments){var ref=db.object(attachment.get("resource"));selections.add(map("asset_type",text(ref,"kind"),"asset_id",text(ref,"id")));}
        Map<String,Map<String,Object>> available=query.references(selections);
        for(var a:attachments){var ref=db.object(a.get("resource"));if(!available.containsKey(text(ref,"kind")+"\u0000"+text(ref,"id")))issues.add(issue("RESOURCE_MISSING","挂接资源已不可用"));if("DELIVERY".equals(a.get("bindingRole"))&&db.array(a.get("mappings")).isEmpty())issues.add(issue("MAPPING_MISSING","交付资源必须配置业务数据项到技术字段的映射"));}
        String hash=hash(db.encode(map("metadata",db.object(row.get("metadata_snapshot")),"items",items,"attachments",attachments))),checkId=id();Timestamp expires=Timestamp.from(Instant.now().plusSeconds(1800));boolean passed=issues.isEmpty();if(passed)db.jdbc.update("UPDATE da_catalog_publication_t SET status='VALIDATED',contract_hash=?,validated_at=?,check_id=?,check_expires_at=?,revision=revision+1,updated_time=? WHERE tenant_id=? AND tid=?",hash,now(),checkId,expires,now(),db.tenant(),versionId);
        return map("checkId",checkId,"versionId",versionId,"versionRevision",String.valueOf(number(row.get("revision"),0)+(passed?1:0)),"checkHash",hash,"passed",passed,"issues",issues,"expiresAt",iso(expires),"affectedSubscriptions",0);
    });}
    @Transactional
    public Object publish(String versionId,Map<String,Object> input,String key){return db.idempotent("VERSION_PUBLISH:"+versionId,key,input,()->{
        var row=db.owned("da_catalog_publication_t",versionId,true);String cat=text(row,"catalog_id");db.manage(db.owned("da_catalog_t",cat,true));db.revision(row,input);if(!"VALIDATED".equals(text(row,"status"))||!text(input,"checkHash").equals(text(row,"contract_hash"))||!text(input,"checkId").equals(text(row,"check_id"))||!(row.get("check_expires_at") instanceof Timestamp exp)||exp.before(now()))throw error(422,"PRECHECK_FAILED","请重新进行版本预检");if(row.get("request_id")!=null)throw error(409,"INVALID_STATE","版本已有发布申请");String flowCode=workbench.flowCode("CATALOG_PUBLICATION"),requestId=id();db.insert("res_catalog_publish_request",map("tid",requestId,"tenant_id",db.tenant(),"catalog_id",cat,"publication_id",versionId,"request_code","PUB-"+requestId,"request_status","IN_REVIEW","publish_scope",text(db.object(row.get("visibility_scope")),"visibility"),"request_reason",required(input,"reason",2000),"created_by",db.user(),"created_time",now(),"updated_by",db.user(),"updated_time",now(),"is_del",0,"revision",0));
        db.jdbc.update("UPDATE da_catalog_publication_t SET request_id=?,revision=revision+1,updated_time=? WHERE tenant_id=? AND tid=?",requestId,now(),db.tenant(),versionId);
        var command=new ApprovalStartRequest();command.setFlowType(flowCode);command.setApproveType("haoyuePublication");command.setFlowOrderId(requestId);command.setFlowParams(map("catalogId",cat,"publicationId",versionId));var flow=approvals.start(command);if(flow.get("instanceId")==null)throw error(503,"UPSTREAM_UNAVAILABLE","发布审批没有生成实例");db.jdbc.update("UPDATE res_catalog_publish_request SET flow_instance_id=? WHERE tenant_id=? AND tid=?",String.valueOf(flow.get("instanceId")),db.tenant(),requestId);
        workbench.event("CATALOG",cat,"PUBLICATION_SUBMITTED",key,map("publicationId",versionId,"requestId",requestId));return map("requestId",requestId,"catalogId",cat,"versionId",versionId,"status","IN_REVIEW","revision","0","submittedAt",iso(now()));
    });}
    @Transactional
    public Object listing(String catalogId,Map<String,Object> input,String key){return db.idempotent("LISTING:"+catalogId,key,input,()->{
        var catalog=db.owned("da_catalog_t",catalogId,true);db.manage(catalog);db.revision(catalog,input);var head=db.one("SELECT * FROM da_catalog_publication_head_t WHERE tenant_id=? AND catalog_id=? FOR UPDATE",db.tenant(),catalogId);if(head==null)throw error(409,"INVALID_STATE","尚无已发布目录版本");String action=required(input,"action",32),reason=required(input,"reason",2000);if(!Set.of("LIST","OFFLINE").contains(action))throw error(400,"INVALID_ARGUMENT","上下架操作不正确");if(action.equals("LIST")&&db.one("SELECT tid FROM da_catalog_publication_t WHERE tenant_id=? AND tid=? AND status='PUBLISHED'",db.tenant(),head.get("current_publication_id"))==null)throw error(409,"INVALID_STATE","仅审批通过的已发布版本可以上架");String status=action.equals("LIST")?"PUBLISHED":"OFFLINE";db.jdbc.update("UPDATE da_catalog_publication_head_t SET availability=?,withdrawal_reason=?,withdrawn_at=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND catalog_id=?",status,reason,action.equals("OFFLINE")?now():null,now(),db.user(),db.tenant(),catalogId);db.jdbc.update("UPDATE da_catalog_t SET revision=COALESCE(CAST(revision AS UNSIGNED),0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",now(),db.user(),db.tenant(),catalogId);workbench.event("CATALOG",catalogId,action,key,map("reason",reason));return map("id",catalogId,"revision",String.valueOf(number(catalog.get("revision"),0)+1),"changed",true,"status",status);
    });}
    public Map<String,Object> versions(String catalogId,Map<String,String> q){
        var catalog=db.owned("da_catalog_t",catalogId,false);db.require("da_catalog_publication_t");boolean manager=db.canManage(catalog);
        if(!manager&&!visiblePublished(catalog))throw error(404,"NOT_FOUND","目录版本不存在或无权访问");
        int pn=Math.max(1,number(q.get("pageNo"),1)),ps=Math.min(100,Math.max(1,number(q.get("pageSize"),15)));
        long count;List<Map<String,Object>> rows;
        if(manager){
            count=db.jdbc.queryForObject("SELECT COUNT(*) FROM da_catalog_publication_t WHERE tenant_id=? AND catalog_id=?",Long.class,db.tenant(),catalogId);
            rows=db.rows("SELECT * FROM da_catalog_publication_t WHERE tenant_id=? AND catalog_id=? ORDER BY version_no DESC LIMIT ? OFFSET ?",db.tenant(),catalogId,ps,(pn-1)*ps);
        }else{
            var visible=db.rows("SELECT * FROM da_catalog_publication_t WHERE tenant_id=? AND catalog_id=? AND status='PUBLISHED' ORDER BY version_no DESC",db.tenant(),catalogId).stream().filter(row->versionVisible(row,catalog)).toList();
            count=visible.size();rows=visible.stream().skip((long)(pn-1)*ps).limit(ps).toList();
        }
        var head=db.one("SELECT * FROM da_catalog_publication_head_t WHERE tenant_id=? AND catalog_id=?",db.tenant(),catalogId);
        Set<String> requestIds=new LinkedHashSet<>();for(var row:rows)if(row.get("request_id")!=null)requestIds.add(text(row,"request_id"));
        Map<String,Map<String,Object>> requests=new HashMap<>();
        if(!requestIds.isEmpty()){
            String marks=String.join(",",Collections.nCopies(requestIds.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(requestIds);
            for(var request:db.rows("SELECT tid,request_status FROM res_catalog_publish_request WHERE tenant_id=? AND tid IN ("+marks+")",args.toArray()))requests.put(text(request,"tid"),request);
        }
        return map("items",rows.stream().map(row->versionSummary(row,head,requests.get(text(row,"request_id")))).toList(),"page",query.page(pn,ps,count,"versionNo","desc"));
    }
    public Map<String,Object> version(String catalogId,String versionId){
        var catalog=db.owned("da_catalog_t",catalogId,false);var row=db.owned("da_catalog_publication_t",versionId,false);
        if(!catalogId.equals(text(row,"catalog_id")))throw error(404,"NOT_FOUND","版本不属于该目录");
        if(!db.canManage(catalog)){
            String requestId=text(row,"request_id");boolean reviewer=!requestId.isBlank()&&!workbench.tasks(requestId).isEmpty();
            if(!reviewer&&(!"PUBLISHED".equals(text(row,"status"))||!visiblePublished(catalog)||!versionVisible(row,catalog)))throw error(404,"NOT_FOUND","目录版本不存在或无权访问");
        }
        var meta=db.object(row.get("metadata_snapshot"));return map("version",versionSummary(row),"catalogName",text(row,"title_snapshot"),"items",db.array(row.get("item_snapshot")),"attachments",versionAttachments(row),"sharingPolicy",meta.get("sharingPolicy"),"changes",changes(row,db.canManage(catalog)),"allowedActions",text(row,"status").equals("DRAFT")?List.of("PRECHECK"):text(row,"status").equals("VALIDATED")&&row.get("request_id")==null?List.of("PRECHECK","PUBLISH"):List.of("VIEW"));
    }
    private boolean visiblePublished(Map<String,Object> catalog){
        var head=db.one("SELECT current_publication_id,availability FROM da_catalog_publication_head_t WHERE tenant_id=? AND catalog_id=?",db.tenant(),catalog.get("tid"));
        if(head==null||!"PUBLISHED".equals(text(head,"availability"))||head.get("current_publication_id")==null)return false;
        var current=db.one("SELECT visibility_scope FROM da_catalog_publication_t WHERE tenant_id=? AND tid=? AND status='PUBLISHED'",db.tenant(),head.get("current_publication_id"));
        if(current==null)return false;var policy=db.object(current.get("visibility_scope"));return switch(text(policy,"visibility")){
            case "TENANT"->true;
            case "DEPARTMENT"->db.organizations().contains(text(catalog,"org_id"));
            case "AUTHORIZED_ORGS"->policy.get("allowedDepartmentIds") instanceof List<?> ids&&db.organizations().stream().anyMatch(ids::contains);
            default->false;
        };
    }
    private boolean versionVisible(Map<String,Object> version,Map<String,Object> catalog){
        var policy=db.object(version.get("visibility_scope"));return switch(text(policy,"visibility")){
            case "TENANT"->true;
            case "DEPARTMENT"->db.organizations().contains(text(catalog,"org_id"));
            case "AUTHORIZED_ORGS"->policy.get("allowedDepartmentIds") instanceof List<?> ids&&db.organizations().stream().anyMatch(ids::contains);
            default->false;
        };
    }
    private Map<String,Object> versionSummary(Map<String,Object> row){var head=db.one("SELECT * FROM da_catalog_publication_head_t WHERE tenant_id=? AND catalog_id=?",db.tenant(),row.get("catalog_id"));var req=row.get("request_id")==null?null:db.one("SELECT request_status FROM res_catalog_publish_request WHERE tenant_id=? AND tid=?",db.tenant(),row.get("request_id"));return versionSummary(row,head,req);}
    private Map<String,Object> versionSummary(Map<String,Object> row,Map<String,Object> head,Map<String,Object> req){return map("id",text(row,"tid"),"catalogId",text(row,"catalog_id"),"versionNo",row.get("version_no"),"status",row.get("status"),"revision",String.valueOf(row.get("revision")),"createdAt",iso(row.get("created_time")),"updatedAt",iso(row.get("updated_time")),"publishedAt",iso(row.get("published_at")),"snapshotHash",row.get("contract_hash"),"isCurrent",head!=null&&Objects.equals(head.get("current_publication_id"),row.get("tid")),"publicationRequestId",row.get("request_id"),"publicationRequestStatus",req==null?"NOT_SUBMITTED":req.get("request_status"),"listingAvailability",head==null?"OFFLINE":head.get("availability"));}
    private List<Map<String,Object>> changes(Map<String,Object> row,boolean canSeeDrafts){
        var meta=db.object(row.get("metadata_snapshot"));String baseId=text(meta,"baseVersionId");
        String visible=canSeeDrafts?"":" AND status='PUBLISHED'";
        Map<String,Object> base=baseId.isBlank()?null:db.one("SELECT * FROM da_catalog_publication_t WHERE tenant_id=? AND catalog_id=? AND tid=?"+visible,db.tenant(),row.get("catalog_id"),baseId);
        if(base==null&&number(row.get("version_no"),1)>1)base=db.one("SELECT * FROM da_catalog_publication_t WHERE tenant_id=? AND catalog_id=? AND version_no<?"+visible+" ORDER BY version_no DESC LIMIT 1",db.tenant(),row.get("catalog_id"),row.get("version_no"));
        var changes=new ArrayList<Map<String,Object>>();var previous=base==null?Map.<String,Object>of():db.object(base.get("metadata_snapshot"));
        difference(changes,"版本说明",null,meta.get("changeReason"),"COMPATIBLE");
        difference(changes,"目录名称",base==null?null:base.get("title_snapshot"),row.get("title_snapshot"),"REVIEW_REQUIRED");
        difference(changes,"目录说明",previous.get("description"),meta.get("description"),"COMPATIBLE");
        difference(changes,"共享范围",db.object(previous.get("sharingPolicy")).get("visibility"),db.object(meta.get("sharingPolicy")).get("visibility"),"REVIEW_REQUIRED");
        difference(changes,"共享方式",db.object(previous.get("sharingPolicy")).get("shareType"),db.object(meta.get("sharingPolicy")).get("shareType"),"REVIEW_REQUIRED");
        var oldItems=new LinkedHashMap<String,Map<String,Object>>();if(base!=null)for(var item:db.array(base.get("item_snapshot")))oldItems.put(text(item,"code"),item);
        var newItems=new LinkedHashMap<String,Map<String,Object>>();for(var item:db.array(row.get("item_snapshot")))newItems.put(text(item,"code"),item);
        for(var entry:oldItems.entrySet())if(!newItems.containsKey(entry.getKey()))difference(changes,"数据项 / "+entry.getKey(),text(entry.getValue(),"name"),null,"BREAKING");
        for(var entry:newItems.entrySet()){
            var before=oldItems.get(entry.getKey());var after=entry.getValue();String path="数据项 / "+entry.getKey();
            if(before==null){difference(changes,path,null,text(after,"name"),"COMPATIBLE");continue;}
            difference(changes,path+" / 名称",before.get("name"),after.get("name"),"COMPATIBLE");
            difference(changes,path+" / 类型",before.get("dataType"),after.get("dataType"),"BREAKING");
            difference(changes,path+" / 长度",before.get("length"),after.get("length"),"REVIEW_REQUIRED");
            difference(changes,path+" / 可空",before.get("nullable"),after.get("nullable"),"REVIEW_REQUIRED");
            difference(changes,path+" / 主键",before.get("primaryKey"),after.get("primaryKey"),"BREAKING");
            difference(changes,path+" / 说明",before.get("definition"),after.get("definition"),"COMPATIBLE");
        }
        var oldResources=new LinkedHashMap<String,Map<String,Object>>();if(base!=null)for(var attachment:versionAttachments(base))oldResources.put(resourceKey(attachment),attachment);
        var newResources=new LinkedHashMap<String,Map<String,Object>>();for(var attachment:versionAttachments(row))newResources.put(resourceKey(attachment),attachment);
        for(var entry:oldResources.entrySet())if(!newResources.containsKey(entry.getKey()))difference(changes,"挂接资源 / "+entry.getKey(),text(db.object(entry.getValue().get("resource")),"name"),null,"BREAKING");
        for(var entry:newResources.entrySet()){
            var before=oldResources.get(entry.getKey());var after=entry.getValue();String path="挂接资源 / "+entry.getKey();
            if(before==null){difference(changes,path,null,text(db.object(after.get("resource")),"name"),"REVIEW_REQUIRED");continue;}
            difference(changes,path+" / 交付渠道",before.get("channel"),after.get("channel"),"REVIEW_REQUIRED");
            difference(changes,path+" / 字段映射数",db.array(before.get("mappings")).size(),db.array(after.get("mappings")).size(),"REVIEW_REQUIRED");
        }
        return changes;
    }
    private String resourceKey(Map<String,Object> attachment){var ref=db.object(attachment.get("resource"));return text(attachment,"resourceType")+":"+text(ref,"id")+":"+text(attachment,"bindingRole");}
    private void difference(List<Map<String,Object>> changes,String path,Object before,Object after,String compatibility){
        if(Objects.equals(before,after))return;
        changes.add(map("path",path,"kind",before==null?"ADDED":after==null?"REMOVED":"MODIFIED","before",before==null?null:String.valueOf(before),"after",after==null?null:String.valueOf(after),"compatibility",compatibility));
    }
    private List<Map<String,Object>> versionAttachments(Map<String,Object> row){List<Map<String,Object>> result=new ArrayList<>();for(var snap:db.rows("SELECT tid,contract_snapshot FROM da_catalog_resource_snapshot_t WHERE tenant_id=? AND publication_id=? ORDER BY created_time,tid",db.tenant(),row.get("tid"))){var a=db.object(snap.get("contract_snapshot"));a.put("resourceSnapshotId",text(snap,"tid"));result.add(a);}result.addAll(db.array(db.object(row.get("metadata_snapshot")).get("references")));return result;}
    private static String bindingKey(String type,String resourceId,String role){return type+"\u0000"+resourceId+"\u0000"+role;}
    private List<Map<String,Object>> batchRows(String sqlTemplate,Set<String> ids){
        if(ids.isEmpty())return List.of();String marks=String.join(",",Collections.nCopies(ids.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(ids);
        return db.rows(sqlTemplate.formatted(marks),args.toArray());
    }
    private static List<Set<String>> chunks(Set<String> ids,int size){
        List<Set<String>> result=new ArrayList<>();Set<String> chunk=new LinkedHashSet<>();
        for(String id:ids){chunk.add(id);if(chunk.size()==size){result.add(chunk);chunk=new LinkedHashSet<>();}}
        if(!chunk.isEmpty())result.add(chunk);return result;
    }
    void checkCycle(String original,String current,Map<String,List<String>> edges,Set<String> seen,int depth){if(depth>50)throw error(422,"PRECHECK_FAILED","目录引用层级过深");if(!seen.add(current))return;for(String next:edges.getOrDefault(current,List.of())){if(original.equals(next))throw error(400,"INVALID_ARGUMENT","目录引用不能形成循环");checkCycle(original,next,edges,seen,depth+1);}}
    private void policy(Map<String,Object> p){if(!Set.of("DEPARTMENT","AUTHORIZED_ORGS","TENANT").contains(text(p,"visibility"))||!Set.of("CONDITIONAL","UNCONDITIONAL","PROHIBITED").contains(text(p,"shareType"))||!Set.of("NONE","MASKED_SAMPLE").contains(text(p,"previewMode")))throw error(400,"INVALID_ARGUMENT","共享策略不完整");if(text(p,"visibility").equals("AUTHORIZED_ORGS")&&(!(p.get("allowedDepartmentIds") instanceof List<?> ids)||ids.isEmpty()))throw error(400,"INVALID_ARGUMENT","请选择可见部门");}
    private Map<String,Object> issue(String code,String message){return map("code",code,"message",message,"severity","ERROR","path","");}
}

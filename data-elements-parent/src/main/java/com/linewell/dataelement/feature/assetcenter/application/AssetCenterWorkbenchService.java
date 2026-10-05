package com.linewell.dataelement.feature.assetcenter.application;

import static com.linewell.dataelement.feature.assetcenter.application.AssetCenterStore.*;
import com.linewell.dataelement.feature.approval.application.ApprovalFlowService;
import com.linewell.dataelement.feature.approval.application.ApprovalPrincipalService;
import com.linewell.dataelement.feature.identity.application.IdentityDirectoryService;
import com.linewell.dataelement.model.approval.ApprovalStartRequest;
import com.linewell.dataelement.model.approval.ApprovalRevokeRequest;
import com.linewell.dataelement.model.approval.ApprovalHandleRequest;
import com.linewell.dataelement.platform.tenant.application.TenantSessions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetCenterWorkbenchService {
    private final AssetCenterStore db;
    private final AssetCenterQueryService queries;
    private final ApprovalFlowService approvals;
    private final ApprovalPrincipalService principals;
    private final IdentityDirectoryService directory;
    public AssetCenterWorkbenchService(AssetCenterStore db,AssetCenterQueryService queries,ApprovalFlowService approvals,ApprovalPrincipalService principals,IdentityDirectoryService directory){this.db=db;this.queries=queries;this.approvals=approvals;this.principals=principals;this.directory=directory;}

    @Transactional
    public Object favorite(String kind,String objectId,Map<String,Object> input,String key){return db.idempotent("FAVORITE_SET",key,input,()->{
        var row=db.owned(queries.table(kind),objectId,true);var ref=queries.ref(kind,objectId);
        if(!db.column("data_market_collect_t","asset_type"))throw error(503,"ASSET_SCHEMA_NOT_READY","请升级个人收藏表结构");
        if(!(input.get("favorited") instanceof Boolean))throw error(400,"INVALID_ARGUMENT","favorited必须为布尔值");
        boolean enabled=Boolean.TRUE.equals(input.get("favorited"));
        if(enabled){Long count=db.jdbc.queryForObject("SELECT COUNT(*) FROM data_market_collect_t WHERE tenant_id=? AND apply_user_id=? AND asset_type=? AND asset_id=?",Long.class,db.tenant(),db.user(),kind,objectId);if(count==0)db.insert("data_market_collect_t",map("tid",id(),"tenant_id",db.tenant(),"apply_user_id",db.user(),"catalog_id",kind.equals("CATALOG")?objectId:null,"asset_type",kind,"asset_id",objectId,"created_time",now(),"updated_time",now()));}
        else db.jdbc.update("DELETE FROM data_market_collect_t WHERE tenant_id=? AND apply_user_id=? AND asset_type=? AND asset_id=?",db.tenant(),db.user(),kind,objectId);
        return map("object",ref,"favorited",enabled,"updatedAt",iso(now()));
    });}
    @Transactional
    public Object visits(Map<String,Object> input,String key){return db.idempotent("VISITS",key,input,()->{
        if(!db.column("data_market_catalog_browse_t","asset_type"))throw error(503,"ASSET_SCHEMA_NOT_READY","请升级个人足迹表结构");
        String action=required(input,"action",32);if(action.equals("CLEAR_ALL"))return map("changed",db.jdbc.update("DELETE FROM data_market_catalog_browse_t WHERE tenant_id=? AND apply_user_id=?",db.tenant(),db.user()));
        if(action.equals("CLEAR_SELECTED")){Object raw=input.get("visitIds");if(!(raw instanceof List<?> ids)||ids.size()>100)throw error(400,"INVALID_ARGUMENT","请选择最多100条足迹");int changed=0;for(Object visitId:ids)changed+=db.jdbc.update("DELETE FROM data_market_catalog_browse_t WHERE tenant_id=? AND apply_user_id=? AND tid=?",db.tenant(),db.user(),String.valueOf(visitId));return map("changed",changed);}
        if(!action.equals("RECORD"))throw error(400,"INVALID_ARGUMENT","未知足迹操作");var ref=db.object(input.get("object"));String kind=required(ref,"kind",32),objectId=required(ref,"id",64);db.owned(queries.table(kind),objectId,true);
        var prior=db.one("SELECT tid FROM data_market_catalog_browse_t WHERE tenant_id=? AND apply_user_id=? AND asset_type=? AND asset_id=? ORDER BY browse_time DESC LIMIT 1",db.tenant(),db.user(),kind,objectId);String visitId=prior==null?id():text(prior,"tid");
        if(prior==null)db.insert("data_market_catalog_browse_t",map("tid",visitId,"tenant_id",db.tenant(),"apply_user_id",db.user(),"catalog_id",kind.equals("CATALOG")?objectId:null,"asset_type",kind,"asset_id",objectId,"browse_time",now(),"visit_count",1));else db.jdbc.update("UPDATE data_market_catalog_browse_t SET browse_time=?,visit_count=visit_count+1 WHERE tenant_id=? AND tid=?",now(),db.tenant(),visitId);
        return map("changed",1,"visitId",visitId);
    });}

    @Transactional
    public Object draft(Map<String,Object> input,String key,boolean demand){return db.idempotent(demand?"DEMAND_DRAFT":"SUBSCRIPTION_DRAFT",key,input,()->{
        db.require("data_apply_form_t",demand?"da_demand_case_t":"da_apply_scope_t");if(!db.column("data_apply_form_t","asset_payload_json"))throw error(503,"ASSET_SCHEMA_NOT_READY","请升级共享申请表结构");
        String title=required(input,"title",255),existing=text(input,demand?"demandId":"subscriptionId"),formId=existing.isBlank()?id():existing;
        String org=text(input,demand?"departmentId":"actingDepartmentId");if(org.isBlank()){var orgs=db.organizations();if(orgs.size()==1)org=orgs.get(0);}if(!org.isBlank())db.organization(org);
        String handlerOrg=demand?text(input,"handlerDepartmentId"):"";
        if(demand&&!handlerOrg.isBlank()&&directory.findOrganizations(List.of(handlerOrg)).stream().noneMatch(entry->handlerOrg.equals(entry.getId())))throw error(400,"INVALID_ARGUMENT","对接处理部门不存在");
        Map<String,Object> payload=new LinkedHashMap<>(input);payload.remove("tenantId");payload.remove("tenant_id");payload.remove("expectedRevision");if(!demand&&!"CATALOG_SUBSCRIPTION".equals(text(input,"requestType")))throw error(400,"INVALID_ARGUMENT","申请类型不正确");
        if(!text(input,"applicationId").isBlank())db.owned("sym_application_t",text(input,"applicationId"),false);
        String catalogs=demand?"":db.array(input.get("resources")).stream().map(r->text(r,"catalogId")).distinct().reduce((a,b)->a+","+b).orElse("");if(catalogs.length()>500)throw error(400,"INVALID_ARGUMENT","申请目录过多，请分批提交");
        if(existing.isBlank()){
            if(!"0".equals(required(input,"expectedRevision",64)))throw error(409,"REVISION_CONFLICT","新增草稿版本必须为0");
            var row=db.audit(formId);row.putAll(map("apply_user_id",db.user(),"apply_org_id",org,"apply_name",title,"catalog_ids",catalogs,"type",demand?"need":"CATALOG_SUBSCRIPTION","flow_status",0,"is_del",0,"asset_payload_json",db.encode(payload)));db.insert("data_apply_form_t",row);
            if(demand){var caseRow=db.audit(id());caseRow.putAll(map("apply_form_id",formId,"original_apply_type","need","canonical_type","DATA_DEMAND","requesting_org_id",org,"owner_org_id",handlerOrg.isBlank()?null:handlerOrg,"status","DRAFT","requested_resource_types",db.encode(input.getOrDefault("resourceKinds",List.of())),"demand_snapshot",db.encode(payload),"idempotency_key",hash(key)));db.insert("da_demand_case_t",caseRow);}
        }else{
            var prior=form(formId,true);db.revision(prior,input);String status=demand?text(demandCase(formId,true),"status"):status(prior);
            if(!Set.of("DRAFT","WITHDRAWN","REJECTED","RETURNED").contains(status))throw error(409,"INVALID_STATE","当前状态不能修改申请");
            if(demand!=text(prior,"type").equals("need"))throw error(400,"INVALID_ARGUMENT","申请类型不匹配");
            db.jdbc.update("UPDATE data_apply_form_t SET apply_name=?,apply_org_id=?,catalog_ids=?,asset_payload_json=?,flow_status=0,revision=COALESCE(CAST(revision AS UNSIGNED),0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",title,org,catalogs,db.encode(payload),now(),db.user(),db.tenant(),formId);
            if(demand)db.jdbc.update("UPDATE da_demand_case_t SET requesting_org_id=?,owner_org_id=?,requested_resource_types=?,demand_snapshot=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND apply_form_id=?",org,handlerOrg.isBlank()?null:handlerOrg,db.encode(input.getOrDefault("resourceKinds",List.of())),db.encode(payload),now(),db.user(),db.tenant(),formId);
        }
        return demand?demand(formId):subscription(formId);
    });}

    @Transactional
    public Object submit(String formId,Map<String,Object> input,String key,boolean demand){return db.idempotent(demand?"DEMAND_SUBMIT":"SUBSCRIPTION_SUBMIT",key,input,()->{
        var row=form(formId,true);db.revision(row,input);var payload=db.object(row.get("asset_payload_json"));String org=text(row,"apply_org_id");db.organization(org);
        if(demand){var c=demandCase(formId,true);if(!"DRAFT".equals(text(c,"status")))throw error(409,"INVALID_STATE","需求已提交");required(payload,"scenario",4000);if(org.isBlank())throw error(400,"INVALID_ARGUMENT","请选择申请部门");if(text(c,"owner_org_id").isBlank())throw error(400,"INVALID_ARGUMENT","请选择对接处理部门");db.jdbc.update("UPDATE da_demand_case_t SET status='SUBMITTED',revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND apply_form_id=?",now(),db.user(),db.tenant(),formId);db.jdbc.update("UPDATE data_apply_form_t SET revision=COALESCE(CAST(revision AS UNSIGNED),0)+1,updated_time=?,submitted_at=? WHERE tenant_id=? AND tid=?",now(),now(),db.tenant(),formId);event("DEMAND",formId,"SUBMITTED",key,map());return demand(formId);}
        if(!Set.of("DRAFT","REJECTED","RETURNED","WITHDRAWN").contains(status(row)))throw error(409,"INVALID_STATE","申请当前不能提交");
        String purpose=required(payload,"purpose",2000);required(payload,"businessScenario",4000);Timestamp from=date(payload,"validFrom"),until=date(payload,"validUntil");if(!until.after(from)||!until.after(now()))throw error(400,"INVALID_ARGUMENT","申请有效期不正确");
        List<Map<String,Object>> resources=db.array(payload.get("resources"));if(resources.isEmpty()||resources.size()>100)throw error(400,"INVALID_ARGUMENT","请选择申请资源与字段");
        Set<String> publicationIds=new LinkedHashSet<>(),catalogIds=new LinkedHashSet<>(),snapshotIds=new LinkedHashSet<>();
        for(var resource:resources){publicationIds.add(text(resource,"versionId"));catalogIds.add(text(resource,"catalogId"));snapshotIds.add(text(resource,"resourceSnapshotId"));}
        Map<String,Map<String,Object>> versions=new HashMap<>(),catalogs=new HashMap<>(),snapshots=new HashMap<>();
        for(var version:rowsByIds("SELECT p.* FROM da_catalog_publication_t p JOIN da_catalog_publication_head_t h ON h.tenant_id=p.tenant_id AND h.current_publication_id=p.tid WHERE p.tenant_id=? AND p.tid IN (%s) AND p.status='PUBLISHED' AND h.availability='PUBLISHED'",publicationIds))versions.put(text(version,"tid"),version);
        for(var catalog:rowsByIds("SELECT * FROM da_catalog_t WHERE tenant_id=? AND tid IN (%s) AND COALESCE(is_del,0)=0",catalogIds))catalogs.put(text(catalog,"tid"),catalog);
        for(var snapshot:rowsByIds("SELECT * FROM da_catalog_resource_snapshot_t WHERE tenant_id=? AND tid IN (%s)",snapshotIds))snapshots.put(text(snapshot,"tid"),snapshot);
        String flowCode=flowCode("CATALOG_SUBSCRIPTION");int submission=number(row.get("submission_version"),0)+1;int ordinal=0;
        for(var resource:resources){
            String pub=required(resource,"versionId",64),cat=required(resource,"catalogId",64),snapshot=required(resource,"resourceSnapshotId",64),binding=required(resource,"attachmentId",64),channel=required(resource,"channel",32);
            var version=versions.get(pub);if(version==null||!cat.equals(text(version,"catalog_id")))throw error(409,"PUBLICATION_STALE","目录已下架或发布版本已变化，请刷新后重新选择");
            var catalog=catalogs.get(cat);if(catalog==null)throw error(404,"NOT_FOUND","对象不存在或无权访问");var policy=db.object(version.get("visibility_scope"));String visibility=text(policy,"visibility");
            boolean visible="TENANT".equals(visibility)||"DEPARTMENT".equals(visibility)&&org.equals(text(catalog,"org_id"))||"AUTHORIZED_ORGS".equals(visibility)&&policy.get("allowedDepartmentIds") instanceof List<?> allowed&&allowed.contains(org);
            if(!visible||"PROHIBITED".equals(text(policy,"shareType")))throw error(403,"FORBIDDEN","当前申请部门不在目录共享范围内");
            var snap=snapshots.get(snapshot);if(snap==null||!pub.equals(text(snap,"publication_id"))||!binding.equals(text(snap,"binding_id")))throw error(400,"INVALID_ARGUMENT","申请资源不属于该目录发布版本");var contract=db.object(snap.get("contract_snapshot"));if(!"DELIVERY".equals(text(contract,"bindingRole"))||!channel.equals(text(contract,"channel")))throw error(400,"INVALID_ARGUMENT","该挂接资源不是所选交付渠道");
            Object idsRaw=resource.get("itemIds");if(!(idsRaw instanceof List<?> itemIds)||itemIds.isEmpty())throw error(400,"INVALID_ARGUMENT","请选择申请字段");var validIds=db.array(version.get("item_snapshot")).stream().map(i->text(i,"id")).collect(java.util.stream.Collectors.toSet());if(!validIds.containsAll(itemIds))throw error(400,"INVALID_ARGUMENT","申请字段不属于已发布目录");
            var scope=db.audit(id());scope.putAll(map("apply_form_id",formId,"submission_version",submission,"scope_no",++ordinal,"catalog_id",cat,"publication_id",pub,"resource_snapshot_id",snapshot,"delivery_channel",channel.equals("API")?"API":channel.equals("FILE")?"FILE":"TABLE","purpose",purpose,"requested_scope",db.encode(map("itemIds",itemIds,"purpose",purpose,"tableDeliveryMode",channel.equals("RESTRICTED_QUERY")?"QUERY":"DISTRIBUTION")),"requested_from",from,"requested_until",until,"application_snapshot",db.encode(map("applicantUserId",db.user(),"departmentId",org,"applicationId",payload.get("applicationId"),"purpose",purpose,"businessScenario",payload.get("businessScenario"),"submittedAt",iso(now()))),"status","SUBMITTED","submitted_at",now(),"idempotency_key",hash(key+":"+ordinal)));db.insert("da_apply_scope_t",scope);
        }
        var start=new ApprovalStartRequest();start.setFlowType(flowCode);start.setApproveType("haoyueSubscription");start.setFlowOrderId(formId);start.setFlowParams(map("submissionVersion",submission));var flow=approvals.start(start);if(flow.get("instanceId")==null)throw error(503,"UPSTREAM_UNAVAILABLE","审批流程没有生成实例，申请尚未提交");
        db.jdbc.update("UPDATE data_apply_form_t SET flow_status=1,flow_order_id=?,flow_instance_id=?,submission_version=?,submitted_at=?,revision=COALESCE(CAST(revision AS UNSIGNED),0)+1,updated_time=? WHERE tenant_id=? AND tid=?",formId,String.valueOf(flow.get("instanceId")),submission,now(),now(),db.tenant(),formId);
        event("SUBSCRIPTION",formId,"SUBMITTED",key,map("submissionVersion",submission,"instanceId",String.valueOf(flow.get("instanceId"))));return subscription(formId);
    });}
    @Transactional
    public Object withdraw(String id,Map<String,Object> input,String key){return db.idempotent("SUBSCRIPTION_WITHDRAW",key,input,()->{var row=form(id,true);db.revision(row,input);if(!status(row).equals("IN_REVIEW"))throw error(409,"INVALID_STATE","仅审批中的申请可撤回");var cmd=new ApprovalRevokeRequest();cmd.setTid(text(row,"flow_instance_id"));cmd.setFlowOrderId(id);cmd.setMessage(text(input,"reason"));approvals.revoke(cmd);db.jdbc.update("UPDATE da_apply_scope_t SET status='WITHDRAWN',updated_time=?,revision=revision+1 WHERE tenant_id=? AND apply_form_id=? AND submission_version=? AND status='SUBMITTED'",now(),db.tenant(),id,row.get("submission_version"));db.jdbc.update("UPDATE data_apply_form_t SET flow_status=4,revision=COALESCE(CAST(revision AS UNSIGNED),0)+1,updated_time=? WHERE tenant_id=? AND tid=?",now(),db.tenant(),id);event("SUBSCRIPTION",id,"WITHDRAWN",key,map());return subscription(id);});}

    public Map<String,Object> subscription(String id){var row=db.owned("data_apply_form_t",id,false);boolean owner=db.user().equals(text(row,"apply_user_id"));if(!owner&&tasks(id).isEmpty())throw error(404,"NOT_FOUND","申请不存在或无权访问");var draft=db.object(row.get("asset_payload_json"));String status=status(row);List<Map<String,Object>> scopes=db.table("da_apply_scope_t")?db.rows("SELECT * FROM da_apply_scope_t WHERE tenant_id=? AND apply_form_id=? AND submission_version=? ORDER BY scope_no",db.tenant(),id,number(row.get("submission_version"),0)).stream().map(this::scopeDto).toList():List.of();return map("id",id,"revision",revisionValue(row),"requestType","CATALOG_SUBSCRIPTION","title",text(row,"apply_name"),"status",status,"applicantUserId",text(row,"apply_user_id"),"departmentId",text(row,"apply_org_id"),"resources",draft.getOrDefault("resources",List.of()),"updatedAt",iso(row.get("updated_time")),"allowedActions",owner?(status.equals("IN_REVIEW")?List.of("VIEW","WITHDRAW"):Set.of("DRAFT","REJECTED","RETURNED","WITHDRAWN").contains(status)?List.of("VIEW","EDIT","SUBMIT"):List.of("VIEW","DELIVERY")):List.of("VIEW"),"submissionVersion",number(row.get("submission_version"),0),"scopes",scopes,"draft",draft);}
    public Map<String,Object> progress(String id){var sub=subscription(id);return map("subscription",sub,"events",events("SUBSCRIPTION",id),"activeTasks",tasks(id),"deliveryStatus",sub.get("status").equals("APPROVED")?"PROVISIONING":"PREPARING");}
    public Object delivery(String id){
        var form=form(id,false);db.require("da_apply_scope_t","da_authorization_scope_t");
        int submission=number(form.get("submission_version"),0);
        var scopes=db.rows("SELECT * FROM da_apply_scope_t WHERE tenant_id=? AND apply_form_id=? AND submission_version=? ORDER BY scope_no",db.tenant(),id,submission);
        Set<String> scopeIds=new LinkedHashSet<>();for(var scope:scopes)scopeIds.add(text(scope,"tid"));
        Map<String,Map<String,Object>> latestAuth=new HashMap<>();
        if(!scopeIds.isEmpty()){
            String marks=String.join(",",Collections.nCopies(scopeIds.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(scopeIds);
            for(var auth:db.rows("SELECT * FROM da_authorization_scope_t WHERE tenant_id=? AND apply_scope_id IN ("+marks+") ORDER BY authorization_version DESC",args.toArray()))
                latestAuth.putIfAbsent(text(auth,"apply_scope_id"),auth);
        }
        Set<String> apiAuthIds=new LinkedHashSet<>();for(var auth:latestAuth.values())if("API".equals(text(auth,"delivery_channel"))&&!text(auth,"api_authorization_id").isBlank())apiAuthIds.add(text(auth,"api_authorization_id"));
        Map<String,String> gatewayStatuses=new HashMap<>();
        if(!apiAuthIds.isEmpty()){
            String marks=String.join(",",Collections.nCopies(apiAuthIds.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(apiAuthIds);
            for(var api:db.rows("SELECT tid,gateway_sync_status FROM data_api_authorization_t WHERE tenant_id=? AND tid IN ("+marks+")",args.toArray()))
                gatewayStatuses.put(text(api,"tid"),text(api,"gateway_sync_status"));
        }
        List<Map<String,Object>> items=new ArrayList<>();
        for(var scope:scopes){
            var auth=latestAuth.get(text(scope,"tid"));
            if(auth==null)continue;
            String channel=text(auth,"delivery_channel"),grant=text(auth,"status");
            String grantStatus=switch(grant){case "ACTIVE"->"ACTIVE";case "SUSPENDED"->"SUSPENDED";case "EXPIRED"->"EXPIRED";case "REVOKED"->"REVOKED";case "SUPERSEDED"->"SUPERSEDED";default->"PENDING_ACTIVATION";};
            String gateway="API".equals(channel)?"PENDING":"NOT_APPLICABLE";
            if("API".equals(channel)&&auth.get("api_authorization_id")!=null){String sync=gatewayStatuses.get(text(auth,"api_authorization_id"));if(sync!=null)gateway=switch(sync){case "SUCCESS"->"SYNCED";case "FAILED"->"FAILED";default->"PENDING";};}
            var authorization=map("id",auth.get("tid"),"applyScopeId",auth.get("apply_scope_id"),"authorizationVersion",auth.get("authorization_version"),"publicationId",auth.get("publication_id"),"resourceSnapshotId",auth.get("resource_snapshot_id"),"deliveryChannel",channel,"apiAuthorizationId",auth.get("api_authorization_id"),"distributionTaskId",auth.get("distribution_task_id"),"validFrom",iso(auth.get("valid_from")),"validUntil",iso(auth.get("valid_until")),"scopeSnapshot",db.object(auth.get("scope_snapshot")),"status",grant,"revision",revisionValue(auth));
            items.add(map("id",auth.get("tid"),"catalogId",scope.get("catalog_id"),"versionId",scope.get("publication_id"),"channel",channel,"grantStatus",grantStatus,"deliveryStatus","ACTIVE".equals(grant)?"READY":"PROVISIONING","gatewayStatus",gateway,"revision",revisionValue(auth),"validFrom",iso(auth.get("valid_from")),"validUntil",iso(auth.get("valid_until")),"allowedActions","ACTIVE".equals(grant)?List.of("API".equals(channel)?"INVOKE_INFO":"FILE".equals(channel)?"DOWNLOAD":"QUERY_SESSION"):List.of(),"authorization",authorization));
        }
        return map("subscriptionId",id,"items",items,"events",events("SUBSCRIPTION",id));
    }
    public Map<String,Object> demand(String id){
        var row=db.owned("data_apply_form_t",id,false);var c=demandCase(id,false);var draft=db.object(row.get("asset_payload_json"));String status=text(c,"status");
        boolean requester=db.user().equals(text(row,"apply_user_id"));
        boolean handler=!"DRAFT".equals(status)&&!text(c,"owner_org_id").isBlank()&&db.organizations().contains(text(c,"owner_org_id"));
        if(!requester&&!handler)throw error(404,"NOT_FOUND","需求不存在或无权访问");
        var matches=db.rows("SELECT event_meta FROM da_demand_match_t WHERE tenant_id=? AND demand_case_id=? AND event_type='PROPOSED' ORDER BY event_seq",db.tenant(),c.get("tid")).stream().map(m->db.object(m.get("event_meta"))).toList();
        List<Map<String,Object>> history=new ArrayList<>(events("DEMAND",id));
        for(var e:db.rows("SELECT tid,event_type,event_message,occurred_at FROM da_demand_match_t WHERE tenant_id=? AND demand_case_id=? ORDER BY event_seq",db.tenant(),c.get("tid")))history.add(map("id",e.get("tid"),"at",iso(e.get("occurred_at")),"action",e.get("event_type"),"opinion",e.get("event_message")));
        List<String> actions=status.equals("DRAFT")?List.of("VIEW","EDIT","SUBMIT"):Set.of("SUBMITTED","TRIAGED","MATCHING").contains(status)?requester?List.of("VIEW","COMMENT","CONFIRM_MATCH","WITHDRAW"):List.of("VIEW","ACCEPT","COMMENT","RECOMMEND","RETURN_FOR_INFO","MARK_UNFULFILLED"):List.of("VIEW");
        return map("id",id,"revision",revisionValue(row),"title",text(row,"apply_name"),"status",status,"requesterId",text(row,"apply_user_id"),"departmentId",text(row,"apply_org_id"),"handlerDepartmentId",text(c,"owner_org_id"),"fields",draft.getOrDefault("fields",List.of()),"matches",matches,"events",history,"allowedActions",actions,"draft",draft,"closeReason",c.get("close_reason"));
    }

    public Map<String,Object> workbench(Map<String,String> query){
        String tab=query.getOrDefault("tab","SUBSCRIPTIONS");int pn=Math.max(1,number(query.get("pageNo"),1)),ps=Math.min(100,Math.max(1,number(query.get("pageSize"),15)));List<Map<String,Object>> items=new ArrayList<>();long total;
        String sortBy=query.getOrDefault("sortBy","updatedAt"),sortOrder=query.getOrDefault("sortOrder","desc");
        if(!Set.of("name","updatedAt").contains(sortBy)||!Set.of("asc","desc").contains(sortOrder))throw error(400,"INVALID_ARGUMENT","排序规则不正确");
        if(tab.equals("TODO")||tab.equals("TODOS")){List<Map<String,Object>> tasks=tasks(null);for(var task:tasks)items.add(map("id",task.get("id"),"kind","TASK","title",task.get("title"),"status",task.get("status"),"updatedAt",task.get("createdAt"),"allowedActions",task.get("allowedActions"),"task",task));String keyword=query.getOrDefault("keyword","").trim(),status=query.getOrDefault("status","").trim();var comparator=Comparator.comparing((Map<String,Object> item)->text(item,sortBy.equals("name")?"title":"updatedAt"),String.CASE_INSENSITIVE_ORDER);if(sortOrder.equals("desc"))comparator=comparator.reversed();items=items.stream().filter(item->(keyword.isBlank()||text(item,"title").contains(keyword)||text(item,"id").contains(keyword))&&(status.isBlank()||status.equals(text(item,"status")))).sorted(comparator).toList();total=items.size();items=items.stream().skip((long)(pn-1)*ps).limit(ps).toList();}
        else if(Set.of("SUBSCRIPTIONS","DEMANDS").contains(tab)){
            boolean demand=tab.equals("DEMANDS");String where=" data_apply_form_t.tenant_id=? AND COALESCE(data_apply_form_t.is_del,0)=0 AND "+(demand?"type='need'":"type IN ('apply','catalogSubscribe','CATALOG_SUBSCRIPTION')");List<Object> args=new ArrayList<>(List.of(db.tenant()));
            if(demand&&!db.organizations().isEmpty()){
                var orgs=db.organizations();String marks=String.join(",",Collections.nCopies(orgs.size(),"?"));
                where+=" AND (apply_user_id=? OR EXISTS (SELECT 1 FROM da_demand_case_t c WHERE c.tenant_id COLLATE utf8mb4_general_ci=data_apply_form_t.tenant_id AND c.apply_form_id COLLATE utf8mb4_general_ci=data_apply_form_t.tid AND c.owner_org_id IN ("+marks+") AND c.status<>'DRAFT'))";
                args.add(db.user());args.addAll(orgs);
            }else{where+=" AND apply_user_id=?";args.add(db.user());}
            String keyword=query.getOrDefault("keyword","");if(!keyword.isBlank()){where+=" AND apply_name LIKE ?";args.add("%"+keyword+"%");}
            if(!query.getOrDefault("status","").isBlank()&&!demand){Integer raw=Map.of("DRAFT",0,"IN_REVIEW",1,"APPROVED",2,"REJECTED",3,"WITHDRAWN",4,"RETURNED",5).get(query.get("status"));if(raw==null)throw error(400,"INVALID_ARGUMENT","状态筛选不正确");where+=" AND flow_status=?";args.add(raw);}
            if(!query.getOrDefault("status","").isBlank()&&demand){where+=" AND EXISTS (SELECT 1 FROM da_demand_case_t c WHERE c.tenant_id COLLATE utf8mb4_general_ci=data_apply_form_t.tenant_id AND c.apply_form_id COLLATE utf8mb4_general_ci=data_apply_form_t.tid AND c.status=?)";args.add(query.get("status"));}
            total=db.jdbc.queryForObject("SELECT COUNT(*) FROM data_apply_form_t WHERE"+where,Long.class,args.toArray());args.add(ps);args.add((long)(pn-1)*ps);
            String orderColumn=sortBy.equals("name")?"apply_name":"updated_time";
            // Build list summaries from the current page and batch-loaded related rows.
            var pageRows=db.rows("SELECT * FROM data_apply_form_t WHERE"+where+" ORDER BY "+orderColumn+" "+sortOrder+",tid LIMIT ? OFFSET ?",args.toArray());
            Set<String> formIds=new LinkedHashSet<>();for(var row:pageRows)formIds.add(text(row,"tid"));
            Map<String,Map<String,Object>> demandCases=new HashMap<>();Map<String,List<Map<String,Object>>> scopesByForm=new HashMap<>();
            if(!formIds.isEmpty()){
                String marks=String.join(",",Collections.nCopies(formIds.size(),"?"));List<Object> formArgs=new ArrayList<>();formArgs.add(db.tenant());formArgs.addAll(formIds);
                if(demand){db.require("da_demand_case_t");for(var c:db.rows("SELECT * FROM da_demand_case_t WHERE tenant_id=? AND apply_form_id IN ("+marks+")",formArgs.toArray()))demandCases.put(text(c,"apply_form_id"),c);}
                else if(db.table("da_apply_scope_t"))for(var scope:db.rows("SELECT * FROM da_apply_scope_t WHERE tenant_id=? AND apply_form_id IN ("+marks+") ORDER BY scope_no",formArgs.toArray()))scopesByForm.computeIfAbsent(text(scope,"apply_form_id"),ignored->new ArrayList<>()).add(scope);
            }
            String currentUser=db.user();Set<String> currentOrgs=demand?new HashSet<>(db.organizations()):Set.of();
            for(var row:pageRows){
                String id=text(row,"tid");String itemStatus;List<String> allowedActions;Map<String,Object> summary=null;
                if(demand){
                    var c=demandCases.get(id);if(c==null)throw error(404,"NOT_FOUND","该历史需求尚未迁移到供需工作台");
                    itemStatus=text(c,"status");boolean requester=currentUser.equals(text(row,"apply_user_id"));
                    boolean handler=!"DRAFT".equals(itemStatus)&&!text(c,"owner_org_id").isBlank()&&currentOrgs.contains(text(c,"owner_org_id"));
                    if(!requester&&!handler)throw error(404,"NOT_FOUND","需求不存在或无权访问");
                    allowedActions=itemStatus.equals("DRAFT")?List.of("VIEW","EDIT","SUBMIT"):Set.of("SUBMITTED","TRIAGED","MATCHING").contains(itemStatus)?requester?List.of("VIEW","COMMENT","CONFIRM_MATCH","WITHDRAW"):List.of("VIEW","ACCEPT","COMMENT","RECOMMEND","RETURN_FOR_INFO","MARK_UNFULFILLED"):List.of("VIEW");
                }else{
                    itemStatus=status(row);allowedActions=itemStatus.equals("IN_REVIEW")?List.of("VIEW","WITHDRAW"):Set.of("DRAFT","REJECTED","RETURNED","WITHDRAWN").contains(itemStatus)?List.of("VIEW","EDIT","SUBMIT"):List.of("VIEW","DELIVERY");
                    var scopes=scopesByForm.getOrDefault(id,List.of()).stream().filter(scope->number(scope.get("submission_version"),0)==number(row.get("submission_version"),0)).map(this::scopeDto).toList();
                    var draft=db.object(row.get("asset_payload_json"));summary=map("id",id,"revision",revisionValue(row),"requestType","CATALOG_SUBSCRIPTION","title",text(row,"apply_name"),"status",itemStatus,"applicantUserId",text(row,"apply_user_id"),"departmentId",text(row,"apply_org_id"),"resources",draft.getOrDefault("resources",List.of()),"updatedAt",iso(row.get("updated_time")),"allowedActions",allowedActions,"submissionVersion",number(row.get("submission_version"),0),"scopes",scopes,"draft",draft);
                }
                var item=map("id",id,"kind",demand?"DEMAND":"SUBSCRIPTION","title",row.get("apply_name"),"status",itemStatus,"updatedAt",iso(row.get("updated_time")),"allowedActions",allowedActions);
                if(!demand)item.put("subscription",summary);items.add(item);
            }
        }else if(Set.of("FAVORITES","HISTORY").contains(tab)){
            String table=tab.equals("FAVORITES")?"data_market_collect_t":"data_market_catalog_browse_t",sort=tab.equals("FAVORITES")?"updated_time":"browse_time";if(!db.column(table,"asset_type"))throw error(503,"ASSET_SCHEMA_NOT_READY","个人工作台尚未升级");String nameOrder="CASE "+table+".asset_type WHEN 'APPLICATION' THEN (SELECT app_name FROM sym_application_t a WHERE a.tenant_id="+table+".tenant_id AND a.tid="+table+".asset_id LIMIT 1) WHEN 'DATASOURCE' THEN (SELECT db_name FROM db_datasource_t s WHERE s.tenant_id="+table+".tenant_id AND s.tid="+table+".asset_id LIMIT 1) WHEN 'TABLE' THEN (SELECT COALESCE(NULLIF(table_name_cn,''),table_name) FROM db_table_t t WHERE t.tenant_id="+table+".tenant_id AND t.tid="+table+".asset_id LIMIT 1) WHEN 'CATALOG' THEN (SELECT catalog_name FROM da_catalog_t c WHERE c.tenant_id="+table+".tenant_id AND c.tid="+table+".asset_id LIMIT 1) WHEN 'API' THEN (SELECT service_name FROM api_info_t a WHERE a.tenant_id="+table+".tenant_id AND a.tid="+table+".asset_id LIMIT 1) END";total=db.jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND apply_user_id=?",Long.class,db.tenant(),db.user());var pageRows=db.rows("SELECT * FROM "+table+" WHERE tenant_id=? AND apply_user_id=? ORDER BY "+(sortBy.equals("name")?nameOrder:sort)+" "+sortOrder+",tid LIMIT ? OFFSET ?",db.tenant(),db.user(),ps,(pn-1)*ps);var refs=queries.references(pageRows);for(var row:pageRows){String refKey=text(row,"asset_type")+"\u0000"+text(row,"asset_id");Map<String,Object> ref=refs.getOrDefault(refKey,map("kind",text(row,"asset_type"),"id",text(row,"asset_id"),"name","资源已不可用","available",false));items.add(map("id",row.get("tid"),"kind",tab.equals("FAVORITES")?"FAVORITE":"VISIT","title",ref.get("name"),"object",ref,"updatedAt",iso(row.get(sort)),"allowedActions",List.of("VIEW","REMOVE"),"visitCount",row.getOrDefault("visit_count",1)));}
        }else throw error(400,"INVALID_ARGUMENT","工作台页签不正确");
        return map("items",items,"page",queries.page(pn,ps,total,sortBy,sortOrder));
    }

    public List<Map<String,Object>> tasks(String businessId){
        db.require("flow_task","flow_instance","flow_user");String app=String.valueOf(TenantSessions.session().get("appId"));List<String> permissions=principals.permissionKeys(db.user(),app);if(permissions.isEmpty())return List.of();String marks=String.join(",",Collections.nCopies(permissions.size(),"?"));
        String sql="SELECT DISTINCT t.id,t.node_name,t.create_time,i.business_id,i.ext,p.catalog_id AS publication_catalog_id,p.publication_id AS publication_version_id FROM flow_task t JOIN flow_instance i ON i.id=t.instance_id AND i.tenant_id=t.tenant_id JOIN flow_user u ON u.associated=t.id AND u.tenant_id=t.tenant_id LEFT JOIN res_catalog_publish_request p ON p.tenant_id COLLATE utf8mb4_general_ci=t.tenant_id AND p.tid COLLATE utf8mb4_general_ci=i.business_id AND p.publication_id IS NOT NULL WHERE t.tenant_id=? AND t.del_flag='0' AND t.flow_status='1' AND u.processed_by IN ("+marks+") AND (EXISTS(SELECT 1 FROM data_apply_form_t a WHERE a.tenant_id=t.tenant_id AND a.tid=i.business_id AND a.type='CATALOG_SUBSCRIPTION') OR p.tid IS NOT NULL)";List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(permissions);if(businessId!=null){sql+=" AND i.business_id=?";args.add(businessId);}sql+=" ORDER BY t.create_time DESC,t.id LIMIT 200";
        return db.rows(sql,args.toArray()).stream().map(r->{
            boolean publication=r.get("publication_version_id")!=null;
            var task=map("id",text(r,"id"),"businessId",text(r,"business_id"),"taskType",publication?"CATALOG_PUBLICATION":"SUBSCRIPTION_APPROVAL","title",text(r,"node_name"),"revision",text(r,"id"),"status","PENDING","createdAt",iso(r.get("create_time")),"allowedActions",List.of("APPROVE","REJECT","RETURN"));
            if(publication){task.put("catalogId",r.get("publication_catalog_id"));task.put("versionId",r.get("publication_version_id"));}
            return task;
        }).toList();
    }
    @Transactional
    public Object handleTask(String taskId,Map<String,Object> input,String key){return db.idempotent("TASK_HANDLE",key,input,()->{var task=tasks(null).stream().filter(t->taskId.equals(t.get("id"))).findFirst().orElseThrow(()->error(404,"NOT_FOUND","任务不存在或不属于当前处理人"));if(!taskId.equals(required(input,"expectedRevision",64)))throw error(409,"REVISION_CONFLICT","任务已变化");String action=required(input,"action",32);if(!Set.of("APPROVE","REJECT","RETURN").contains(action))throw error(400,"INVALID_ARGUMENT","不支持该任务操作");String biz=text(task,"businessId");if(task.get("taskType").equals("SUBSCRIPTION_APPROVAL")){var row=db.owned("data_apply_form_t",biz,true);if(number(input.get("expectedSubmissionVersion"),0)!=number(row.get("submission_version"),0))throw error(409,"REVISION_CONFLICT","申请轮次已变化");}
        var command=new ApprovalHandleRequest();command.setTaskId(taskId);command.setBusinessId(biz);command.setHandleType(action.equals("APPROVE")?"PASS":action.equals("REJECT")?"UNPASS":"REJECT");command.setMessage(required(input,"opinion",4000));command.setApproveType(task.get("taskType").equals("CATALOG_PUBLICATION")?"haoyuePublication":"haoyueSubscription");approvals.handle(command);return map("id",taskId,"revision",taskId,"changed",true,"status","COMPLETED");});}
    public String flowCode(String type){db.require("wf_process_binding_t");var binding=db.one("SELECT flow_code FROM wf_process_binding_t WHERE tenant_id=? AND business_type=? AND status=1 AND is_del=0",db.tenant(),type);if(binding==null)throw error(503,"FLOW_NOT_CONFIGURED","尚未配置"+(type.equals("CATALOG_PUBLICATION")?"目录发布":"目录订阅")+"审批流程，请在审批中心配置业务流程绑定");return text(binding,"flow_code");}
    private Map<String,Object> form(String id,boolean lock){var row=db.owned("data_apply_form_t",id,lock);if(!db.user().equals(text(row,"apply_user_id")))throw error(404,"NOT_FOUND","申请不存在或无权访问");return row;}
    private Map<String,Object> demandCase(String id,boolean lock){db.require("da_demand_case_t");var c=db.one("SELECT * FROM da_demand_case_t WHERE tenant_id=? AND apply_form_id=?"+(lock?" FOR UPDATE":""),db.tenant(),id);if(c==null)throw error(404,"NOT_FOUND","该历史需求尚未迁移到供需工作台");return c;}
    private String status(Map<String,Object> row){return switch(number(row.get("flow_status"),0)){case 1->"IN_REVIEW";case 2->"APPROVED";case 3->"REJECTED";case 4->"WITHDRAWN";case 5->"RETURNED";default->"DRAFT";};}
    private List<Map<String,Object>> rowsByIds(String sqlTemplate,Set<String> ids){
        if(ids.isEmpty())return List.of();
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(ids);
        return db.rows(sqlTemplate.formatted(marks),args.toArray());
    }
    private String revisionValue(Map<String,Object> row){return row.get("revision")==null?"0":String.valueOf(row.get("revision"));}
    private Map<String,Object> scopeDto(Map<String,Object> r){return map("id",text(r,"tid"),"scopeNo",r.get("scope_no"),"submissionVersion",r.get("submission_version"),"catalogId",r.get("catalog_id"),"publicationId",r.get("publication_id"),"resourceSnapshotId",r.get("resource_snapshot_id"),"deliveryChannel",r.get("delivery_channel"),"requestedScope",db.object(r.get("requested_scope")),"status",r.get("status"),"revision",revisionValue(r),"submittedAt",iso(r.get("submitted_at")));}
    void event(String kind,String id,String action,String key,Map<String,Object> payload){db.insert("da_asset_event_t",map("tid",AssetCenterStore.id(),"tenant_id",db.tenant(),"object_type",kind,"object_id",id,"event_type",action,"event_key",hash(db.tenant()+db.user()+kind+action+key),"correlation_id",id,"actor_user_id",db.user(),"payload_redacted",db.encode(payload),"occurred_at",now(),"created_time",now()));}
    private List<Map<String,Object>> events(String kind,String id){if(!db.table("da_asset_event_t"))return List.of();return db.rows("SELECT tid,event_type,occurred_at,payload_redacted FROM da_asset_event_t WHERE tenant_id=? AND object_type=? AND object_id=? ORDER BY occurred_at,tid",db.tenant(),kind,id).stream().map(r->map("id",text(r,"tid"),"at",iso(r.get("occurred_at")),"action",text(r,"event_type"),"opinion",db.object(r.get("payload_redacted")).get("opinion"))).toList();}
    private Timestamp date(Map<String,Object> payload,String key){try{return Timestamp.from(Instant.parse(required(payload,key,64)));}catch(AssetCenterException e){throw e;}catch(Exception e){throw error(400,"INVALID_ARGUMENT",key+"时间格式不正确");}}
}

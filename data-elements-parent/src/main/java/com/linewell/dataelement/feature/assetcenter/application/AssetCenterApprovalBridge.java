package com.linewell.dataelement.feature.assetcenter.application;

import static com.linewell.dataelement.feature.assetcenter.application.AssetCenterStore.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies final approval decisions to the asset center without widening the approved scope. */
@Service
public class AssetCenterApprovalBridge {
    private final AssetCenterStore db;
    public AssetCenterApprovalBridge(AssetCenterStore db) { this.db=db; }

    @Transactional
    public void complete(String type,String businessId,int decision) {
        if("haoyuePublication".equals(type)) publication(businessId,decision);
        else if("haoyueSubscription".equals(type)) subscription(businessId,decision);
    }

    private void publication(String requestId,int decision) {
        db.require("da_catalog_publication_t","da_catalog_publication_head_t");
        var request=db.one("SELECT * FROM res_catalog_publish_request WHERE tenant_id=? AND tid=? AND publication_id IS NOT NULL FOR UPDATE",db.tenant(),requestId);
        if(request==null)throw error(404,"NOT_FOUND","发布申请不存在");
        if(decision==1)return;
        String status=decision==2?"PUBLISHED":decision==4?"WITHDRAWN":decision==5?"RETURNED":"REJECTED";
        if(status.equals(text(request,"request_status")))return;
        if(!"IN_REVIEW".equals(text(request,"request_status")))throw error(409,"INVALID_STATE","发布申请已经处理");
        String publicationId=text(request,"publication_id"),catalogId=text(request,"catalog_id");
        if(decision==2) {
            var version=db.one("SELECT * FROM da_catalog_publication_t WHERE tenant_id=? AND tid=? FOR UPDATE",db.tenant(),publicationId);
            if(version==null||!"VALIDATED".equals(text(version,"status"))||!requestId.equals(text(version,"request_id")))
                throw error(409,"INVALID_STATE","发布版本不再满足审批条件");
            db.jdbc.update("UPDATE da_catalog_publication_t SET status='PUBLISHED',published_at=?,published_by=?,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",
                    now(),db.user(),now(),db.user(),db.tenant(),publicationId);
            db.jdbc.update("UPDATE da_catalog_publication_head_t SET current_publication_id=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND catalog_id=?",
                    publicationId,now(),db.user(),db.tenant(),catalogId);
        }
        db.jdbc.update("UPDATE res_catalog_publish_request SET request_status=?,reviewed_time=?,published_time=?,reviewer_id=?,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",
                status,now(),decision==2?now():null,db.user(),now(),db.user(),db.tenant(),requestId);
    }

    private void subscription(String formId,int decision) {
        db.require("da_apply_scope_t","da_authorization_scope_t");
        var form=db.one("SELECT * FROM data_apply_form_t WHERE tenant_id=? AND tid=? AND type='CATALOG_SUBSCRIPTION' FOR UPDATE",db.tenant(),formId);
        if(form==null)throw error(404,"NOT_FOUND","订阅申请不存在");
        if(decision==1)return;
        int old=number(form.get("flow_status"),0);
        if(old==decision)return;
        if(old!=1)throw error(409,"INVALID_STATE","订阅申请已经处理");
        int submission=number(form.get("submission_version"),0);
        String scopeStatus=decision==2?"APPROVED":decision==4?"WITHDRAWN":"REJECTED";
        var scopes=db.rows("SELECT * FROM da_apply_scope_t WHERE tenant_id=? AND apply_form_id=? AND submission_version=? AND status='SUBMITTED' FOR UPDATE",db.tenant(),formId,submission);
        Set<String> existingGrants=new HashSet<>();Map<String,Map<String,Object>> snapshots=new HashMap<>();
        if(decision==2&&!scopes.isEmpty()){
            Set<String> scopeIds=new HashSet<>(),snapshotIds=new HashSet<>();
            for(var scope:scopes){scopeIds.add(text(scope,"tid"));snapshotIds.add(text(scope,"resource_snapshot_id"));}
            for(var grant:rowsByIds("SELECT apply_scope_id FROM da_authorization_scope_t WHERE tenant_id=? AND apply_scope_id IN (%s)",scopeIds))existingGrants.add(text(grant,"apply_scope_id"));
            for(var snapshot:rowsByIds("SELECT * FROM da_catalog_resource_snapshot_t WHERE tenant_id=? AND tid IN (%s)",snapshotIds))snapshots.put(text(snapshot,"tid"),snapshot);
        }
        for(var scope:scopes) {
            db.jdbc.update("UPDATE da_apply_scope_t SET status=?,approved_scope=?,decided_at=?,revision=revision+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",
                    scopeStatus,decision==2?scope.get("requested_scope"):null,decision==2?now():null,now(),db.user(),db.tenant(),scope.get("tid"));
            if(decision==2) pendingDelivery(form,scope,existingGrants,snapshots);
        }
        db.jdbc.update("UPDATE data_apply_form_t SET flow_status=?,revision=COALESCE(revision,0)+1,updated_time=?,updated_by=? WHERE tenant_id=? AND tid=?",
                decision,now(),db.user(),db.tenant(),formId);
    }

    private List<Map<String,Object>> rowsByIds(String sqlTemplate,Set<String> ids){
        if(ids.isEmpty())return List.of();
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));List<Object> args=new ArrayList<>();args.add(db.tenant());args.addAll(ids);
        return db.rows(sqlTemplate.formatted(marks),args.toArray());
    }

    private void pendingDelivery(Map<String,Object> form,Map<String,Object> scope,
            Set<String> existingGrants,Map<String,Map<String,Object>> snapshots) {
        if(existingGrants.contains(text(scope,"tid")))return;
        Timestamp from=scope.get("requested_from") instanceof Timestamp t&&t.after(now())?t:now();
        Timestamp until=(Timestamp)scope.get("requested_until");
        if(until==null||!until.after(from))return;
        var snapshot=snapshots.get(text(scope,"resource_snapshot_id"));
        if(snapshot==null)throw error(409,"RESOURCE_CHANGED","交付资源快照不存在");
        var contract=db.object(snapshot.get("contract_snapshot"));
        var resource=db.object(contract.get("resource"));
        String channel=text(scope,"delivery_channel"),formId=text(form,"tid"),catalogId=text(scope,"catalog_id"),externalId=id();
        if("API".equals(channel)) {
            if(!"API".equals(text(resource,"kind")))throw error(409,"RESOURCE_CHANGED","接口交付资源类型不匹配");
            db.insert("data_api_authorization_t",map("tid",externalId,"tenant_id",db.tenant(),"apply_form_id",formId,"catalog_id",catalogId,
                    "api_id",text(resource,"id"),"apply_user_id",text(form,"apply_user_id"),"authorization_status","PENDING",
                    "gateway_sync_status","PENDING","authorization_scope","SCOPED_PENDING","gateway_message","待完成范围校验与网关同步",
                    "authorized_time",now(),"expire_time",until,"created_by",db.user(),"created_time",now(),"updated_by",db.user(),"updated_time",now(),"is_del",0));
        } else {
            db.insert("data_distribution_task_t",map("tid",externalId,"tenant_id",db.tenant(),"apply_form_id",formId,"catalog_id",catalogId,
                    "catalog_name",text(form,"apply_name"),"source_table_id",text(resource,"id"),"source_table_name",text(resource,"name"),
                    "task_code","HY-"+externalId,"task_name",text(form,"apply_name")+"受控交付","task_status","GENERATED","progress",0,
                    "execution_engine","PENDING_INTEGRATION","execution_mode","PENDING_INTEGRATION","status_message","审批通过，等待受控交付引擎接管",
                    "created_by",db.user(),"created_time",now(),"updated_by",db.user(),"updated_time",now(),"is_del",0));
        }
        var grant=db.audit(id());grant.putAll(map("apply_scope_id",scope.get("tid"),"authorization_version",1,"publication_id",scope.get("publication_id"),
                "resource_snapshot_id",scope.get("resource_snapshot_id"),"delivery_channel",channel,
                "api_authorization_id","API".equals(channel)?externalId:null,"distribution_task_id","API".equals(channel)?null:externalId,
                "grantee_user_id",form.get("apply_user_id"),"grantee_org_id",form.get("apply_org_id"),"scope_snapshot",scope.get("requested_scope"),
                "valid_from",from,"valid_until",until,"status","PENDING","idempotency_key",hash(db.tenant()+":"+scope.get("tid")+":approval")));
        db.insert("da_authorization_scope_t",grant);
    }
}

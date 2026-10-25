package com.linewell.dataelement.feature.logmanagement;

import com.linewell.dataelement.platform.audit.*;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** One count + one page; names and departments are stored snapshots, never row-by-row lookups. */
@Service
public class LogQueryService {
    private final AuditStore store;
    private final AuditSanitizer sanitizer;
    public LogQueryService(AuditStore store,AuditSanitizer sanitizer) { this.store=store; this.sanitizer=sanitizer; }
    public record View(String table,String actor,String account,String name,String org,String orgName,String action,String path,String detail) { }
    public View view(LogScope scope,String kind) {
        if(scope.platform()) return new View("iam_audit_event_t","actor_id","actor_account","actor_name","actor_org_id","actor_org_name","action","request_path","snapshot_json");
        return switch(kind) {
            case "auth" -> new View("log_user_login_t","created_by","account","full_name","org_id","org_name","action_code","route_path","detail_json");
            case "requests" -> new View("log_request_t","user_id","account","full_name","org_id","org_name","action_code","route_path","detail_json");
            case "operations","security" -> new View("log_system_t","user_id","user_account","user_name","org_id","org_name","action_code","route_path","detail_json");
            default -> throw new IllegalArgumentException("日志类型无效");
        };
    }
    public record Selection(View view,String where,Map<String,Object> params) { }
    public Selection selection(LogScope scope,String kind,Map<String,Object> input) {return selection(scope,kind,input,true);}
    private Selection selection(LogScope scope,String kind,Map<String,Object> input,boolean withTime) {
        View v=view(scope,kind); Map<String,Object> params=new LinkedHashMap<>();
        String where=" where 1=1";
        if(scope.platform()) {
            params.put("domain",scope.domain()); where+=" and identity_domain=:domain";
            if(scope.appId()!=null) { params.put("app",scope.appId()); where+=" and app_id=:app"; }
            String category=switch(kind) {case "auth"->"AUTH";case "requests"->"API";default->"OPERATION";};
            params.put("type",category); where+=" and event_type=:type";
        } else {
            params.put("tenant",scope.tenantId());where+=" and tenant_id=:tenant";
            if(scope.mode().contains("OWNER")) { params.put("owner",scope.actorId());where+=" and "+v.actor()+"=:owner"; }
            if(scope.mode().contains("ORG")) {
                if(scope.orgIds().isEmpty()) where+=" and 1=0";
                else { params.put("orgs",scope.orgIds());where+=" and "+v.org()+" in (:orgs)"; }
            }
        }
        if(kind.equals("security")) where+=" and category='SECURITY'";
        else if(kind.equals("operations")) where+=" and (category is null or category in ('OPERATION','TASK'))";
        for(String filter:List.of("result","actorId","orgId","traceId","requestId")) {
            String value=text(input.get(filter),128);
            if(!value.isBlank()) {
                if(filter.equals("result")&&!AuditRecorder.RESULTS.contains(value)) throw new IllegalArgumentException("日志结果无效");
                String column=switch(filter) {case "actorId"->v.actor();case "orgId"->v.org();case "traceId"->"trace_id";case "requestId"->"request_id";default->"result";};
                params.put(filter,value);where+=" and "+column+"=:"+filter;
            }
        }
        String keyword=text(input.get("keyword"),100);
        if(!keyword.isBlank()) {
            params.put("keyword","%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            where+=" and ("+String.join(" or ",List.of(v.account(),v.name(),v.orgName(),v.action(),"target_name","trace_id","menu_path","operation_name").stream().map(c->c+" like :keyword escape '!' ").toList())+")";
        }
        Instant end=parseTime(input.get("endTime"),Instant.now().plusSeconds(1));
        Instant start=parseTime(input.get("startTime"),end.minus(Duration.ofDays(7)));
        if(!start.isBefore(end)||Duration.between(start,end).compareTo(Duration.ofDays(90))>0) throw new IllegalArgumentException("请选择不超过90天的有效时间区间");
        params.put("start",LogTimes.utc(start)); params.put("end",LogTimes.utc(end));
        params.put("legacyStart",java.time.LocalDateTime.ofInstant(start,java.time.ZoneId.of("Asia/Shanghai")));params.put("legacyEnd",java.time.LocalDateTime.ofInstant(end,java.time.ZoneId.of("Asia/Shanghai")));
        // Legacy timestamps remain explicitly marked; this compatibility path assumes the documented local deployment zone.
        if(withTime)where+=" and ((event_time>=:start and event_time<:end) or (event_time is null and created_time>=:legacyStart and created_time<:legacyEnd))";
        if(input.get("receivedBefore")!=null){params.put("receivedBefore",LogTimes.utc(parseTime(input.get("receivedBefore"),Instant.now())));where+=" and (received_time is null or received_time<=:receivedBefore)";}
        return new Selection(v,where,params);
    }
    public Map<String,Object> page(LogScope scope,String kind,Map<String,Object> input) {
        Selection s=selection(scope,kind,input); long page=number(input.get("page"),1,1,10000);long size=number(input.get("size"),20,1,100);
        var params=new LinkedHashMap<>(s.params());params.put("size",size);params.put("offset",(page-1)*size);
        Long total=store.named(scope.tenantId()).queryForObject("select count(*) from "+s.view().table()+s.where(),params,Long.class);
        var rows=read(scope,s,params,order(input),false);
        return Map.of("list",rows,"total",total==null?0:total,"page",page,"size",size,"timeZone","Asia/Shanghai","appliedScopeSummary",scope.mode());
    }
    public List<Map<String,Object>> read(LogScope scope,Selection s,Map<String,Object> params,String order,boolean detail) {
        // The displayed legacy creation time is Asia/Shanghai; sort it on the same
        // UTC timeline as new events, including exports and archive snapshots.
        if(order.startsWith("event_time "))order="coalesce(event_time,created_time - interval '8' hour)"+order.substring("event_time".length());
        else {String column=order.split(" ",2)[0];if(column.equals("actor_name"))column=s.view().name();else if(column.equals("action_code"))column=s.view().action();order="case when "+column+" is null then 1 else 0 end,"+order;}
        String sql="select "+projection(s.view(),detail)+" from "+s.view().table()+s.where()+" order by "+order+" limit :size offset :offset";
        var rows=store.named(scope.tenantId()).queryForList(sql,params);
        return rows.stream().map(row->normalize(row,detail)).toList();
    }
    private String projection(View v,boolean detail) {
        boolean platform=v.table().equals("iam_audit_event_t");
        return "tid,event_time,created_time,category,actor_kind,menu_id,menu_path,page_route,operation_name,function_code,"+v.actor()+" actor_id,"+v.account()+" actor_account,"+v.name()+" actor_name,"+v.org()+" actor_org_id,"+v.orgName()+" actor_org_name,"+v.action()+" action_code,"+(platform?"object_type target_type,object_id target_id,":"target_type,target_id,")+"target_name,result,reason_code,ip,duration_ms,trace_id,request_id,session_ref,http_status,business_code,total_count,success_count,failure_count,source_app,batch_id,task_id,"+v.path()+" route_path"+(detail?","+v.detail()+" details":"");
    }
    private Map<String,Object> normalize(Map<String,Object> input,boolean detail) {
        Map<String,Object> row=AuditStore.lower(JdbcValueNormalizer.map(input));
        row.put("legacy",row.get("event_time")==null);
        Object time=row.get("event_time");if(time!=null)row.put("event_time",LogTimes.instant(time).toString());
        if(row.get("created_time")!=null) {
            Object created=row.get("created_time");
            if(time==null && created instanceof java.sql.Timestamp stamp)row.put("created_time",stamp.toLocalDateTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toString());
            else if(time==null && created instanceof java.time.LocalDateTime date)row.put("created_time",date.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toString());
            else row.put("created_time",LogTimes.instant(created).toString());
        }
        if(detail && row.get("details")!=null) {
            try { row.put("details",sanitizer.clean(new com.fasterxml.jackson.databind.ObjectMapper().readValue(String.valueOf(row.get("details")),Object.class))); }
            catch(Exception ignored) { row.put("details",Map.of("phase","LEGACY_DETAIL_NOT_EXPOSED")); }
        }
        return row;
    }
    public Map<String,Object> statistics(LogScope scope,String kind,Map<String,Object> input) {
        var s=selection(scope,kind,input);
        return AuditStore.lower(store.named(scope.tenantId()).queryForMap("select count(*) total,sum(case when result='SUCCESS' then 1 else 0 end) success_count,sum(case when result='FAILED' then 1 else 0 end) failure_count,sum(case when result='DENIED' then 1 else 0 end) denied_count,sum(case when result='PARTIAL' then 1 else 0 end) partial_count from "+s.view().table()+s.where(),s.params()));
    }
    public Map<String,Object> detail(LogScope scope,String kind,String id) {
        var rows=authorizedEvents(scope,kind,List.of(id),true);
        return rows.getFirst();
    }
    public Object summary(LogScope scope,Map<String,Object> input){
        if(!scope.platform())throw new SecurityException("请使用各日志模块中对应授权范围的统计");
        List<String> fragments=new ArrayList<>();var params=new LinkedHashMap<String,Object>();
        for(String kind:List.of("auth","operations","requests","security")){
            var s=selection(scope,kind,input);String where=s.where().replace(":type",":type_"+kind);params.putAll(s.params());params.put("type_"+kind,s.params().get("type"));
            fragments.add("select '"+kind+"' category,count(*) total,sum(case when result='SUCCESS' then 1 else 0 end) success_count,sum(case when result='FAILED' then 1 else 0 end) failure_count,sum(case when result='DENIED' then 1 else 0 end) denied_count,max(event_time) last_event_time from "+s.view().table()+where);
        }
        String field=text(input.getOrDefault("sortField","category"),40),dir=text(input.getOrDefault("sortDir","asc"),4);
        if(!Set.of("category","total","success_count","failure_count","denied_count","last_event_time").contains(field)||!Set.of("asc","desc").contains(dir))throw new IllegalArgumentException("排序参数无效");
        var rows=store.named(scope.tenantId()).queryForList("select * from ("+String.join(" union all ",fragments)+") summary_events order by "+field+" "+dir,params).stream().map(AuditStore::lower).map(row->{if(row.get("last_event_time")!=null)row.put("last_event_time",LogTimes.instant(row.get("last_event_time")).toString());return row;}).toList();
        return Map.of("list",rows,"total",rows.size());
    }
    public List<Map<String,Object>> authorizedEvents(LogScope scope,String kind,List<String> ids,boolean detail) {
        if(ids==null||ids.isEmpty()||ids.size()>100||new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(id->id==null||id.length()>64)) throw new IllegalArgumentException("一次最多查询100个不同事件");
        var s=selection(scope,kind,Map.of(),false);
        String where=s.where()+" and tid in (:ids)";
        var params=new LinkedHashMap<>(s.params());params.put("ids",ids);
        var rows=store.named(scope.tenantId()).queryForList("select "+projection(s.view(),detail)+" from "+s.view().table()+where,params);
        if(rows.size()!=ids.size()) throw new SecurityException("事件不存在或超出当前授权范围");
        return rows.stream().map(row->normalize(row,detail)).toList();
    }
    public Object batchItems(LogScope scope,String kind,String id,Map<String,Object> input){
        authorizedEvents(scope,kind,List.of(id),false);
        var p=new LinkedHashMap<String,Object>();p.put("scopeKind",scope.scopeKind());p.put("scopeId",scope.scopeId());p.put("domain",scope.platform()?scope.domain():"global");p.put("eventId",id);p.put("store",kind);
        long page=number(input.get("page"),1,1,10000),size=number(input.get("size"),20,1,100);p.put("size",size);p.put("offset",(page-1)*size);
        String where=" where scope_kind=:scopeKind and scope_id=:scopeId and identity_domain=:domain and event_id=:eventId and event_store=:store";
        Long total=store.named(scope.tenantId()).queryForObject("select count(*) from log_batch_item_t"+where,p,Long.class);
        var rows=store.named(scope.tenantId()).queryForList("select item_no,target_type,target_id,target_name,action_code,result,reason_code from log_batch_item_t"+where+" order by item_no limit :size offset :offset",p).stream().map(JdbcValueNormalizer::map).map(AuditStore::lower).toList();
        return Map.of("list",rows,"total",total==null?0:total,"page",page,"size",size);
    }
    public String order(Map<String,Object> input) {
        String field=text(input.get("sortField"),40),direction=text(input.get("sortDir"),4);
        Map<String,String> allowed=Map.of("event_time","event_time","actor_name","actor_name","action_code","action_code","duration_ms","duration_ms","total_count","total_count","success_count","success_count","failure_count","failure_count");
        if(!field.isEmpty()&&!allowed.containsKey(field)) throw new IllegalArgumentException("排序字段无效");
        if(!direction.isEmpty()&&!Set.of("asc","desc").contains(direction)) throw new IllegalArgumentException("排序方向无效");
        return (field.isEmpty()?"event_time":allowed.get(field))+" "+(direction.isEmpty()?"desc":direction)+",tid asc";
    }
    static String text(Object value,int max) { String result=Objects.toString(value,"");if(result.length()>max) throw new IllegalArgumentException("日志查询内容过长");return result; }
    static long number(Object value,long fallback,long min,long max) {
        try { long result=value==null?fallback:Long.parseLong(String.valueOf(value));if(result<min||result>max) throw new NumberFormatException();return result; }
        catch(NumberFormatException e) { throw new IllegalArgumentException("分页参数无效"); }
    }
    private Instant parseTime(Object input,Instant fallback) {
        if(input==null||String.valueOf(input).isBlank()) return fallback;
        try { return Instant.parse(String.valueOf(input)); }catch(Exception e) { throw new IllegalArgumentException("日志时间格式无效，请使用带时区的时间"); }
    }
}

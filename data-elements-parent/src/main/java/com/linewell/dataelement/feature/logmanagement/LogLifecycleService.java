package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.audit.*;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded exports, policy revisions and verified archives, all under explicit current scopes. */
@Service
public class LogLifecycleService {
    private final AuditStore store; private final LogAccessService access; private final LogQueryService query;
    private final ObjectMapper mapper;private final AuditRecorder audit;private final AuditPolicyCache policyCache;private final LogJobLeases leases;private final Path directory;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"log-file-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public LogLifecycleService(AuditStore store,LogAccessService access,LogQueryService query,ObjectMapper mapper,AuditRecorder audit,AuditPolicyCache policyCache,LogJobLeases leases,@Value("${data-element.audit.files-path:../logs/backend/log-files}") String path) {
        this.store=store;this.access=access;this.query=query;this.mapper=mapper;this.audit=audit;this.policyCache=policyCache;this.leases=leases;this.directory=Path.of(path).toAbsolutePath().normalize();
    }
    private Map<String,Object> scopeParams(LogScope scope) {return new LinkedHashMap<>(Map.of("scopeKind",scope.scopeKind(),"scopeId",scope.scopeId(),"owner",scope.actorId(),"identityDomain",scope.platform()?scope.domain():"global"));}
    private String ownedWhere(LogScope scope,boolean owner) {return " where scope_kind=:scopeKind and scope_id=:scopeId and identity_domain=:identityDomain"+(owner?" and owner_id=:owner":"");}
    private void requireAll(LogScope scope) {if(!scope.mode().equals("ALL")||scope.platform()&&scope.appId()!=null) throw new SecurityException("此操作需要日志模块的完整范围授权");}
    public Object page(LogScope scope,String module,Map<String,Object> input) {
        if(module.equals("policies")) {
            var result=new ArrayList<>(policies(scope));
            String field=LogQueryService.text(input.getOrDefault("sortField","category"),40),direction=LogQueryService.text(input.getOrDefault("sortDir","asc"),4);
            if(!Set.of("category","hot_days","total_retention_days","sample_rate","actor_name","updated_time").contains(field)||!Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("排序参数无效");
            Comparator<Map<String,Object>> comparator=(left,right)->{Object a=left.get(field),b=right.get(field);if(a==null)return b==null?0:1;if(b==null)return -1;int compared=a instanceof Number&&b instanceof Number?Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue()):a.toString().compareTo(b.toString());return direction.equals("desc")?-compared:compared;};
            String keyword=LogQueryService.text(input.get("keyword"),100).toLowerCase(Locale.ROOT);result.removeIf(row->!keyword.isBlank()&&!String.valueOf(row.get("category")).toLowerCase(Locale.ROOT).contains(keyword)&&!Map.of("AUTH","登录日志","OPERATION","操作日志","REQUEST","接口日志","SECURITY","安全事件","TASK","任务与集成").get(String.valueOf(row.get("category"))).contains(keyword));
            result.sort(comparator);return result.stream().map(this::wireTimes).toList();
        }
        if(!Set.of("exports","archives").contains(module)) throw new IllegalArgumentException("日志模块无效");
        if(module.equals("archives")) requireAll(scope);
        String table=module.equals("exports")?"log_export_job_t":"log_archive_batch_t";
        var p=scopeParams(scope);long page=LogQueryService.number(input.get("page"),1,1,10000),size=LogQueryService.number(input.get("size"),20,1,100);
        String where=ownedWhere(scope,module.equals("exports"));
        String keyword=LogQueryService.text(input.get("keyword"),100),status=LogQueryService.text(input.get("status"),24);
        if(!keyword.isBlank()){p.put("keyword","%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");where+=" and (tid like :keyword escape '!' or actor_name like :keyword escape '!' or "+(module.equals("exports")?"format_code":"event_store")+" like :keyword escape '!')";}
        if(!status.isBlank()){if(!Set.of("PENDING","RUNNING","SUCCESS","FAILED","EXPIRED","CANCELLED","PREPARING","WRITTEN","VERIFIED","PURGING","COMPLETE").contains(status))throw new IllegalArgumentException("状态无效");p.put("status",status);where+=" and status=:status";}
        if(input.get("startTime")!=null&&input.get("endTime")!=null){Instant start=Instant.parse(String.valueOf(input.get("startTime"))),end=Instant.parse(String.valueOf(input.get("endTime")));if(!start.isBefore(end)||Duration.between(start,end).compareTo(Duration.ofDays(90))>0)throw new IllegalArgumentException("请选择不超过90天的时间区间");p.put("createdStart",LogTimes.utc(start));p.put("createdEnd",LogTimes.utc(end));where+=" and created_time>=:createdStart and created_time<:createdEnd";}
        Long total=store.named(scope.tenantId()).queryForObject("select count(*) from "+table+where,p,Long.class);
        p.put("size",size);p.put("offset",(page-1)*size);
        String fields="actor_name,actor_account,actor_org_name,"+(module.equals("exports")?"tid,format_code,status,row_limit,row_count,created_time,finished_at,expires_at,last_error_code":"tid,event_store,status,record_count,byte_count,range_start,range_end,hold_status,created_time,verified_at");
        Map<String,String> sorts=new HashMap<>(Map.of("created_time","created_time","finished_at",module.equals("exports")?"finished_at":"verified_at","name",module.equals("exports")?"format_code":"event_store","row_count",module.equals("exports")?"row_count":"record_count"));
        sorts.put("actor_name","actor_name");
        if(module.equals("archives")){sorts.put("range_start","range_start");sorts.put("range_end","range_end");}
        String field=LogQueryService.text(input.get("sortField"),40),direction=LogQueryService.text(input.get("sortDir"),4);
        if(!field.isBlank()&&!sorts.containsKey(field)||!direction.isBlank()&&!Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("排序参数无效");
        String column=field.isBlank()?"created_time":sorts.get(field);String order="case when "+column+" is null then 1 else 0 end,"+column+" "+(direction.isBlank()?"desc":direction)+",tid";
        var rows=store.named(scope.tenantId()).queryForList("select "+fields+" from "+table+where+" order by "+order+" limit :size offset :offset",p).stream().map(JdbcValueNormalizer::map).map(AuditStore::lower).map(this::wireTimes).toList();
        return Map.of("list",rows,"total",total==null?0:total,"page",page,"size",size);
    }
    public List<Map<String,Object>> policies(LogScope scope) {
        requireAll(scope);
        var rows=store.named(scope.tenantId()).queryForList("select actor_name,actor_account,actor_org_name,category,enabled,hot_days,total_retention_days,sample_rate,detail_limit_bytes,archive_enabled,version,updated_time from log_policy_t"+ownedWhere(scope,false)+" order by category",scopeParams(scope)).stream().map(AuditStore::lower).toList();
        List<Map<String,Object>> out=new ArrayList<>();
        for(String category:List.of("AUTH","OPERATION","REQUEST","SECURITY","TASK")) {
            Map<String,Object> row=new LinkedHashMap<>(Map.of("category",category,"enabled",1,"hot_days",category.equals("REQUEST")?30:180,"total_retention_days",category.equals("REQUEST")?90:365,"sample_rate",1,"detail_limit_bytes",32768,"archive_enabled",1,"version",0));
            rows.stream().filter(r->category.equals(r.get("category"))).findFirst().ifPresent(row::putAll);out.add(row);
        }
        return out;
    }
    public Object savePolicy(LogScope scope,Map<String,Object> input) {
        requireAll(scope);
        String category=LogQueryService.text(input.get("category"),24);
        if(!Set.of("AUTH","OPERATION","REQUEST","SECURITY","TASK").contains(category))throw new IllegalArgumentException("日志类别无效");
        int minimum=category.equals("REQUEST")?1:180;
        long hot=LogQueryService.number(input.get("hotDays"),180,minimum,3650),retention=LogQueryService.number(input.get("retentionDays"),365,hot,3650),version=LogQueryService.number(input.get("version"),0,0,Long.MAX_VALUE);
        double sample;try {sample=Double.parseDouble(String.valueOf(input.getOrDefault("sampleRate",1)));}catch(Exception e) {throw new IllegalArgumentException("采样比例无效");}
        if(!Double.isFinite(sample)||sample<=0||sample>1||!category.equals("REQUEST")&&sample!=1)throw new IllegalArgumentException("强制审计类别必须全量记录");
        var p=scopeParams(scope);p.put("category",category);p.put("hot",hot);p.put("retention",retention);p.put("sample",sample);p.put("version",version);p.put("now",LogTimes.utc(Instant.now()));p.putAll(actorSnapshot());
        Object saved=new TransactionTemplate(new DataSourceTransactionManager(store.dataSource(scope.tenantId()))).execute(tx->{
            if(version==0) {
                var row=base(scope);row.put("category",category);row.put("hot_days",hot);row.put("total_retention_days",retention);row.put("sample_rate",sample);row.put("detail_limit_bytes",32768);row.put("enabled",1);row.put("archive_enabled",1);row.put("updated_by",scope.actorId());row.put("updated_time",p.get("now"));row.put("version",1);
                store.insert(scope.tenantId(),"log_policy_t",row);
            } else {
                int count=store.named(scope.tenantId()).update("update log_policy_t set hot_days=:hot,total_retention_days=:retention,sample_rate=:sample,updated_by=:owner,actor_name=:actor_name,actor_account=:actor_account,actor_org_name=:actor_org_name,updated_time=:now,version=version+1"+ownedWhere(scope,false)+" and category=:category and version=:version",p);
                if(count!=1) throw new IllegalStateException("策略已被其他人修改，请刷新后重试");
            }
            audit.record(Objects.requireNonNull(audit.current()),"OPERATION","LOG_POLICY_UPDATED","SUCCESS",null,"LOG_POLICY",category,category,Map.of("category",category,"hotDays",hot,"retentionDays",retention));
            return Map.of("saved",true);
        });
        policyCache.invalidate(scope.tenantId(),scope.platform()?scope.domain():"global");return saved;
    }
    public Object createExport(LogScope permission,Map<String,Object> input) {
        String kind=kind(input);LogScope read=access.refresh(permission,kind);LogScope scope=access.intersect(read,permission);
        query.selection(scope,kind,input);
        String format=LogQueryService.text(input.getOrDefault("format","CSV"),10);
        if(!Set.of("CSV","JSONL").contains(format))throw new IllegalArgumentException("导出格式无效");
        long limit=LogQueryService.number(input.get("rowLimit"),100000,1,100000);
        var normalized=new LinkedHashMap<>(input);Instant cutoff=Instant.now();normalized.put("endTime",cutoff.toString());normalized.put("receivedBefore",cutoff.toString());
        if(input.get("endTime")!=null&&Instant.parse(String.valueOf(input.get("endTime"))).isBefore(cutoff)) normalized.put("endTime",input.get("endTime"));
        var row=base(scope);String id=String.valueOf(row.get("tid"));
        row.put("owner_kind",scope.platform()?"OPERATOR":"TENANT_USER");row.put("owner_id",scope.actorId());row.put("query_json",json(normalized));row.put("scope_snapshot_json",json(scope));row.put("cutoff_time",LogTimes.utc(cutoff));row.put("status","PENDING");row.put("format_code",format);row.put("row_limit",limit);row.put("row_count",0);row.put("updated_time",row.get("created_time"));row.put("version",1);
        row.putAll(leases.fields());
        new TransactionTemplate(new DataSourceTransactionManager(store.dataSource(scope.tenantId()))).executeWithoutResult(tx->{store.insert(scope.tenantId(),"log_export_job_t",row);audit.record(Objects.requireNonNull(audit.current()),"OPERATION","LOG_EXPORT_SUBMITTED","ACCEPTED",null,"LOG_EXPORT",id,"日志导出",Map.of("format",format,"count",limit));});
        leases.track(scope,"log_export_job_t",id);
        try {workers.execute(()->export(scope,id,kind,normalized,format,limit));} catch(RejectedExecutionException full) {finish(scope,"log_export_job_t",id,"FAILED",Map.of("last_error_code","QUEUE_FULL"));}
        return Map.of("id",id,"status","PENDING");
    }
    private void export(LogScope before,String id,String kind,Map<String,Object> input,String format,long limit) {
        Path path=directory.resolve(id+"."+format.toLowerCase(Locale.ROOT));
        try {
            access.refresh(before,"exports");LogScope current=access.intersect(before,access.refresh(before,kind));
            finish(before,"log_export_job_t",id,"RUNNING",Map.of());Files.createDirectories(directory);
            long count=writeFile(current,kind,input,path,format,limit);
            access.refresh(before,"exports");access.refresh(before,kind);
            finish(before,"log_export_job_t",id,"SUCCESS",Map.of("row_count",count,"object_key",path.getFileName().toString(),"sha256",hash(path),"finished_at",LogTimes.utc(Instant.now()),"expires_at",LogTimes.utc(Instant.now().plus(Duration.ofHours(24)))));
        } catch(Exception error) {
            try {Files.deleteIfExists(path);}catch(Exception ignored) { }
            finish(before,"log_export_job_t",id,"FAILED",Map.of("last_error_code",error instanceof SecurityException?"PERMISSION_CHANGED":"EXPORT_FAILED"));
        }
    }
    private long writeFile(LogScope scope,String kind,Map<String,Object> input,Path path,String format,long limit) throws Exception {
        var selection=query.selection(scope,kind,input);long count=0;
        try(var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new LimitedOutput(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)),StandardCharsets.UTF_8))) {
            if(format.equals("CSV"))writer.write("\ufeff发生时间,姓名,账号,部门,功能路径,具体操作,操作编码,目标,结果,来源IP,请求号,链路号\n");
            while(count<limit) {
                // Recheck by chunk, never one query per event; scopes can only shrink.
                access.refresh(scope,"exports");scope=access.intersect(scope,access.refresh(scope,kind));selection=query.selection(scope,kind,input);
                var params=new LinkedHashMap<>(selection.params());params.put("size",Math.min(500,limit-count));params.put("offset",count);
                var rows=query.read(scope,selection,params,"event_time asc,tid asc",format.equals("JSONL"));if(rows.isEmpty())break;
                for(var row:rows) {
                    if(format.equals("JSONL"))writer.write(json(row)+"\n");
                    else {List<String> cells=new ArrayList<>();for(String key:List.of("event_time","actor_name","actor_account","actor_org_name","menu_path","operation_name","action_code","target_name","result","ip","request_id","trace_id"))cells.add(csv(row.get(key)));writer.write(String.join(",",cells)+"\n");}
                }
                count+=rows.size();
            }
        }
        return count;
    }
    public Path download(LogScope scope,String id) {
        var p=scopeParams(scope);p.put("id",id);
        var rows=store.named(scope.tenantId()).queryForList("select status,object_key,sha256,expires_at,scope_snapshot_json,query_json from log_export_job_t"+ownedWhere(scope,true)+" and tid=:id",p);
        if(rows.size()!=1)throw new SecurityException("导出任务不存在或无权下载");
        var row=AuditStore.lower(JdbcValueNormalizer.map(rows.getFirst()));
        try {
            LogScope original=mapper.readValue(String.valueOf(row.get("scope_snapshot_json")),LogScope.class);
            if(!Objects.equals(original.domain(),scope.domain()))throw new SecurityException("导出身份域不匹配");
            LogScope now=access.refresh(original,kind(mapper.readValue(String.valueOf(row.get("query_json")),Map.class)));
            // A narrower scope requires a fresh export; previously generated broader files are never served.
            if(!sameRange(access.intersect(original,now),original))throw new SecurityException("数据范围已变更，请重新导出");
            if(!"SUCCESS".equals(row.get("status"))||row.get("expires_at")==null||LogTimes.instant(row.get("expires_at")).isBefore(Instant.now()))throw new IllegalStateException("导出文件未完成或已失效");
            String key=String.valueOf(row.get("object_key"));if(!key.matches("[a-f0-9]{32}\\.(csv|jsonl)"))throw new IllegalStateException("导出文件标识无效");
            Path path=directory.resolve(key).normalize();if(!path.startsWith(directory)||!Files.isRegularFile(path)||Files.isSymbolicLink(path)||!path.toRealPath().getParent().equals(directory.toRealPath()))throw new IllegalStateException("导出文件不可用");
            if(!Objects.equals(row.get("sha256"),hash(path)))throw new IllegalStateException("导出文件完整性校验失败，请重新导出");
            audit.record(Objects.requireNonNull(audit.current()),"OPERATION","LOG_EXPORT_DOWNLOADED","SUCCESS",null,"LOG_EXPORT",id,null,null);
            return path;
        }catch(SecurityException|IllegalStateException e){throw e;}catch(Exception e) {throw new IllegalStateException("导出任务无法读取",e);}
    }
    public Object createArchive(LogScope scope,Map<String,Object> input) {
        requireAll(scope);String kind=kind(input);LogScope read=access.refresh(scope,kind);var s=query.selection(read,kind,input);
        if(input.get("startTime")==null||input.get("endTime")==null)throw new IllegalArgumentException("请选择归档时间区间");
        String category=kind.equals("auth")?"AUTH":kind.equals("requests")?"REQUEST":kind.equals("security")?"SECURITY":"OPERATION";
        var policy=policies(scope).stream().filter(r->category.equals(r.get("category"))).findFirst().orElseThrow();
        Instant end=Instant.parse(String.valueOf(input.get("endTime")));
        if(end.isAfter(Instant.now().minus(Duration.ofDays(((Number)policy.get("hot_days")).longValue()))))throw new IllegalArgumentException("归档区间仍在在线保留期内");
        var row=base(scope);String id=String.valueOf(row.get("tid"));
        var rangeParams=scopeParams(scope);rangeParams.put("kind",kind);rangeParams.put("start",s.params().get("start"));rangeParams.put("end",s.params().get("end"));
        Long generation=store.named(scope.tenantId()).queryForObject("select coalesce(max(generation),0)+1 from log_archive_batch_t"+ownedWhere(scope,false)+" and event_store=:kind and range_start=:start and range_end=:end",rangeParams,Long.class);
        row.put("event_store",kind);row.put("range_start",s.params().get("start"));row.put("range_end",s.params().get("end"));row.put("generation",generation==null?1:generation);row.put("status","PREPARING");row.put("record_count",0);row.put("byte_count",0);row.put("retention_until",LogTimes.utc(end.plus(Duration.ofDays(((Number)policy.get("total_retention_days")).longValue()))));row.put("hold_status","NONE");row.put("operator_id",scope.actorId());row.put("updated_time",row.get("created_time"));row.put("version",1);
        row.putAll(leases.fields());
        new TransactionTemplate(new DataSourceTransactionManager(store.dataSource(scope.tenantId()))).executeWithoutResult(tx->{store.insert(scope.tenantId(),"log_archive_batch_t",row);audit.record(Objects.requireNonNull(audit.current()),"OPERATION","LOG_ARCHIVE_SUBMITTED","ACCEPTED",null,"LOG_ARCHIVE",id,"日志归档",Map.of("category",category));});
        leases.track(scope,"log_archive_batch_t",id);
        var frozen=new LinkedHashMap<>(input);frozen.put("receivedBefore",Instant.now().toString());
        try {workers.execute(()->archive(scope,id,kind,frozen));}catch(RejectedExecutionException full) {finish(scope,"log_archive_batch_t",id,"FAILED",Map.of());}
        return Map.of("id",id,"status","PREPARING");
    }
    private void archive(LogScope scope,String id,String kind,Map<String,Object> input) {
        Path path=directory.resolve(id+".archive.jsonl");
        try {
            requireAll(access.refresh(scope,"archives"));Files.createDirectories(directory);
            var selection=query.selection(access.refresh(scope,kind),kind,input);
            Long count=store.named(scope.tenantId()).queryForObject("select count(*) from "+selection.view().table()+selection.where(),selection.params(),Long.class);
            if(count!=null&&count>100000)throw new IllegalArgumentException("归档范围过大，请缩小时间区间");
            // Export read primitive checks the archive capability separately; no write/delete side effects.
            String[] writtenDigest=new String[1];long actual=writeArchiveFile(scope,kind,input,path,writtenDigest);
            String digest=hash(path);long verified;
            try(var lines=Files.lines(path,StandardCharsets.UTF_8)) {verified=lines.count();}
            if(verified!=actual||!digest.equals(writtenDigest[0]))throw new IllegalStateException("归档回读数量或摘要不一致");
            Path manifest=directory.resolve(id+".manifest.json");Files.writeString(manifest,json(Map.of("id",id,"count",actual,"sha256",digest,"scopeKind",scope.scopeKind(),"scopeId",scope.scopeId())),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            finish(scope,"log_archive_batch_t",id,"VERIFIED",Map.of("record_count",actual,"byte_count",Files.size(path),"object_key",path.getFileName().toString(),"manifest_key",manifest.getFileName().toString(),"sha256",digest,"verified_at",LogTimes.utc(Instant.now())));
            // Archiving never implicitly purges historical evidence. Retention cleanup is an explicit separate action.
        }catch(Exception error) {try{Files.deleteIfExists(path);Files.deleteIfExists(directory.resolve(id+".manifest.json"));}catch(Exception ignored){}finish(scope,"log_archive_batch_t",id,"FAILED",Map.of());}
    }
    private long writeArchiveFile(LogScope scope,String kind,Map<String,Object> input,Path path,String[] digest) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        long count=0;try(var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.security.DigestOutputStream(new LimitedOutput(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)),md),StandardCharsets.UTF_8))) {
            while(count<100000) {
                requireAll(access.refresh(scope,"archives"));var current=access.refresh(scope,kind);requireAll(current);
                var s=query.selection(current,kind,input);var p=new LinkedHashMap<>(s.params());p.put("size",500);p.put("offset",count);
                var rows=query.read(current,s,p,"event_time asc,tid asc",true);if(rows.isEmpty())break;
                for(var row:rows)writer.write(json(row)+"\n");count+=rows.size();
            }
        }digest[0]=HexFormat.of().formatHex(md.digest());return count;
    }
    Path directory(){return directory;}
    private static class LimitedOutput extends java.io.FilterOutputStream{
        private long bytes;
        LimitedOutput(java.io.OutputStream out){super(out);}
        @Override public void write(int value)throws java.io.IOException{if(++bytes>268435456)throw new java.io.IOException("LOG_FILE_SIZE_LIMIT");out.write(value);}
        @Override public void write(byte[] value,int offset,int length)throws java.io.IOException{if((bytes+=length)>268435456)throw new java.io.IOException("LOG_FILE_SIZE_LIMIT");out.write(value,offset,length);}
    }
    private Map<String,Object> actorSnapshot(){var actor=audit.current();var values=new LinkedHashMap<String,Object>();values.put("actor_name",actor==null?null:actor.name());values.put("actor_account",actor==null?null:actor.account());values.put("actor_org_name",actor==null?null:actor.orgName());return values;}
    private Map<String,Object> base(LogScope scope) {
        var row=new LinkedHashMap<String,Object>();row.put("tid",AuditTrace.random());row.put("scope_kind",scope.scopeKind());row.put("scope_id",scope.scopeId());row.put("tenant_id",scope.tenantId());row.put("identity_domain",scope.platform()?scope.domain():"global");row.put("created_time",LogTimes.utc(Instant.now()));row.putAll(actorSnapshot());return row;
    }
    private void finish(LogScope scope,String table,String id,String status,Map<String,Object> fields) {
        boolean terminal=Set.of("SUCCESS","FAILED","VERIFIED","COMPLETE","EXPIRED").contains(status);
        var values=new LinkedHashMap<>(fields);values.put("status",status);values.put("updated_time",LogTimes.utc(Instant.now()));if(terminal)values.put("lease_until",null);
        var p=scopeParams(scope);p.put("id",id);p.put("worker",leases.workerId());p.put("now",LogTimes.utc(Instant.now()));p.putAll(values);
        String assignments=String.join(",",values.keySet().stream().map(key->key+"=:"+key).toList());
        try {
            new TransactionTemplate(new DataSourceTransactionManager(store.dataSource(scope.tenantId()))).executeWithoutResult(tx->{
                int changed=store.named(scope.tenantId()).update("update "+table+" set "+assignments+",version=version+1"+ownedWhere(scope,false)+" and tid=:id and worker_id=:worker and lease_until>=:now and status in ('PENDING','RUNNING','PREPARING','WRITTEN')",p);
                if(changed!=1)throw new IllegalStateException("日志任务租约已失效");
                if(terminal) {
                    boolean export=table.equals("log_export_job_t");
                    var actor=new AuditIdentity(scope.tenantId(),"SYSTEM",null,null,"后台任务",null,null,scope.sourceApp(),null,scope.domain());
                    var event=audit.prepare(actor,"TASK",export?"LOG_EXPORT_FINISHED":"LOG_ARCHIVE_FINISHED",status.equals("FAILED")?"FAILED":"SUCCESS",null,export?"LOG_EXPORT":"LOG_ARCHIVE",id,export?"日志导出":"日志归档",Map.of("id",id,"phase",status));
                    event.row().put("task_id",id);event.row().put("origin_key","LOG_JOB:"+id+":"+status);store.insert(scope.tenantId(),event.table(),event.row());
                }
            });
        } finally {if(terminal)leases.forget(id);}
    }
    private Map<String,Object> wireTimes(Map<String,Object> row) {row.replaceAll((key,value)->value!=null&&(key.endsWith("_time")||key.endsWith("_at")||key.equals("range_start")||key.equals("range_end"))?LogTimes.instant(value).toString():value);return row;}
    private boolean sameRange(LogScope a,LogScope b) {return Objects.equals(a.tenantId(),b.tenantId())&&Objects.equals(a.actorId(),b.actorId())&&Objects.equals(a.domain(),b.domain())&&Objects.equals(a.appId(),b.appId())&&a.mode().equals(b.mode())&&new HashSet<>(a.orgIds()).equals(new HashSet<>(b.orgIds()));}
    private String kind(Map<String,Object> input) {String kind=LogQueryService.text(input.get("kind"),20);if(!Set.of("auth","operations","requests","security").contains(kind))throw new IllegalArgumentException("请选择日志类别");return kind;}
    private String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception error){throw new IllegalArgumentException("日志任务内容无效",error);}}
    private String hash(Path path) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>0)md.update(buffer,0,n);}return HexFormat.of().formatHex(md.digest());}
    private String csv(Object value) {String text=Objects.toString(value,"").replaceAll("[\\r\\n\\p{Cntrl}]"," ");if(text.matches("^[=+@-].*"))text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
    @PreDestroy public void close() {workers.shutdown();try{workers.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}

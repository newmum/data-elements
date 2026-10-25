package com.linewell.dataelement.feature.logmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.audit.*;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit archive holds, verified retrieval, and exact-event hot-store cleanup. Never a range-only delete. */
@Service
public class LogArchiveService {
    private final AuditStore store;private final LogAccessService access;private final LogQueryService query;
    private final LogLifecycleService lifecycle;private final AuditRecorder audit;private final ObjectMapper mapper;
    public LogArchiveService(AuditStore store,LogAccessService access,LogQueryService query,LogLifecycleService lifecycle,AuditRecorder audit,ObjectMapper mapper){this.store=store;this.access=access;this.query=query;this.lifecycle=lifecycle;this.audit=audit;this.mapper=mapper;}
    private void all(LogScope scope){if(!scope.mode().equals("ALL")||scope.appId()!=null)throw new SecurityException("归档管理需要该日志模块的完整范围授权");}
    private Map<String,Object> parameters(LogScope scope,String id){return new LinkedHashMap<>(Map.of("kind",scope.scopeKind(),"scope",scope.scopeId(),"domain",scope.platform()?scope.domain():"global","id",id));}
    private String where(){return " where scope_kind=:kind and scope_id=:scope and identity_domain=:domain and tid=:id";}
    private Map<String,Object> row(LogScope scope,String id,boolean lock){
        var rows=store.named(scope.tenantId()).queryForList("select * from log_archive_batch_t"+where()+(lock?" for update":""),parameters(scope,id));
        if(rows.size()!=1)throw new SecurityException("归档不存在或无权访问");return AuditStore.lower(JdbcValueNormalizer.map(rows.getFirst()));
    }
    public Object action(LogScope scope,String id,Map<String,Object> input){
        all(scope);String action=LogQueryService.text(input.get("action"),20);
        if(action.equals("browse"))return browse(scope,row(scope,id,false),input);
        if(!Set.of("hold","release","purge-plan","purge").contains(action))throw new IllegalArgumentException("归档操作无效");
        return new TransactionTemplate(new DataSourceTransactionManager(store.dataSource(scope.tenantId()))).execute(tx->{
            var archive=row(scope,id,true);var p=parameters(scope,id);p.put("now",LogTimes.utc(Instant.now()));
            if(Set.of("hold","release").contains(action)){
                String reason=LogQueryService.text(input.get("reason"),512).trim();if(reason.isEmpty())throw new IllegalArgumentException("请填写冻结或解除冻结的原因");
                p.put("hold",action.equals("hold")?"HELD":"NONE");p.put("reason",reason);
                store.named(scope.tenantId()).update("update log_archive_batch_t set hold_status=:hold,hold_reason=:reason,updated_time=:now,version=version+1"+where(),p);
                audit.record(Objects.requireNonNull(audit.current()),"OPERATION",action.equals("hold")?"LOG_ARCHIVE_HELD":"LOG_ARCHIVE_RELEASED","SUCCESS",null,"LOG_ARCHIVE",id,null,Map.of("phase",action));
                return Map.of("saved",true);
            }
            if(!Set.of("VERIFIED","COMPLETE").contains(Objects.toString(archive.get("status"))))throw new IllegalStateException("只允许清理已经校验完成的归档");
            if("HELD".equals(archive.get("hold_status")))throw new IllegalStateException("此归档已冻结，不能清理");
            String kind=String.valueOf(archive.get("event_store"));LogScope read=access.refresh(scope,kind);all(read);
            p.put("store",kind);p.put("start",archive.get("range_start"));p.put("end",archive.get("range_end"));
            Long held=store.named(scope.tenantId()).queryForObject("select count(*) from log_archive_batch_t where scope_kind=:kind and scope_id=:scope and identity_domain=:domain and event_store=:store and hold_status='HELD' and range_start<:end and range_end>:start",p,Long.class);
            if(held!=null&&held>0)throw new IllegalStateException("时间区间内存在冻结归档，不能清理");
            var events=readFile(archive);Set<String> archivedIds=new HashSet<>();events.forEach(event->archivedIds.add(String.valueOf(event.get("tid"))));
            var selection=query.selection(read,kind,Map.of("startTime",LogTimes.instant(archive.get("range_start")).toString(),"endTime",LogTimes.instant(archive.get("range_end")).toString()));
            // One bounded preload followed by chunked writes; no per-event database reads.
            var existing=store.named(scope.tenantId()).queryForList("select tid from "+selection.view().table()+selection.where()+" order by tid limit 100001",selection.params(),String.class);
            if(existing.size()>100000)throw new IllegalStateException("当前区间超过单批上限，请重新分段归档");
            var eligible=existing.stream().filter(archivedIds::contains).toList();
            if(action.equals("purge-plan"))return Map.of("count",eligible.size(),"archiveCount",events.size(),"version",archive.get("version"),"verified",true);
            if(!id.equals(input.get("confirmationId"))||LogQueryService.number(input.get("version"),-1,1,Long.MAX_VALUE)!=((Number)archive.get("version")).longValue())throw new IllegalStateException("请先预览清理范围并再次确认");
            for(int offset=0;offset<eligible.size();offset+=500){
                var batch=new LinkedHashMap<>(selection.params());batch.put("ids",eligible.subList(offset,Math.min(offset+500,eligible.size())));
                store.named(scope.tenantId()).update("delete from "+selection.view().table()+selection.where()+" and tid in (:ids)",batch);
            }
            store.named(scope.tenantId()).update("update log_archive_batch_t set status='COMPLETE',purge_completed_at=:now,updated_time=:now,version=version+1"+where(),p);
            audit.record(Objects.requireNonNull(audit.current()),"OPERATION","LOG_ARCHIVE_HOT_PURGED","SUCCESS",null,"LOG_ARCHIVE",id,null,Map.of("count",eligible.size()));
            return Map.of("deleted",eligible.size(),"archiveRetained",true);
        });
    }
    private Object browse(LogScope scope,Map<String,Object> archive,Map<String,Object> input){
        all(access.refresh(scope,String.valueOf(archive.get("event_store"))));
        var events=readFile(archive);String field=LogQueryService.text(input.getOrDefault("sortField","event_time"),40),dir=LogQueryService.text(input.getOrDefault("sortDir","asc"),4);
        if(!Set.of("event_time","action_code","actor_name","duration_ms").contains(field)||!Set.of("asc","desc").contains(dir))throw new IllegalArgumentException("排序参数无效");
        Comparator<Map<String,Object>> order=Comparator.comparing(event->Objects.toString(event.get(field),""));
        if(field.equals("duration_ms"))order=Comparator.comparingLong(event->event.get(field) instanceof Number n?n.longValue():-1);
        events.sort(dir.equals("desc")?order.reversed():order);int page=(int)LogQueryService.number(input.get("page"),1,1,10000),size=(int)LogQueryService.number(input.get("size"),20,1,100),start=Math.min(events.size(),(page-1)*size);
        return Map.of("list",events.subList(start,Math.min(events.size(),start+size)),"total",events.size(),"page",page,"size",size);
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> readFile(Map<String,Object> archive){
        try{
            if(!Set.of("VERIFIED","COMPLETE").contains(String.valueOf(archive.get("status"))))throw new IllegalStateException("归档未通过校验");
            String key=Objects.toString(archive.get("object_key"),"");if(!key.matches("[a-f0-9]{32}\\.archive\\.jsonl"))throw new IllegalStateException("归档标识无效");
            Path base=lifecycle.directory().toRealPath(),file=base.resolve(key);
            if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||!file.toRealPath().getParent().equals(base)||Files.size(file)>268435456)throw new IllegalStateException("归档文件不可用或超出单批容量");
            MessageDigest md=MessageDigest.getInstance("SHA-256");try(var stream=new java.security.DigestInputStream(Files.newInputStream(file),md)){stream.transferTo(java.io.OutputStream.nullOutputStream());}
            if(!HexFormat.of().formatHex(md.digest()).equals(archive.get("sha256")))throw new IllegalStateException("归档完整性校验失败");
            var events=new ArrayList<Map<String,Object>>();try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){String line;while((line=reader.readLine())!=null){if(events.size()>=100000||line.length()>131072)throw new IllegalStateException("归档容量无效");events.add(mapper.readValue(line,Map.class));}}
            if(events.size()!=((Number)archive.get("record_count")).longValue())throw new IllegalStateException("归档数量校验失败");return events;
        }catch(SecurityException|IllegalStateException error){throw error;}catch(Exception error){throw new IllegalStateException("归档文件读取失败",error);}
    }
}

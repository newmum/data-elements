package com.haitong.store;

import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.util.*;

/** All table/column names are constants. No client-controlled SQL or identifiers. */
@Repository
public class RelayRepository {
    private final JdbcTemplate jdbc;private final DatabaseDialect dialect;private final ExchangeLog ledger;private final Json json;
    public RelayRepository(JdbcTemplate jdbc,DatabaseDialect dialect,ExchangeLog ledger,Json json) {this.jdbc=jdbc;this.dialect=dialect;this.ledger=ledger;this.json=json;}
    public ExchangeLog ledger(){return ledger;}
    private <T> List<T> page(String sql,RowMapper<T> mapper,int limit,int offset,Object... args){return jdbc.query(dialect.pageSql(sql),mapper,dialect.pageArgs(limit,offset,args));}
    public JdbcTemplate jdbc() { return jdbc; }
    public long now() { return Objects.requireNonNull(jdbc.queryForObject(dialect.clockSql(),Timestamp.class)).getTime(); }
    private static <T> Optional<T> first(List<T> rows) { return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst()); }
    private static final RowMapper<Message> MESSAGE=(r,i)->new Message(r.getString("id"),r.getString("owner"),r.getString("source_type"),r.getString("source_scope"),r.getString("source_key"),r.getString("route_code"),r.getString("payload_sha256"),r.getString("payload"),r.getString("output_payload"),r.getString("payload_ref"),r.getString("original_name"),r.getString("file_id"),r.getObject("record_no")==null?null:r.getLong("record_no"),r.getString("status"),r.getString("error_code"),r.getString("config_snapshot"),r.getString("source_metadata"),r.getLong("created_at"));
    private static final RowMapper<Delivery> DELIVERY=(r,i)->new Delivery(r.getString("id"),r.getString("message_id"),r.getString("target_code"),Kind.valueOf(r.getString("target_type")),r.getString("target_plan"),r.getString("status"),r.getInt("attempts"),r.getInt("max_attempts"),r.getBoolean("retry_safe"),r.getString("lease_token"),r.getLong("lease_until"),r.getString("result_metadata"),r.getString("last_error_code"),r.getString("last_error_message"),r.getLong("next_attempt_at"));
    private static final RowMapper<FileRow> FILE=(r,i)->new FileRow(r.getString("id"),r.getString("owner"),r.getString("source_type"),r.getString("source_scope"),r.getString("source_key"),r.getString("route_code"),r.getString("original_name"),r.getString("payload_ref"),r.getString("content_sha256"),r.getLong("file_size"),r.getString("content_format"),r.getString("config_snapshot"),r.getString("status"),r.getLong("last_record_no"),r.getLong("total_records"),r.getBoolean("eof_reached"),r.getString("lease_token"),r.getLong("lease_until"),r.getInt("attempts"),r.getString("last_error_code"),r.getLong("created_at"));
    public Optional<Message> message(String id) { return first(jdbc.query("SELECT * FROM ht_message WHERE id=?",MESSAGE,id)); }
    public Optional<Message> findMessage(String scope,String route,String key) {
        return first(jdbc.query("SELECT * FROM ht_message WHERE source_scope=? AND route_code=? AND source_key=?",MESSAGE,scope,route,key));
    }
    public void insertMessage(Message m) {
        jdbc.update("""
          INSERT INTO ht_message(id,owner,source_type,source_scope,source_key,route_code,payload_sha256,payload,output_payload,
          payload_ref,original_name,file_id,record_no,status,error_code,config_snapshot,created_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,m.id(),m.owner(),m.sourceType(),m.sourceScope(),m.sourceKey(),m.routeCode(),m.sha256(),m.payload(),m.outputPayload(),m.fileRef(),m.originalName(),m.fileId(),m.recordNo(),m.status(),m.errorCode(),m.snapshot(),m.createdAt());
    }
    public void insertDelivery(String messageId,TargetPlan target,String plan,int maxAttempts,long time) {
        jdbc.update("""
          INSERT INTO ht_delivery(id,message_id,target_code,target_type,target_plan,status,max_attempts,retry_safe,next_attempt_at,created_at,updated_at,retry_audit)
          VALUES (?,?,?,?,?,'PENDING',?,?,?,?,?,'[]')
          """,UUID.randomUUID().toString(),messageId,target.code(),target.kind().name(),plan,maxAttempts,target.safe(),time,time,time);
    }
    public List<Delivery> deliveries(String messageId) { return jdbc.query("SELECT * FROM ht_delivery WHERE message_id=? ORDER BY target_code",DELIVERY,messageId); }
    public Optional<Delivery> delivery(String id) { return first(jdbc.query("SELECT * FROM ht_delivery WHERE id=?",DELIVERY,id)); }
    public List<Delivery> listDeliveries(String status,int limit,int offset) {
        return status==null || status.isBlank()
            ?page("SELECT * FROM ht_delivery ORDER BY created_at DESC,id",DELIVERY,limit,offset)
            :page("SELECT * FROM ht_delivery WHERE status=? ORDER BY created_at DESC,id",DELIVERY,limit,offset,status);
    }
    public long pendingDeliveries() { return jdbc.queryForObject("SELECT COUNT(*) FROM ht_delivery WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')",Long.class); }
    public long pendingFiles() { return jdbc.queryForObject("SELECT COUNT(*) FROM ht_file_checkpoint WHERE status IN ('READY','PROCESSING','RETRY_WAIT')",Long.class); }
    public Map<String,Long> counts() {
        Map<String,Long> result=new TreeMap<>();
        jdbc.query("SELECT status,COUNT(*) AS n FROM ht_delivery GROUP BY status",r->{ result.put(r.getString("status"),r.getLong("n")); });
        return result;
    }
    public long oldestPendingAgeMs() {
        Long value=jdbc.queryForObject("SELECT MIN(created_at) FROM ht_delivery WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')",Long.class);
        return value==null?0:Math.max(0,now()-value);
    }
    /** Atomic compare-and-set claim, shared by MySQL/DM/H2; no database lock held during I/O. */
    @Transactional
    public Optional<Delivery> claim(Kind kind,long leaseSeconds) {
        long now=now();
        List<String> ids=page("""
          SELECT id FROM ht_delivery WHERE target_type=? AND status IN ('PENDING','RETRY_WAIT')
          AND next_attempt_at<=? AND attempts<max_attempts ORDER BY next_attempt_at,created_at,id
          """,(r,i)->r.getString("id"),16,0,kind.name(),now);
        for(String id:ids) {
            String token=UUID.randomUUID().toString();
            int n=jdbc.update("""
              UPDATE ht_delivery SET status='RUNNING',attempts=attempts+1,lease_token=?,lease_until=?,updated_at=?,attempt_started_at=?
              WHERE id=? AND status IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=? AND attempts<max_attempts
              """,token,now+leaseSeconds*1000,now,now,id,now);
            if(n==1) {Delivery d=delivery(id).orElseThrow();Map<String,Object> c=deliveryContext(d);c.put("startedAt",now);
                ledger.append(ExchangeLog.protocol(d.kind().name()),"OUT","DELIVERY","START","RUNNING",c);return Optional.of(d);}
        }
        return Optional.empty();
    }
    public boolean renew(Delivery d,long seconds) {
        long time=now();
        return jdbc.update("UPDATE ht_delivery SET lease_until=?,updated_at=? WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>?",time+seconds*1000,time,d.id(),d.leaseToken(),time)==1;
    }
    private Map<String,Object> deliveryContext(Delivery d){
        Map<String,Object> c=ledger.deliveryContext(message(d.messageId()).orElseThrow(),d);
        Long started=jdbc.queryForObject("SELECT attempt_started_at FROM ht_delivery WHERE id=?",Long.class,d.id());
        if(started!=null){c.put("startedAt",started);c.put("durationMs",Math.max(0,now()-started));}return c;
    }
    @Transactional
    public boolean complete(Delivery d,String result) {
        long time=now();int count=jdbc.update("""
          UPDATE ht_delivery SET status='SUCCEEDED',result_metadata=?,last_error_code=NULL,last_error_message=NULL,
          lease_token=NULL,lease_until=0,completed_at=?,updated_at=?
          WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>?
          """,result,time,time,d.id(),d.leaseToken(),time);
        if(count==1) {
            Map<String,Object> c=deliveryContext(d);Map<String,Object> parsed=json.object(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Map<String,Object> details=new LinkedHashMap<>();
            for(String key:List.of("httpStatus","completion","topic","partition","offset","path","sha256","bytes","size","fileName","recordCount","marker","completionMarker","alreadyExisted"))
                if(parsed.containsKey(key))details.put(key,parsed.get(key));
            details.put("attemptNumber",d.attempts());c.put("details",details);
            if(parsed.containsKey("httpStatus"))c.put("httpStatus",parsed.get("httpStatus"));
            if(parsed.containsKey("sha256"))c.put("sha256",parsed.get("sha256"));
            if(parsed.containsKey("bytes"))c.put("payloadBytes",parsed.get("bytes"));
            else if(parsed.containsKey("size"))c.put("payloadBytes",parsed.get("size"));
            if(parsed.containsKey("path"))c.put("destinationRef",parsed.get("path"));
            ledger.append(ExchangeLog.protocol(d.kind().name()),"OUT","DELIVERY","FINISH","SUCCEEDED",c);
        }return count==1;
    }
    @Transactional
    public boolean fail(Delivery d,String code,boolean permanent,boolean unknown) { return fail(d,code,permanent,unknown,Map.of()); }
    public boolean fail(Delivery d,String code,boolean permanent,boolean unknown,Map<String,Object> diagnostics) {
        long time=now();String status=unknown?"UNKNOWN":permanent || d.attempts()>=d.maxAttempts()?"FAILED":"RETRY_WAIT";
        long next=time+Rules.backoffMs(d.attempts(),Math.random());
        String message=diagnosticMessage(code,diagnostics);
        int count=jdbc.update("""
          UPDATE ht_delivery SET status=?,last_error_code=?,last_error_message=?,next_attempt_at=?,lease_token=NULL,lease_until=0,updated_at=?
          WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>?
          """,status,code,message,next,time,d.id(),d.leaseToken(),time);
        if(count==1){Map<String,Object> c=deliveryContext(d);c.put("errorCode",code);
            Map<String,Object> details=new LinkedHashMap<>();
            Object existingDetails=c.get("details");
            if(existingDetails instanceof Map<?,?> existing) {
                for(var entry:existing.entrySet()) if(entry.getKey()!=null) details.put(String.valueOf(entry.getKey()),entry.getValue());
            }
            if(diagnostics!=null && !diagnostics.isEmpty()) details.put("diagnostics",diagnostics);
            c.put("details",details);
            if(code.startsWith("BACKEND_HTTP_"))try{c.put("httpStatus",Integer.parseInt(code.substring(13)));}catch(NumberFormatException ignored){}
            ledger.append(ExchangeLog.protocol(d.kind().name()),"OUT","DELIVERY","FINISH",unknown?"UNKNOWN":"FAILED",c);
        }return count==1;
    }
    private String diagnosticMessage(String code,Map<String,Object> diagnostics) {
        if(diagnostics==null || diagnostics.isEmpty()) return "目标处理失败，请凭错误码和任务编号核查";
        String stage=Objects.toString(diagnostics.get("stage"),"");
        String reply=Objects.toString(diagnostics.get("ftpReplyCode"),"");
        String result=(stage.isBlank()?code:code+" stage="+stage)+(reply.isBlank()?"":" ftpReply="+reply);
        return result.length()<=255?result:result.substring(0,255);
    }
    @Transactional
    public int recoverExpired() {
        long time=now();int recovered=0;
        for(Delivery d:page("SELECT * FROM ht_delivery WHERE status='RUNNING' AND lease_until<=? ORDER BY lease_until,id",DELIVERY,200,0,time)){
            String status=!d.safe()?"UNKNOWN":d.attempts()>=d.maxAttempts()?"FAILED":"RETRY_WAIT";
            int count=jdbc.update("""
              UPDATE ht_delivery SET status=?,last_error_code='LEASE_EXPIRED',last_error_message='执行者租约超期，需要重试或核实',
              next_attempt_at=?,lease_token=NULL,lease_until=0,updated_at=? WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until<=?
              """,status,time,time,d.id(),d.leaseToken(),time);
            if(count==1){Map<String,Object> c=deliveryContext(d);c.put("errorCode","LEASE_EXPIRED");
                ledger.append(ExchangeLog.protocol(d.kind().name()),"OUT","DELIVERY_RECOVERY","FINISH","UNKNOWN",c);recovered++;}
        }return recovered;
    }
    public String audit(String id) { return jdbc.queryForObject("SELECT retry_audit FROM ht_delivery WHERE id=?",String.class,id); }
    public boolean retry(String id,String audit) {
        long time=now();
        return jdbc.update("""
          UPDATE ht_delivery SET status='RETRY_WAIT',max_attempts=attempts+10,next_attempt_at=?,last_error_code=NULL,
          last_error_message=NULL,retry_audit=?,updated_at=? WHERE id=? AND status='FAILED' AND retry_safe=1
          """,time,audit,time,id)==1;
    }
    public boolean resolve(String id,boolean succeeded,String audit,String result) {
        long time=now();
        return jdbc.update("""
          UPDATE ht_delivery SET status=?,max_attempts=attempts+10,next_attempt_at=?,retry_audit=?,result_metadata=?,
          last_error_code=NULL,last_error_message=NULL,completed_at=?,updated_at=? WHERE id=? AND status='UNKNOWN'
          """,succeeded?"SUCCEEDED":"RETRY_WAIT",time,audit,result,succeeded?time:null,time,id)==1;
    }
    public Optional<FileRow> file(String id) { return first(jdbc.query("SELECT * FROM ht_file_checkpoint WHERE id=?",FILE,id)); }
    public Optional<FileRow> findFile(String scope,String route,String key) {
        return first(jdbc.query("SELECT * FROM ht_file_checkpoint WHERE source_scope=? AND route_code=? AND source_key=?",FILE,scope,route,key));
    }
    public void insertFile(FileRow f) {
        jdbc.update("""
          INSERT INTO ht_file_checkpoint(id,owner,source_type,source_scope,source_key,route_code,original_name,payload_ref,
          content_sha256,file_size,content_format,config_snapshot,status,created_at,updated_at,retry_audit)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'READY',?,?,'[]')
          """,f.id(),f.owner(),f.sourceType(),f.sourceScope(),f.sourceKey(),f.routeCode(),f.name(),f.path(),f.sha256(),f.size(),f.format(),f.snapshot(),f.createdAt(),f.createdAt());
    }
    @Transactional
    public Optional<FileRow> claimFile(long leaseSeconds,int maxAttempts) {
        long time=now();
        List<String> ids=page("SELECT id FROM ht_file_checkpoint WHERE status IN ('READY','RETRY_WAIT') AND next_attempt_at<=? AND attempts<? ORDER BY created_at,id",(r,i)->r.getString("id"),16,0,time,maxAttempts);
        for(String id:ids) {
            String token=UUID.randomUUID().toString();
            int n=jdbc.update("UPDATE ht_file_checkpoint SET status='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=?,updated_at=? WHERE id=? AND status IN ('READY','RETRY_WAIT') AND next_attempt_at<=? AND attempts<?",token,time+leaseSeconds*1000,time,id,time,maxAttempts);
            if(n==1){FileRow f=file(id).orElseThrow();ledger.append(ExchangeLog.protocol(f.sourceType()),"IN","FILE_PARSE","START","RUNNING",ledger.fileContext(f));return Optional.of(f);}
        }
        return Optional.empty();
    }
    public boolean renewFile(FileRow f,long seconds) {
        long time=now();
        return jdbc.update("UPDATE ht_file_checkpoint SET lease_until=?,updated_at=? WHERE id=? AND status='PROCESSING' AND lease_token=? AND lease_until>?",time+seconds*1000,time,f.id(),f.leaseToken(),time)==1;
    }
    /** Must run inside the same transaction as row-message inserts and checkpoint update. */
    public void lockFile(FileRow f) {
        long time=now();
        List<String> ids=jdbc.queryForList("SELECT id FROM ht_file_checkpoint WHERE id=? AND status='PROCESSING' AND lease_token=? AND lease_until>? FOR UPDATE",String.class,f.id(),f.leaseToken(),time);
        if(ids.isEmpty()) throw new IllegalStateException("文件租约已失效");
    }
    public void advanceFile(FileRow f,long recordNo) {
        if(jdbc.update("UPDATE ht_file_checkpoint SET last_record_no=?,updated_at=? WHERE id=? AND status='PROCESSING' AND lease_token=?",recordNo,now(),f.id(),f.leaseToken())!=1)
            throw new IllegalStateException("文件检查点更新失败");
    }
    @Transactional
    public void finishFile(FileRow f,long total) {
        Long expected=jdbc.queryForObject("SELECT expected_records FROM ht_file_checkpoint WHERE id=?",Long.class,f.id());
        if(expected!=null && !f.format().equals("BINARY") && expected!=total)throw new RelayException(422,"FILE_RECORD_COUNT_MISMATCH","实际记录数与完成清单不一致");
        long time=now();
        if(jdbc.update("UPDATE ht_file_checkpoint SET status='INGESTED',last_record_no=?,total_records=?,eof_reached=1,lease_token=NULL,lease_until=0,updated_at=? WHERE id=? AND status='PROCESSING' AND lease_token=? AND lease_until>?",total,total,time,f.id(),f.leaseToken(),time)!=1)
            throw new IllegalStateException("文件完成状态更新失败");
        Map<String,Object> c=ledger.fileContext(f);c.put("recordCount",f.format().equals("BINARY")?null:total);
        ledger.append(ExchangeLog.protocol(f.sourceType()),"IN","FILE_PARSE","FINISH","INGESTED",c);
    }
    @Transactional
    public void failFile(FileRow f,String code,boolean permanent,int maxAttempts) {
        long time=now();String state=permanent || f.attempts()>=maxAttempts?"FAILED":"RETRY_WAIT";
        int count=jdbc.update("UPDATE ht_file_checkpoint SET status=?,last_error_code=?,lease_token=NULL,lease_until=0,next_attempt_at=?,updated_at=? WHERE id=? AND status='PROCESSING' AND lease_token=? AND lease_until>?",state,code,time+Rules.backoffMs(f.attempts(),Math.random()),time,f.id(),f.leaseToken(),time);
        if(count==1){Map<String,Object> c=ledger.fileContext(f);c.put("errorCode",code);ledger.append(ExchangeLog.protocol(f.sourceType()),"IN","FILE_PARSE","FINISH","FAILED",c);}
    }
    @Transactional
    public void recoverFiles(int maxAttempts) {
        long time=now();for(FileRow f:page("SELECT * FROM ht_file_checkpoint WHERE status='PROCESSING' AND lease_until<=? ORDER BY lease_until,id",FILE,200,0,time)){
            int count=jdbc.update("UPDATE ht_file_checkpoint SET status=CASE WHEN attempts>=? THEN 'FAILED' ELSE 'RETRY_WAIT' END,last_error_code='LEASE_EXPIRED',lease_token=NULL,lease_until=0,next_attempt_at=?,updated_at=? WHERE id=? AND status='PROCESSING' AND lease_token=? AND lease_until<=?",maxAttempts,time,time,f.id(),f.leaseToken(),time);
            if(count==1){Map<String,Object> c=ledger.fileContext(f);c.put("errorCode","LEASE_EXPIRED");ledger.append(ExchangeLog.protocol(f.sourceType()),"IN","FILE_RECOVERY","FINISH","UNKNOWN",c);}
        }
    }
    public Map<String,Long> fileStats(String id) {
        long messages=jdbc.queryForObject("SELECT COUNT(*) FROM ht_message WHERE file_id=?",Long.class,id);
        long bad=jdbc.queryForObject("SELECT COUNT(*) FROM ht_message WHERE file_id=? AND status='QUARANTINED'",Long.class,id);
        Map<String,Long> stats=new TreeMap<>();stats.put("recordsAccepted",messages);stats.put("quarantined",bad);
        jdbc.query("SELECT d.status,COUNT(*) AS n FROM ht_delivery d JOIN ht_message m ON d.message_id=m.id WHERE m.file_id=? GROUP BY d.status",(RowCallbackHandler)r->stats.put(r.getString("status"),r.getLong("n")),id);
        return stats;
    }
    /** Aggregate statistics for one already-authorized page, without a query per file. */
    public Map<String,Map<String,Long>> fileStatsBatch(List<String> fileIds) {
        if(fileIds.isEmpty()) return Map.of();
        if(fileIds.size()>200) throw new IllegalArgumentException("文件列表批量统计最多 200 项");
        Map<String,Map<String,Long>> result=new HashMap<>();
        for(String id:fileIds) {
            Map<String,Long> counts=new TreeMap<>();counts.put("recordsAccepted",0L);counts.put("quarantined",0L);
            result.put(id,counts);
        }
        String placeholders=String.join(",",Collections.nCopies(fileIds.size(),"?"));
        Object[] args=fileIds.toArray();
        jdbc.query("SELECT file_id,COUNT(*) AS total_count,SUM(CASE WHEN status='QUARANTINED' THEN 1 ELSE 0 END) AS bad_count FROM ht_message WHERE file_id IN ("+placeholders+") GROUP BY file_id",
            (RowCallbackHandler)r->{
                Map<String,Long> counts=result.get(r.getString("file_id"));
                if(counts!=null) {counts.put("recordsAccepted",r.getLong("total_count"));counts.put("quarantined",r.getLong("bad_count"));}
            },args);
        jdbc.query("SELECT m.file_id,d.status,COUNT(*) AS n FROM ht_delivery d JOIN ht_message m ON d.message_id=m.id WHERE m.file_id IN ("+placeholders+") GROUP BY m.file_id,d.status",
            (RowCallbackHandler)r->{
                Map<String,Long> counts=result.get(r.getString("file_id"));
                if(counts!=null) counts.put(r.getString("status"),r.getLong("n"));
            },args);
        return result;
    }
    public List<FileRow> listFiles(int limit,int offset) { return page("SELECT * FROM ht_file_checkpoint ORDER BY created_at DESC,id",FILE,limit,offset); }
    public List<Message> quarantined(int limit,int offset) { return page("SELECT * FROM ht_message WHERE status='QUARANTINED' ORDER BY created_at DESC,id",MESSAGE,limit,offset); }
    /** Successful payload only; deduplication identities, file checkpoints and failures are never deleted. */
    public int purgeSuccessfulPayloads(long before) {
        return jdbc.update("""
          UPDATE ht_message SET payload=NULL,output_payload=NULL,payload_purged_at=? WHERE created_at<? AND payload_purged_at IS NULL
          AND file_id IS NULL AND status='ACCEPTED'
          AND EXISTS (SELECT 1 FROM ht_delivery d WHERE d.message_id=ht_message.id)
          AND NOT EXISTS (SELECT 1 FROM ht_delivery d WHERE d.message_id=ht_message.id AND d.status<>'SUCCEEDED')
          """,now(),before);
    }
}

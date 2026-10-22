package com.haitong.store;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.util.*;

/** Append-only transport ledger. Failures propagate in business transactions: acceptance cannot outlive its log. */
@Component
public class ExchangeLog {
    private final JdbcTemplate jdbc;private final DatabaseDialect dialect;private final Json json;
    public ExchangeLog(JdbcTemplate jdbc,DatabaseDialect dialect,Json json){this.jdbc=jdbc;this.dialect=dialect;this.json=json;}
    public long now(){return Objects.requireNonNull(jdbc.queryForObject(dialect.clockSql(),Timestamp.class)).getTime();}
    public static String protocol(String type){return type.startsWith("KAFKA")?"KAFKA":type.contains("FTP")?"FTP":type.equals("LOCAL")?"LOCAL":type.equals("LOG")?"LOG":"HTTP";}
    public Map<String,Object> messageContext(Message m){
        Map<String,Object> c=new LinkedHashMap<>();c.put("messageId",m.id());c.put("owner",m.owner());c.put("routeCode",m.routeCode());
        c.put("fileId",m.fileId());c.put("sha256",m.sha256());c.put("recordCount",m.fileRef()==null?1:null);
        c.put("payloadBytes",m.payload()==null?null:(long)Base64.getDecoder().decode(m.payload()).length);
        c.put("sourceRef",m.sourceScope()+"/"+m.sourceKey());
        jdbc.query("SELECT business_key,batch_key,payload_bytes FROM ht_message WHERE id=?",(RowCallbackHandler)row->{
            c.put("businessKey",row.getString("business_key"));c.put("batchKey",row.getString("batch_key"));
            if(row.getObject("payload_bytes")!=null)c.put("payloadBytes",row.getLong("payload_bytes"));
        },m.id());return c;
    }
    public Map<String,Object> deliveryContext(Message m,Delivery d){
        Map<String,Object> c=messageContext(m);c.put("deliveryId",d.id());c.put("targetCode",d.targetCode());c.put("attemptId",d.leaseToken());
        if(m.outputPayload()!=null){byte[] out=Base64.getDecoder().decode(m.outputPayload());c.put("sha256",Rules.sha256(out));c.put("payloadBytes",(long)out.length);}
        TargetPlan t=json.read(d.plan(),TargetPlan.class);
        c.put("destinationRef",switch(t.kind()){case KAFKA,FILE_KAFKA->t.topic();case FTP,LOCAL,FILE_FTP,FILE_LOCAL->t.directory();case PLATFORM_FTP->"platform:ODS/FJGHCC";case LOG->"application-log";case ACTION->t.action().code();});
        c.put("details",Map.of("attemptNumber",d.attempts(),"retrySafe",d.safe()));return c;
    }
    public Map<String,Object> fileContext(FileRow f){
        Map<String,Object> c=new LinkedHashMap<>();c.put("fileId",f.id());c.put("owner",f.owner());c.put("routeCode",f.routeCode());
        c.put("sourceRef",f.sourceScope()+"/"+f.sourceKey());c.put("sha256",f.sha256());c.put("payloadBytes",f.size());c.put("attemptId",f.leaseToken());
        c.put("details",Map.of("fileName",f.name(),"format",f.format()));return c;
    }
    public void append(String protocol,String direction,String event,String phase,String outcome,Map<String,Object> c){
        long time=now();Object details=c.getOrDefault("details",Map.of());
        jdbc.update("""
          INSERT INTO ht_exchange_log(id,protocol,direction,event_type,phase,outcome,attempt_id,request_id,owner,route_code,
            message_id,delivery_id,file_id,target_code,source_ref,destination_ref,business_key,batch_key,
            payload_sha256,payload_bytes,record_count,http_status,started_at,duration_ms,error_code,metadata,created_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,UUID.randomUUID().toString(),shortText(protocol,16),shortText(direction,16),shortText(event,64),shortText(phase,16),shortText(outcome,30),
          text(c,"attemptId",64),text(c,"requestId",64),text(c,"owner",100),text(c,"routeCode",100),text(c,"messageId",36),text(c,"deliveryId",36),
          text(c,"fileId",36),text(c,"targetCode",100),text(c,"sourceRef",1000),text(c,"destinationRef",1000),text(c,"businessKey",200),text(c,"batchKey",200),
          text(c,"sha256",64),c.get("payloadBytes"),c.get("recordCount"),c.get("httpStatus"),c.get("startedAt"),c.get("durationMs"),text(c,"errorCode",80),json.write(details),time);
    }
    public List<Map<String,Object>> query(long from,long to,String protocol,String messageId,int limit,int offset){
        String sql="SELECT * FROM ht_exchange_log WHERE created_at>=? AND created_at<?";
        List<Object> args=new ArrayList<>(List.of(from,to));
        if(protocol!=null&&!protocol.isBlank()){sql+=" AND protocol=?";args.add(protocol);}
        if(messageId!=null&&!messageId.isBlank()){sql+=" AND message_id=?";args.add(messageId);}
        sql+=" ORDER BY created_at DESC,id";
        return jdbc.query(dialect.pageSql(sql),(r,i)->{
            Map<String,Object> row=new LinkedHashMap<>();var meta=r.getMetaData();
            for(int col=1;col<=meta.getColumnCount();col++) {
                String label=meta.getColumnLabel(col).toLowerCase(Locale.ROOT);if(label.equals("ht_rn"))continue;
                Object value=r.getObject(col);if(value instanceof java.sql.Clob)value=r.getString(col);row.put(label,value);
            }return row;
        },dialect.pageArgs(limit,offset,args.toArray()));
    }
    public static String text(Map<String,Object> c,String key,int max){Object value=c.get(key);return value==null?null:shortText(value.toString(),max);}
    private static String shortText(String s,int max){String value=s.replaceAll("[\\p{Cntrl}]"," ");return value.length()<=max?value:value.substring(0,max);}
}

package com.haitong.files;

import com.haitong.config.Secrets;
import com.haitong.core.*;
import com.haitong.store.*;
import java.util.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

/** All predicates include grants resolved from server configuration, never a caller-supplied tenant. */
@Repository
@ConditionalOnProperty(prefix="relay.files",name="enabled",havingValue="true")
public class FileUploadRepository implements InitializingBean {
    public record Upload(Map<String,Object> row){
        public Upload {Map<String,Object> copy=new LinkedHashMap<>();row.forEach((k,v)->copy.put(k.toLowerCase(Locale.ROOT),v));row=copy;}
        public String text(String key){return Objects.toString(row.get(key),"");}
        public long number(String key){Object v=row.get(key);return v==null?0:((Number)v).longValue();}
        public String id(){return text("upload_id");} public String tenant(){return text("tenant_id");}
        public String owner(){return text("owner");} public String state(){return text("state");}
        public long size(){return number("size_bytes");}
    }
    private final JdbcTemplate jdbc;private final DatabaseDialect dialect;private final RelayRepository relay;
    private final FileContractCatalog catalog;private final FileTransferProperties p;
    public FileUploadRepository(JdbcTemplate jdbc,DatabaseDialect dialect,RelayRepository relay,FileContractCatalog catalog,FileTransferProperties p){
        this.jdbc=jdbc;this.dialect=dialect;this.relay=relay;this.catalog=catalog;this.p=p;
    }
    public JdbcTemplate jdbc(){return jdbc;} public long now(){return relay.now();}
    private static Optional<Upload> first(List<Map<String,Object>> rows){return rows.isEmpty()?Optional.empty():Optional.of(new Upload(rows.getFirst()));}
    public Optional<Upload> raw(String id,boolean lock){return first(jdbc.queryForList("SELECT * FROM ht_file_upload WHERE upload_id=?"+(lock?" FOR UPDATE":""),id));}
    private String grants(Secrets.Identity caller,List<Object> args,String alias){
        List<String> terms=new ArrayList<>();
        for(var scope:catalog.scopes(caller)){
            var c=scope.contract();
            terms.add("("+alias+"tenant_id=? AND "+alias+"datasource_id=? AND "+alias+"collection_id=? AND ("+alias+"owner=?"+(scope.readAll()?" OR "+alias+"published_at IS NOT NULL":"")+"))");
            args.addAll(List.of(c.tenantId(),c.datasourceId(),c.collectionId(),caller.id()));
        }
        return terms.isEmpty()?"1=0":"("+String.join(" OR ",terms)+")";
    }
    public List<Upload> authorized(Secrets.Identity caller,List<String> ids){
        if(ids.isEmpty())return List.of();if(ids.size()>100)throw RelayException.invalid("一次最多查询100个上传会话");
        List<Object> args=new ArrayList<>(ids);String where=grants(caller,args,"");
        return jdbc.queryForList("SELECT * FROM ht_file_upload WHERE upload_id IN ("+marks(ids.size())+") AND "+where,args.toArray()).stream().map(Upload::new).toList();
    }
    public Optional<Upload> byMessage(Secrets.Identity caller,String message){
        List<Object> args=new ArrayList<>(List.of(message));return first(jdbc.queryForList("SELECT * FROM ht_file_upload WHERE message_id=? AND "+grants(caller,args,""),args.toArray()));
    }
    public Map<String,Object> page(Secrets.Identity caller,int size,int offset){
        List<Object> args=new ArrayList<>();String where=grants(caller,args,"");
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM ht_file_upload WHERE "+where,Long.class,args.toArray()));
        List<Upload> rows=jdbc.queryForList(dialect.pageSql("SELECT * FROM ht_file_upload WHERE "+where+" ORDER BY created_at DESC,upload_id"),dialect.pageArgs(size,offset,args.toArray())).stream().map(Upload::new).toList();
        return Map.of("total",total,"rows",rows);
    }
    public Optional<Upload> existing(FileContractCatalog.Contract c,String owner,String key){
        return first(jdbc.queryForList("SELECT * FROM ht_file_upload WHERE tenant_id=? AND owner=? AND datasource_id=? AND collection_id=? AND init_key=?",c.tenantId(),owner,c.datasourceId(),c.collectionId(),key));
    }
    public Optional<Upload> existingSubmission(FileContractCatalog.Contract c,String owner,String key){
        return first(jdbc.queryForList("SELECT * FROM ht_file_upload WHERE tenant_id=? AND owner=? AND datasource_id=? AND collection_id=? AND submission_key=?",c.tenantId(),owner,c.datasourceId(),c.collectionId(),key));
    }
    public List<Map<String,Object>> parts(String id){return jdbc.queryForList("SELECT * FROM ht_file_upload_part WHERE upload_id=? ORDER BY part_no",id);}
    public Optional<Map<String,Object>> part(String id,int no){var rows=jdbc.queryForList("SELECT * FROM ht_file_upload_part WHERE upload_id=? AND part_no=?",id,no);return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());}
    public List<Map<String,Object>> partPage(String id,int after,int limit){
        return jdbc.queryForList(dialect.pageSql("SELECT part_no,size_bytes,sha256 FROM ht_file_upload_part WHERE upload_id=? AND state='COMPLETE' AND part_no>? ORDER BY part_no"),dialect.pageArgs(limit,0,id,after)).stream().map(r->Map.<String,Object>of("partNumber",r.get("part_no"),"size",r.get("size_bytes"),"sha256",r.get("sha256"))).toList();
    }
    public Map<String,Map<String,Object>> progress(List<Upload> rows){
        if(rows.isEmpty())return Map.of();List<String> ids=rows.stream().map(Upload::id).toList();
        Map<String,Map<String,Object>> result=new HashMap<>();
        for(var r:jdbc.queryForList("SELECT upload_id,COUNT(*) AS completed_parts,SUM(size_bytes) AS confirmed_bytes FROM ht_file_upload_part WHERE upload_id IN ("+marks(ids.size())+") AND state='COMPLETE' GROUP BY upload_id",ids.toArray()))
            result.put(r.get("upload_id").toString(),Map.of("completedParts",r.get("completed_parts"),"confirmedBytes",r.get("confirmed_bytes")));
        return result;
    }
    public Map<String,List<Map<String,Object>>> deliveries(List<Upload> rows){
        if(rows.isEmpty())return Map.of();List<String> ids=rows.stream().map(Upload::id).toList();
        String sql="SELECT u.upload_id,d.id,d.target_code,d.status,d.attempts,d.last_error_code,r.business_status,r.business_ref FROM ht_file_upload u JOIN ht_delivery d ON d.message_id=u.message_id LEFT JOIN ht_file_receipt r ON r.delivery_id=d.id AND r.tenant_id=u.tenant_id WHERE u.upload_id IN ("+marks(ids.size())+") ORDER BY d.target_code";
        Map<String,List<Map<String,Object>>> result=new HashMap<>();
        for(var r:jdbc.queryForList(sql,ids.toArray())){
            Map<String,Object> item=new LinkedHashMap<>();item.put("deliveryId",r.get("id"));item.put("targetCode",r.get("target_code"));item.put("status",r.get("status"));item.put("attempts",r.get("attempts"));item.put("errorCode",r.get("last_error_code"));item.put("businessStatus",r.get("business_status"));item.put("businessRef",r.get("business_ref"));
            result.computeIfAbsent(r.get("upload_id").toString(),ignored->new ArrayList<>()).add(item);
        }
        return result;
    }
    public Optional<Upload> claim(){
        long now=now();String sql="SELECT * FROM ht_file_upload WHERE ((state='FINALIZING' OR (state='READY' AND ((submission_hash IS NOT NULL AND published_at IS NULL) OR quota_state='RESERVED')) OR (state IN ('ABORTED','EXPIRED','REJECTED') AND quota_state<>'RELEASED') OR (state IN ('CREATED','UPLOADING') AND expires_at<=?)) AND lease_until<? AND next_attempt_at<=?) ORDER BY created_at,upload_id";
        var selected=first(jdbc.queryForList(dialect.pageSql(sql),dialect.pageArgs(1,0,now,now,now)));
        if(selected.isEmpty())return selected;Upload u=selected.get();String token=UUID.randomUUID().toString();
        int n=jdbc.update("UPDATE ht_file_upload SET lease_token=?,lease_until=?,attempts=attempts+1,updated_at=? WHERE upload_id=? AND lease_until<?",token,now+p.getLeaseSeconds()*1000L,now,u.id(),now);
        if(n!=1)return Optional.empty();return raw(u.id(),false);
    }
    public void reserve(FileContractCatalog.Contract c,long size,long now){
        long amount=Math.multiplyExact(size,2);
        if(jdbc.update("UPDATE ht_storage_quota SET reserved_bytes=reserved_bytes+?,active_sessions=active_sessions+1,updated_at=? WHERE quota_key='GLOBAL' AND reserved_bytes+committed_bytes+?<=limit_bytes AND active_sessions<?",amount,now,amount,p.getMaxActiveUploads())!=1)throw RelayException.capacity();
        if(jdbc.update("UPDATE ht_storage_quota SET reserved_bytes=reserved_bytes+?,active_sessions=active_sessions+1,updated_at=? WHERE quota_key=? AND reserved_bytes+committed_bytes+?<=limit_bytes AND active_sessions<?",amount,now,catalog.quotaKey(c),amount,c.policy().getMaxActiveUploads())!=1)throw RelayException.capacity();
    }
    public void quotaTransition(Upload u,boolean commit,long now){
        String old=u.text("quota_state");if(old.equals("RELEASED")||commit&&old.equals("COMMITTED"))return;
        long reserved=old.equals("RESERVED")?u.size()*2:0, committedDelta=commit?u.size():old.equals("COMMITTED")?-u.size():0;
        String key=Rules.sha256((u.tenant()+"|"+u.text("datasource_id")+"|"+u.text("collection_id")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(String scope:List.of("GLOBAL",key)){
            int n=jdbc.update("UPDATE ht_storage_quota SET reserved_bytes=reserved_bytes-?,committed_bytes=committed_bytes+?,active_sessions=active_sessions-?,updated_at=? WHERE quota_key=? AND reserved_bytes>=? AND committed_bytes+?>=0 AND active_sessions>=?",reserved,committedDelta,old.equals("RESERVED")?1:0,now,scope,reserved,committedDelta,old.equals("RESERVED")?1:0);
            if(n!=1)throw new IllegalStateException("文件配额账本不一致");
        }
        jdbc.update("UPDATE ht_file_upload SET quota_state=? WHERE upload_id=?",commit?"COMMITTED":"RELEASED",u.id());
    }
    @Override public void afterPropertiesSet(){
        if(dialect==DatabaseDialect.DM)throw new IllegalStateException("文件分片功能当前只启用MySQL/H2，达梦结构需单独验证后启用");
        for(String table:List.of("ht_file_upload","ht_file_upload_part","ht_storage_quota","ht_file_receipt"))jdbc.queryForList("SELECT * FROM "+table+" WHERE 1=0");
        Map<String,Object[]> desired=new LinkedHashMap<>();long now=now();desired.put("GLOBAL",new Object[]{"GLOBAL",null,p.getStorageLimitBytes(),now});
        for(var c:catalog.all())desired.put(catalog.quotaKey(c),new Object[]{catalog.quotaKey(c),c.tenantId(),c.policy().getQuotaBytes(),now});
        Set<String> existing=new HashSet<>(jdbc.queryForList("SELECT quota_key FROM ht_storage_quota",String.class));
        List<Object[]> inserts=new ArrayList<>(),updates=new ArrayList<>();
        for(var e:desired.entrySet()){
            Object[] v=e.getValue();if(!existing.contains(e.getKey()))inserts.add(v);
            else updates.add(new Object[]{v[2],now,e.getKey()});
        }
        if(!inserts.isEmpty())try{jdbc.batchUpdate("INSERT INTO ht_storage_quota(quota_key,tenant_id,limit_bytes,updated_at) VALUES (?,?,?,?)",inserts);}catch(org.springframework.dao.DuplicateKeyException ignored){/* Concurrent startup can seed the same scope; no counters are reset. */}
        if(!updates.isEmpty())jdbc.batchUpdate("UPDATE ht_storage_quota SET limit_bytes=?,updated_at=? WHERE quota_key=?",updates);
    }
    public static String marks(int count){return String.join(",",Collections.nCopies(count,"?"));}
}

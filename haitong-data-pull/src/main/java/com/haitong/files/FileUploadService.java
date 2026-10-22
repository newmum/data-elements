package com.haitong.files;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import com.haitong.files.FileUploadRepository.Upload;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix="relay.files",name="enabled",havingValue="true")
public class FileUploadService {
    public record Init(String datasourceId,String collectionId,String fileName,long size,String sha256) {}
    public record Submission(String businessKey,Map<String,Object> metadata) {}
    public record Receipt(String deliveryId,String sha256,String status,String businessRef) {}
    private record PartClaim(Upload upload,String token,long size,Map<String,Object> previous) {}
    private final FileUploadRepository repo;private final FileContractCatalog catalog;private final FileTransferProperties p;
    private final FileStorage storage;private final TransactionTemplate tx;private final Json json;private final IngressService ingress;private final RelayRepository relay;
    public FileUploadService(FileUploadRepository repo,FileContractCatalog catalog,FileTransferProperties p,FileStorage storage,TransactionTemplate tx,Json json,IngressService ingress,RelayRepository relay){
        this.repo=repo;this.catalog=catalog;this.p=p;this.storage=storage;this.tx=tx;this.json=json;this.ingress=ingress;this.relay=relay;
    }
    public Map<String,Object> initialize(Secrets.Identity caller,String key,Init input) throws IOException {
        Rules.key(key);var c=catalog.upload(caller,input.datasourceId(),input.collectionId());String name=Rules.fileName(input.fileName());
        String extension=Rules.extension(name).replaceFirst("^\\.","").toLowerCase(Locale.ROOT);
        if(!c.policy().getAllowedExtensions().contains(extension))throw RelayException.invalid("文件格式不在集合白名单内");
        if(input.size()<1||input.size()>c.policy().getMaxFileBytes())throw new RelayException(413,"FILE_TOO_LARGE","文件大小超出集合约定");
        String hash=optionalHash(input.sha256());long count=(input.size()+p.getPartSizeBytes()-1)/p.getPartSizeBytes();
        if(count>10000)throw RelayException.invalid("分片总数超过10000，请调整服务端分片大小");
        String identityHash=Rules.sha256(json.bytes(List.of(name,input.size(),hash,c.tenantId(),c.datasourceId(),c.collectionId())));
        var old=repo.existing(c,caller.id(),key);if(old.isPresent())return duplicateInit(old.get(),identityHash);
        storage.freeSpace();String id=UUID.randomUUID().toString();long now=repo.now();
        try{
            tx.executeWithoutResult(status->{repo.reserve(c,input.size(),now);
                repo.jdbc().update("""
                  INSERT INTO ht_file_upload(upload_id,tenant_id,owner,datasource_id,collection_id,contract_version,contract_hash,contract_json,
                    init_key,init_hash,file_name,media_type,size_bytes,expected_sha256,part_size,part_count,state,security_state,quota_state,created_at,updated_at,expires_at)
                  VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'CREATED','PENDING','RESERVED',?,?,?)
                  """,id,c.tenantId(),caller.id(),c.datasourceId(),c.collectionId(),c.version(),c.hash(),json.write(c),key,identityHash,name,media(extension),input.size(),hash.isEmpty()?null:hash,p.getPartSizeBytes(),count,now,now,now+p.getSessionHours()*3600000L);
            });
        }catch(DataIntegrityViolationException e){return duplicateInit(repo.existing(c,caller.id(),key).orElseThrow(()->e),identityHash);}
        return basic(repo.raw(id,false).orElseThrow());
    }
    private Map<String,Object> duplicateInit(Upload u,String hash){if(!u.text("init_hash").equals(hash))throw RelayException.conflict();Map<String,Object> out=basic(u);out.put("duplicate",true);return out;}
    public Map<String,Object> putPart(Secrets.Identity caller,String id,int no,String checksum,long contentLength,InputStream input) throws IOException {
        String expectedHash=requiredHash(checksum);storage.acquire();PartClaim claim=null;FileStorage.Saved saved=null;boolean committed=false;
        try{
            storage.freeSpace();claim=Objects.requireNonNull(tx.execute(status->{Upload u=owned(caller,id,true);long now=repo.now();
                writable(u,now);if(no<1||no>u.number("part_count"))throw RelayException.invalid("分片序号从1开始且不能超过总数");
                long expected=Math.min(u.number("part_size"),u.size()-(no-1)*u.number("part_size"));
                if(contentLength>=0&&contentLength!=expected)throw RelayException.invalid("Content-Length 与分片长度不一致");
                var old=repo.part(id,no).orElse(null);if(old!=null&&old.get("state").equals("WRITING")&&((Number)old.get("lease_until")).longValue()>now)throw busy();
                if(old!=null&&old.get("sha256")!=null&&!expectedHash.equals(old.get("sha256")))throw RelayException.conflict();
                String token=UUID.randomUUID().toString();
                if(old==null)repo.jdbc().update("INSERT INTO ht_file_upload_part(upload_id,tenant_id,part_no,state,attempt_token,lease_until,updated_at) VALUES (?,?,?,'WRITING',?,?,?)",id,u.tenant(),no,token,now+p.getLeaseSeconds()*1000L,now);
                else repo.jdbc().update("UPDATE ht_file_upload_part SET state='WRITING',attempt_token=?,lease_until=?,updated_at=? WHERE upload_id=? AND part_no=?",token,now+p.getLeaseSeconds()*1000L,now,id,no);
                repo.jdbc().update("UPDATE ht_file_upload SET state='UPLOADING',updated_at=? WHERE upload_id=?",now,id);
                return new PartClaim(u,token,expected,old);
            }));
            PartClaim current=claim;saved=storage.save(id,no,claim.token(),input,claim.size(),()->renewPart(current,no));
            if(!saved.sha256().equals(expectedHash))throw new RelayException(422,"PART_CHECKSUM_MISMATCH","分片 SHA-256 不匹配");
            FileStorage.Saved stored=saved;
            tx.executeWithoutResult(status->{Upload u=owned(caller,id,true);writable(u,repo.now());
                if(repo.jdbc().update("UPDATE ht_file_upload_part SET size_bytes=?,sha256=?,payload_path=?,state='COMPLETE',lease_until=0,updated_at=? WHERE upload_id=? AND part_no=? AND attempt_token=? AND state='WRITING'",stored.size(),stored.sha256(),stored.path().toString(),repo.now(),id,no,current.token())!=1)throw busy();
            });
            committed=true;
            if(claim.previous()!=null&&claim.previous().get("payload_path")!=null)try{storage.delete(claim.previous().get("payload_path").toString());}catch(IOException ignored){/* Finalization removes stale private part files. */}
            return Map.of("uploadId",id,"partNumber",no,"size",saved.size(),"sha256",saved.sha256(),"status","COMPLETE");
        }catch(Exception e){
            // A connection failure during COMMIT can hide a successful database commit.
            // Re-lock in a fresh transaction before deleting any potentially referenced file.
            if(!committed&&claim!=null){
                PartClaim current=claim;FileStorage.Saved attempted=saved;
                try{
                    boolean referenced=Boolean.TRUE.equals(tx.execute(status->{repo.raw(id,true).orElseThrow();var row=repo.part(id,no).orElse(null);
                        if(attempted!=null&&row!=null&&attempted.path().toString().equals(row.get("payload_path")))return true;
                        restorePart(current,no);return false;
                    }));
                    if(!referenced&&saved!=null)storage.delete(saved.path().toString());
                }catch(Exception ignored){/* Keep the private object and quota until the database outcome can be established. */}
            }
            throw e;
        }finally{storage.release();}
    }
    private void restorePart(PartClaim c,int no){
        var old=c.previous();if(old!=null&&old.get("sha256")!=null)
            repo.jdbc().update("UPDATE ht_file_upload_part SET state='COMPLETE',lease_until=0 WHERE upload_id=? AND part_no=? AND attempt_token=?",c.upload().id(),no,c.token());
        else repo.jdbc().update("DELETE FROM ht_file_upload_part WHERE upload_id=? AND part_no=? AND attempt_token=?",c.upload().id(),no,c.token());
    }
    private void renewPart(PartClaim c,int no){long now=repo.now();if(repo.jdbc().update("UPDATE ht_file_upload_part SET lease_until=?,updated_at=? WHERE upload_id=? AND part_no=? AND attempt_token=? AND state='WRITING' AND EXISTS (SELECT 1 FROM ht_file_upload u WHERE u.upload_id=? AND u.state='UPLOADING' AND u.expires_at>?)",now+p.getLeaseSeconds()*1000L,now,c.upload().id(),no,c.token(),c.upload().id(),now)!=1)throw busy();}
    public Map<String,Object> complete(Secrets.Identity caller,String id){
        return Objects.requireNonNull(tx.execute(status->{Upload u=owned(caller,id,true);if(Set.of("FINALIZING","READY").contains(u.state()))return basic(u);writable(u,repo.now());
            var progress=repo.jdbc().queryForMap("SELECT COUNT(*) AS n,SUM(size_bytes) AS bytes FROM ht_file_upload_part WHERE upload_id=? AND state='COMPLETE'",id);
            if(((Number)progress.get("n")).longValue()!=u.number("part_count")||progress.get("bytes")==null||((Number)progress.get("bytes")).longValue()!=u.size())throw new RelayException(409,"PARTS_INCOMPLETE","请先补齐所有分片后再完成上传");
            repo.jdbc().update("UPDATE ht_file_upload SET state='FINALIZING',updated_at=?,next_attempt_at=0 WHERE upload_id=?",repo.now(),id);Map<String,Object> result=basic(u);result.put("state","FINALIZING");return result;
        }));
    }
    public Map<String,Object> submit(Secrets.Identity caller,String id,String key,Submission input){
        Rules.key(key);Rules.key(input.businessKey());
        try{return Objects.requireNonNull(tx.execute(status->{Upload u=owned(caller,id,true);var c=snapshot(u);Map<String,Object> metadata=catalog.metadata(c,input.metadata());
            String payload=json.write(Map.of("uploadId",id,"businessKey",input.businessKey(),"metadata",metadata));String hash=Rules.sha256(json.bytes(List.of(id,input.businessKey(),metadata)));
            if(!u.text("submission_hash").isEmpty()){
                if(!u.text("submission_key").equals(key)||!u.text("submission_hash").equals(hash))throw RelayException.conflict();return submitted(u);
            }
            if(Set.of("ABORTED","EXPIRED","REJECTED").contains(u.state()))throw busy();
            var same=repo.existingSubmission(c,caller.id(),key);if(same.isPresent())throw RelayException.conflict();
            String message=UUID.randomUUID().toString();repo.jdbc().update("UPDATE ht_file_upload SET submission_key=?,submission_hash=?,submission_json=?,message_id=?,next_attempt_at=0,updated_at=? WHERE upload_id=?",key,hash,payload,message,repo.now(),id);
            return Map.of("uploadId",id,"messageId",message,"status","WAITING_FILE","statusUrl","/api/v1/file-uploads/"+id);
        }));}catch(DataIntegrityViolationException e){throw RelayException.conflict();}
    }
    public Map<String,Object> abort(Secrets.Identity caller,String id){return Objects.requireNonNull(tx.execute(status->{Upload u=owned(caller,id,true);
        if(!u.text("message_id").isEmpty()||u.state().equals("READY")||u.state().equals("FINALIZING"))throw new RelayException(409,"UPLOAD_ALREADY_SUBMITTED","已进入校验或推送阶段，不能取消");
        repo.jdbc().update("UPDATE ht_file_upload SET state='ABORTED',updated_at=?,next_attempt_at=0 WHERE upload_id=?",repo.now(),id);Map<String,Object> out=basic(u);out.put("state","ABORTED");return out;
    }));}
    private Upload owned(Secrets.Identity caller,String id,boolean lock){Upload u=repo.raw(id,lock).orElseThrow(RelayException::notFound);
        var current=catalog.upload(caller,u.text("datasource_id"),u.text("collection_id"));
        if(!u.owner().equals(caller.id())||!u.tenant().equals(current.tenantId()))throw RelayException.notFound();return u;
    }
    public Upload readable(Secrets.Identity caller,String id){return repo.authorized(caller,List.of(id)).stream().findFirst().orElseThrow(RelayException::notFound);}
    public Path content(Secrets.Identity caller,String id) throws IOException {Upload u=readable(caller,id);if(!u.state().equals("READY"))throw busy();return storage.checked(u.text("payload_path"));}
    private void writable(Upload u,long now){if(!Set.of("CREATED","UPLOADING").contains(u.state())||u.number("expires_at")<=now)throw new RelayException(409,"UPLOAD_NOT_WRITABLE","上传已完成、取消或过期");}
    private static RelayException busy(){return new RelayException(409,"FILE_STATE_CONFLICT","文件状态不支持当前操作，请查询状态后重试");}
    private FileContractCatalog.Contract snapshot(Upload u){return json.read(u.text("contract_json"),FileContractCatalog.Contract.class);}
    public Map<String,Object> status(Secrets.Identity caller,String id){return statuses(caller,List.of(id)).getFirst();}
    public Optional<Map<String,Object>> pendingMessage(Secrets.Identity caller,String messageId){
        return repo.byMessage(caller,messageId).map(u->{Map<String,Object> result=basic(u);result.put("status",Set.of("ABORTED","EXPIRED","REJECTED").contains(u.state())?u.state():"WAITING_FILE");return result;});
    }
    public List<Map<String,Object>> statuses(Secrets.Identity caller,List<String> ids){
        if(ids.isEmpty()||ids.size()>100||new HashSet<>(ids).size()!=ids.size())throw RelayException.invalid("请提供1至100个不重复的上传ID");
        var rows=repo.authorized(caller,ids);if(rows.size()!=ids.size())throw RelayException.notFound();return decorate(rows);
    }
    @SuppressWarnings("unchecked") public Map<String,Object> page(Secrets.Identity caller,int limit,int offset){
        if(limit<1||limit>100||offset<0||offset>1000000)throw RelayException.invalid("分页参数超出范围");
        var page=repo.page(caller,limit,offset);return Map.of("total",page.get("total"),"rows",decorate((List<Upload>)page.get("rows")),"limit",limit,"offset",offset);
    }
    private List<Map<String,Object>> decorate(List<Upload> rows){
        var progress=repo.progress(rows);var deliveries=repo.deliveries(rows);List<Map<String,Object>> out=new ArrayList<>();
        for(Upload u:rows){Map<String,Object> item=basic(u);item.putAll(progress.getOrDefault(u.id(),Map.of("completedParts",0L,"confirmedBytes",0L)));item.put("deliveries",deliveries.getOrDefault(u.id(),List.of()));
            var states=deliveries.getOrDefault(u.id(),List.of()).stream().map(d->d.get("status").toString()).toList();
            item.put("deliveryStatus",u.text("published_at").isEmpty()?u.text("message_id").isEmpty()?"NOT_SUBMITTED":"WAITING_FILE":Rules.aggregate(states));out.add(item);}
        return out;
    }
    public Map<String,Object> parts(Secrets.Identity caller,String id,int after,int limit){readable(caller,id);if(after<0||limit<1||limit>100)throw RelayException.invalid("分片分页参数不正确");return Map.of("uploadId",id,"parts",repo.partPage(id,after,limit));}
    private Map<String,Object> basic(Upload u){Map<String,Object> out=new LinkedHashMap<>();
        out.put("uploadId",u.id());out.put("fileId",u.id());out.put("datasourceId",u.text("datasource_id"));out.put("collectionId",u.text("collection_id"));
        out.put("fileName",u.text("file_name"));out.put("size",u.size());out.put("mediaType",u.text("media_type"));out.put("sha256",u.text("final_sha256"));
        out.put("partSize",u.number("part_size"));out.put("partCount",u.number("part_count"));out.put("state",u.state());out.put("securityState",u.text("security_state"));
        out.put("contractVersion",u.number("contract_version"));out.put("messageId",u.text("message_id"));out.put("errorCode",u.text("error_code"));out.put("expiresAt",u.number("expires_at"));
        out.put("statusUrl","/api/v1/file-uploads/"+u.id());if(u.state().equals("READY"))out.put("contentUrl","/api/v1/file-uploads/"+u.id()+"/content");return out;
    }
    private Map<String,Object> submitted(Upload u){Map<String,Object> result=basic(u);result.put("duplicate",true);return result;}
    public boolean runOne(){var claim=repo.claim();if(claim.isEmpty())return false;Upload u=claim.get();
        try{
            if(Set.of("CREATED","UPLOADING","ABORTED","EXPIRED","REJECTED").contains(u.state())){
                long active=Objects.requireNonNull(repo.jdbc().queryForObject("SELECT COUNT(*) FROM ht_file_upload_part WHERE upload_id=? AND state='WRITING' AND lease_until>?",Long.class,u.id(),repo.now()));
                if(active>0){release(u,"ACTIVE_PART",5000);return true;}
                tx.executeWithoutResult(status->{Upload locked=fenced(u);if(Set.of("CREATED","UPLOADING").contains(locked.state()))repo.jdbc().update("UPDATE ht_file_upload SET state='EXPIRED' WHERE upload_id=?",u.id());});
                storage.cleanup(u.id(),"");tx.executeWithoutResult(status->{Upload locked=fenced(u);repo.quotaTransition(locked,false,repo.now());});release(u,u.text("error_code").isEmpty()?null:u.text("error_code"),0);return true;
            }
            return processReady(u);
        }catch(RelayException e){
            if(e.status()==422){repo.jdbc().update("UPDATE ht_file_upload SET state='REJECTED',security_state='REJECTED',error_code=?,lease_until=0,next_attempt_at=0 WHERE upload_id=? AND lease_token=?",e.code(),u.id(),u.text("lease_token"));}
            else release(u,e.code(),10000);
        }catch(Exception e){release(u,"FILE_PROCESSING_FAILED",10000);}
        return true;
    }
    private boolean processReady(Upload claimed) throws Exception {
        Upload u=claimed;
        if(u.state().equals("FINALIZING")){
            storage.freeSpace();var parts=repo.parts(u.id());
            Set<String> keep=new HashSet<>();for(var part:parts)keep.add(part.get("payload_path").toString());storage.cleanupExcept(u.id(),keep);
            FileStorage.Saved saved=storage.assemble(u,parts,()->renew(claimed));String security;
            try{security=storage.scan(saved.path(),snapshot(u).policy().isRequireMalwareScan(),()->renew(claimed));}
            catch(Exception e){storage.delete(saved.path().toString());throw e;}
            int n=repo.jdbc().update("UPDATE ht_file_upload SET state='READY',payload_path=?,final_sha256=?,security_state=?,error_code=NULL,updated_at=? WHERE upload_id=? AND lease_token=? AND state='FINALIZING'",saved.path().toString(),saved.sha256(),security,repo.now(),u.id(),u.text("lease_token"));
            if(n!=1){storage.delete(saved.path().toString());throw busy();}u=repo.raw(u.id(),false).orElseThrow();
        }
        if(u.text("quota_state").equals("RESERVED")){
            renew(u);storage.cleanup(u.id(),u.text("payload_path"));Upload ready=u;
            tx.executeWithoutResult(status->{Upload locked=fenced(ready);repo.quotaTransition(locked,true,repo.now());});
        }
        if(!u.text("submission_hash").isEmpty()&&u.text("published_at").isEmpty())publish(u);
        release(u,null,0);return true;
    }
    private void publish(Upload u){var c=snapshot(u);ingress.capacity(c.plan().targets().size());
        tx.executeWithoutResult(status->{Upload locked=fenced(u);if(!locked.state().equals("READY")||!locked.text("published_at").isEmpty())return;
            var submitted=json.object(locked.text("submission_json").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("fileId",u.id());metadata.put("sourceFileId",u.id());metadata.put("tenantId",u.tenant());
            metadata.put("datasourceId",u.text("datasource_id"));metadata.put("collectionId",u.text("collection_id"));metadata.put("contractVersion",u.number("contract_version"));
            metadata.put("fileName",u.text("file_name"));metadata.put("fileBytes",u.size());metadata.put("sha256",u.text("final_sha256"));metadata.put("mediaType",u.text("media_type"));
            metadata.put("businessKey",submitted.get("businessKey"));metadata.put("metadata",submitted.get("metadata"));metadata.put("contentUrl","/api/v1/file-uploads/"+u.id()+"/content");metadata.put("securityState",u.text("security_state"));
            long now=repo.now();FileRow file=new FileRow(u.id(),u.owner(),"FILE_PUSH","file-push:"+u.tenant(),u.id(),c.plan().code(),u.text("file_name"),u.text("payload_path"),u.text("final_sha256"),u.size(),"BINARY",json.write(c.plan()),"INGESTED",1,1,true,null,0,0,null,now);
            relay.insertFile(file);repo.jdbc().update("UPDATE ht_file_checkpoint SET status='INGESTED',last_record_no=1,total_records=1,eof_reached=1 WHERE id=?",u.id());
            ingress.registeredBinary(file,c.plan(),metadata,u.text("message_id"));repo.jdbc().update("UPDATE ht_message SET business_key=? WHERE id=?",submitted.get("businessKey"),u.text("message_id"));
            repo.jdbc().update("UPDATE ht_file_upload SET published_at=?,updated_at=? WHERE upload_id=?",now,now,u.id());
        });
    }
    private Upload fenced(Upload u){Upload locked=repo.raw(u.id(),true).orElseThrow();if(!locked.text("lease_token").equals(u.text("lease_token")))throw busy();return locked;}
    private void renew(Upload u){long now=repo.now();if(repo.jdbc().update("UPDATE ht_file_upload SET lease_until=? WHERE upload_id=? AND lease_token=?",now+p.getLeaseSeconds()*1000L,u.id(),u.text("lease_token"))!=1)throw busy();}
    private void release(Upload u,String error,long delay){repo.jdbc().update("UPDATE ht_file_upload SET lease_until=0,lease_token=NULL,next_attempt_at=?,error_code=? WHERE upload_id=? AND lease_token=?",repo.now()+delay,error,u.id(),u.text("lease_token"));}
    public Map<String,Object> receipt(Secrets.Identity caller,String id,String key,Receipt input){
        Rules.key(key);String checksum=requiredHash(input.sha256());if(!Set.of("ACCEPTED","REJECTED").contains(input.status()))throw RelayException.invalid("业务回执状态只能为 ACCEPTED 或 REJECTED");
        if(input.businessRef()!=null&&(input.businessRef().length()>200||input.businessRef().chars().anyMatch(Character::isISOControl)))throw RelayException.invalid("业务回执编号过长或含控制字符");
        return Objects.requireNonNull(tx.execute(status->{Upload u=repo.raw(id,true).orElseThrow(RelayException::notFound);var c=snapshot(u);
            var current=catalog.get(u.text("datasource_id"),u.text("collection_id"));
            if(!u.tenant().equals(current.tenantId()))throw RelayException.notFound();
            var allowed=c.policy().getReceiptClients().entrySet().stream().filter(e->e.getValue().equals(caller.id())&&Objects.equals(current.policy().getReceiptClients().get(e.getKey()),caller.id())).map(Map.Entry::getKey).toList();
            if(allowed.isEmpty())throw RelayException.notFound();
            List<Object> args=new ArrayList<>(List.of(input.deliveryId(),u.text("message_id")));args.addAll(allowed);
            var targets=repo.jdbc().queryForList("SELECT target_code,status FROM ht_delivery WHERE id=? AND message_id=? AND target_code IN ("+FileUploadRepository.marks(allowed.size())+")",args.toArray());
            if(targets.isEmpty())throw RelayException.notFound();if(!targets.getFirst().get("status").equals("SUCCEEDED"))throw busy();
            if(!u.text("final_sha256").equals(checksum))throw RelayException.conflict();
            String payload=Rules.sha256(json.bytes(List.of(id,input.deliveryId(),checksum,input.status(),Objects.toString(input.businessRef(),""))));
            var old=repo.jdbc().queryForList("SELECT receipt_id,payload_hash,receipt_key FROM ht_file_receipt WHERE tenant_id=? AND ((sender_id=? AND receipt_key=?) OR delivery_id=?)",u.tenant(),caller.id(),key,input.deliveryId());
            if(!old.isEmpty()){if(old.size()!=1||!old.getFirst().get("payload_hash").equals(payload)||!old.getFirst().get("receipt_key").equals(key))throw RelayException.conflict();return Map.of("receiptId",old.getFirst().get("receipt_id"),"duplicate",true);}
            String receipt=UUID.randomUUID().toString();repo.jdbc().update("INSERT INTO ht_file_receipt(receipt_id,tenant_id,sender_id,receipt_key,payload_hash,upload_id,delivery_id,target_code,sha256,business_status,business_ref,received_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",receipt,u.tenant(),caller.id(),key,payload,id,input.deliveryId(),targets.getFirst().get("target_code"),checksum,input.status(),input.businessRef(),repo.now());
            return Map.of("receiptId",receipt,"duplicate",false);
        }));
    }
    private static String optionalHash(String value){if(value==null||value.isBlank())return "";return requiredHash(value);}
    private static String requiredHash(String value){if(value==null||!value.matches("[a-fA-F0-9]{64}"))throw RelayException.invalid("SHA-256 必须为64位十六进制字符串");return value.toLowerCase(Locale.ROOT);}
    private static String media(String ext){return switch(ext){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "pdf"->"application/pdf";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";default->throw RelayException.invalid("格式不支持");};}
}


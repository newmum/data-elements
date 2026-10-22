package com.haitong.core;

import com.haitong.config.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;

@Service
public class IngressService {
    private final RelayRepository repo;private final TransactionTemplate tx;private final Json json;
    private final RelayProperties p;private final RouteCatalog routes;
    public IngressService(RelayRepository repo,TransactionTemplate tx,Json json,RelayProperties p,RouteCatalog routes) {
        this.repo=repo;this.tx=tx;this.json=json;this.p=p;this.routes=routes;
    }
    public Accepted http(String owner,String route,String key,byte[] bytes) {
        Rules.key(key);return accept(owner,"HTTP","http:"+owner,route,key,bytes,routes.route(route),false);
    }
    /**
     * Idempotent retries must return their original acceptance even if the
     * current registration contract changed after the first request.
     */
    public Optional<Accepted> existingHttp(String owner,String route,String key,byte[] bytes) {
        Rules.key(key);
        return repo.findMessage("http:"+owner,route,key).map(message -> duplicate(message,Rules.sha256(bytes)));
    }
    public Accepted http(String owner,String route,String key,byte[] bytes,RoutePlan plan,Map<String,Object> metadata) {
        Rules.key(key);return accept(owner,"HTTP","http:"+owner,route,key,bytes,plan,false,metadata);
    }
    public Accepted accept(String owner,String sourceType,String scope,String route,String key,byte[] bytes,RoutePlan plan,boolean quarantineInvalid) {
        return accept(owner,sourceType,scope,route,key,bytes,plan,quarantineInvalid,Map.of());
    }
    public Accepted accept(String owner,String sourceType,String scope,String route,String key,byte[] bytes,RoutePlan plan,boolean quarantineInvalid,Map<String,Object> metadata) {
        String digest=Rules.sha256(bytes);
        var existing=repo.findMessage(scope,route,key);if(existing.isPresent()) return duplicate(existing.get(),digest);
        Prepared prepared=prepare(bytes,plan,quarantineInvalid);
        capacity(plan.targets().size());
        try {
            return Objects.requireNonNull(tx.execute(status-> {
                var old=repo.findMessage(scope,route,key);if(old.isPresent()) return duplicate(old.get(),digest);
                return insert(owner,sourceType,scope,route,key,bytes,digest,prepared,plan,null,null,null,null,metadata);
            }));
        } catch(DataIntegrityViolationException e) {
            // The failed transaction has already rolled back (never continue in a transaction that failed). Read in a NEW transaction.
            return duplicate(repo.findMessage(scope,route,key).orElseThrow(()->e),digest);
        }
    }
    public Accepted action(String owner,String key,byte[] bytes,ActionPlan action) {
        Rules.key(key);Map<String,Object> input=json.object(bytes);json.require(input,action.requiredFields());
        TargetPlan target=new TargetPlan(action.code(),Kind.ACTION,"","",null,null,action);
        RoutePlan plan=new RoutePlan("action."+action.code(),"JSONL",List.of(),Map.of(),List.of(target));
        return accept(owner,"ACTION","action:"+owner,plan.code(),key,bytes,plan,false);
    }
    public record Prepared(byte[] output,String error) {}
    public Prepared prepare(byte[] raw,RoutePlan plan,boolean quarantineInvalid) {
        try {
            if(raw.length>p.getLimits().getMaxMessageBytes()) throw new RelayException(413,"PAYLOAD_TOO_LARGE","单条数据超过限制");
            Map<String,Object> value=json.object(raw);json.require(value,plan.requiredFields());
            BusinessFields.value(value,plan.businessKeyField(),200);BusinessFields.value(value,plan.batchKeyField(),200);BusinessFields.value(value,plan.businessTimeField(),100);
            byte[] output=json.bytes(json.normalize(value,plan.renameFields()));
            if(output.length>p.getLimits().getMaxMessageBytes()) throw new RelayException(413,"PAYLOAD_TOO_LARGE","转换后数据超过限制");
            return new Prepared(output,null);
        } catch(RelayException ex) {
            if(!quarantineInvalid) throw ex;return new Prepared(null,ex.code());
        }
    }
    /** Called only within a transaction holding the corresponding file checkpoint row lock. */
    public Accepted fileRecord(FileRow file,long recordNo,byte[] raw,RoutePlan plan) {
        String key=file.id()+":"+recordNo,scope="file:"+file.id(),digest=Rules.sha256(raw);
        var old=repo.findMessage(scope,plan.code(),key);if(old.isPresent()) return duplicate(old.get(),digest);
        return insert(file.owner(),file.sourceType(),scope,plan.code(),key,raw,digest,prepare(raw,plan,true),plan,file.id(),recordNo,null,null,Map.of("sourceFileId",file.id(),"fileName",file.name(),"recordNumber",recordNo));
    }
    public Accepted binaryFileRecord(FileRow file,RoutePlan plan) {
        String key=file.id()+":1",scope="file:"+file.id();
        var old=repo.findMessage(scope,plan.code(),key);if(old.isPresent()) return duplicate(old.get(),file.sha256());
        return insert(file.owner(),file.sourceType(),scope,plan.code(),key,new byte[0],file.sha256(),new Prepared(null,null),plan,file.id(),1L,file.path(),file.name(),Map.of("sourceFileId",file.id(),"fileName",file.name(),"fileBytes",file.size()));
    }
    private Accepted insert(String owner,String type,String scope,String route,String key,byte[] raw,String digest,Prepared prepared,
                            RoutePlan plan,String fileId,Long recordNo,String fileRef,String fileName,Map<String,Object> metadata) {
        return insertWithId(UUID.randomUUID().toString(),owner,type,scope,route,key,raw,digest,prepared,plan,fileId,recordNo,fileRef,fileName,metadata);
    }
    /** Caller holds the upload row lock; file checkpoint and all deliveries commit atomically. */
    public Accepted registeredBinary(FileRow file,RoutePlan plan,Map<String,Object> metadata,String messageId) {
        return insertWithId(messageId,file.owner(),"FILE_PUSH","file:"+file.id(),plan.code(),file.id()+":1",new byte[0],file.sha256(),new Prepared(null,null),plan,file.id(),1L,file.path(),file.name(),metadata);
    }
    private Accepted insertWithId(String id,String owner,String type,String scope,String route,String key,byte[] raw,String digest,Prepared prepared,
                            RoutePlan plan,String fileId,Long recordNo,String fileRef,String fileName,Map<String,Object> metadata) {
        long now=repo.now();
        Message message=new Message(id,owner,type,scope,key,route,digest,Base64.getEncoder().encodeToString(raw),
            prepared.output()==null?null:Base64.getEncoder().encodeToString(prepared.output()),fileRef,fileName,fileId,recordNo,
            prepared.error()==null?"ACCEPTED":"QUARANTINED",prepared.error(),json.write(plan),null,now);
        repo.insertMessage(message);
        String businessKey=null,batchKey=null,businessTime=null;
        if(prepared.error()==null && fileRef==null){Map<String,Object> input=json.object(raw);
            businessKey=BusinessFields.value(input,plan.businessKeyField(),200);batchKey=BusinessFields.value(input,plan.batchKeyField(),200);businessTime=BusinessFields.value(input,plan.businessTimeField(),100);}
        String outputHash=fileRef!=null?digest:prepared.output()==null?null:Rules.sha256(prepared.output());
        repo.jdbc().update("UPDATE ht_message SET business_key=?,batch_key=?,business_time=?,output_sha256=?,payload_bytes=?,output_bytes=?,source_metadata=? WHERE id=?",
            businessKey,batchKey,businessTime,outputHash,fileRef==null?(long)raw.length:metadata.get("fileBytes"),prepared.output()==null?null:(long)prepared.output().length,json.write(metadata),id);
        Map<String,Object> context=repo.ledger().messageContext(message);context.put("businessKey",businessKey);context.put("batchKey",batchKey);context.put("details",metadata);
        if(fileRef!=null){context.put("recordCount",null);context.put("payloadBytes",metadata.get("fileBytes"));}
        context.put("errorCode",prepared.error());
        repo.ledger().append(com.haitong.store.ExchangeLog.protocol(type),"IN","MESSAGE_RECEIVE","RESULT",prepared.error()==null?"ACCEPTED":"QUARANTINED",context);
        if(prepared.error()==null) for(TargetPlan target:plan.targets()) repo.insertDelivery(id,target,json.write(target),p.getWorker().getMaxAttempts(),now);
        return new Accepted(id,false);
    }
    private Accepted duplicate(Message old,String digest) {
        if(!old.sha256().equals(digest)) throw RelayException.conflict();
        repo.ledger().append(com.haitong.store.ExchangeLog.protocol(old.sourceType()),"IN","MESSAGE_RECEIVE","RESULT","DUPLICATE",repo.ledger().messageContext(old));
        return new Accepted(old.id(),true);
    }
    public void capacity(long additionalTasks) {
        if(repo.pendingDeliveries()+additionalTasks>p.getLimits().getMaxPendingDeliveries()) throw RelayException.capacity();
        try {
            Path root=Path.of(p.getWorkDir());
            if(Files.getFileStore(root).getUsableSpace()<p.getLimits().getMinFreeDiskBytes()) throw RelayException.capacity();
        } catch(java.io.IOException ex) { throw RelayException.capacity(); }
    }
}

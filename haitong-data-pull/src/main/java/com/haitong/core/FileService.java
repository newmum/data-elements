package com.haitong.core;

import com.haitong.config.*;
import com.haitong.connector.FtpConnector;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FileService {
    private static final Logger log=LoggerFactory.getLogger(FileService.class);
    private record Item(long no,byte[] bytes) {}
    private final RelayProperties p;private final RouteCatalog catalog;private final RelayRepository repo;
    private final IngressService ingress;private final TransactionTemplate tx;private final Json json;private final FtpConnector ftp;
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("file-lease").factory());
    private long reservedBytes;
    public FileService(RelayProperties p,RouteCatalog catalog,RelayRepository repo,IngressService ingress,TransactionTemplate tx,Json json,FtpConnector ftp) {
        this.p=p;this.catalog=catalog;this.repo=repo;this.ingress=ingress;this.tx=tx;this.json=json;this.ftp=ftp;
    }
    public Accepted upload(String owner,String channel,String key,String name,InputStream stream) throws Exception {
        Rules.key(key);Rules.fileName(name);RoutePlan plan=catalog.route(channel);validateExtension(name,plan.fileFormat());
        var old=repo.findFile("http-file:"+owner,channel,key);
        if(old.isPresent()) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;long size=0;
            while((n=stream.read(b))!=-1) {size+=n;if(size>p.getLimits().getMaxFileBytes()) throw new RelayException(413,"FILE_TOO_LARGE","文件超过限制");digest.update(b,0,n);}
            String hash=HexFormat.of().formatHex(digest.digest());
            if(!old.get().sha256().equals(hash) || !old.get().name().equals(name)) throw RelayException.conflict();
            repo.ledger().append("HTTP","IN","FILE_RECEIVE","RESULT","DUPLICATE",repo.ledger().fileContext(old.get()));
            return new Accepted(old.get().id(),true);
        }
        reserve(p.getLimits().getMaxFileBytes());
        try {
            PayloadFiles.Saved saved=PayloadFiles.save(Path.of(p.getWorkDir()).resolve("spool"),stream,p.getLimits().getMaxFileBytes(),p.getLimits().getMinFreeDiskBytes());
            return register(owner,"HTTP_FILE","http-file:"+owner,key,name,saved,plan,Map.of("fileName",name),null,null);
        } finally {release(p.getLimits().getMaxFileBytes());}
    }
    private Accepted register(String owner,String type,String scope,String key,String name,PayloadFiles.Saved saved,RoutePlan plan,Map<String,Object> metadata,Long expectedRecords,String sourcePath) throws IOException {
        FileRow row=new FileRow(UUID.randomUUID().toString(),owner,type,scope,key,plan.code(),name,saved.path().toAbsolutePath().normalize().toString(),saved.sha256(),saved.size(),plan.fileFormat(),json.write(plan),"READY",0,0,false,null,0,0,null,repo.now());
        try {tx.executeWithoutResult(status->{
            repo.insertFile(row);repo.jdbc().update("UPDATE ht_file_checkpoint SET expected_records=?,source_metadata=?,source_path=? WHERE id=?",expectedRecords,json.write(metadata),sourcePath,row.id());
            Map<String,Object> c=repo.ledger().fileContext(row);c.put("details",metadata);c.put("sourceRef",sourcePath==null?scope:sourcePath);
            repo.ledger().append(com.haitong.store.ExchangeLog.protocol(type),"IN","FILE_RECEIVE","RESULT","ACCEPTED",c);
        });return new Accepted(row.id(),false);}
        catch(DataIntegrityViolationException ex) {
            FileRow old=repo.findFile(scope,plan.code(),key).orElseThrow(()->ex);
            // Duplicate insertion is known to have rolled back. Generic DB errors keep the spool for recovery.
            Files.deleteIfExists(saved.path());
            if(!old.sha256().equals(row.sha256()) || !old.name().equals(row.name())) throw RelayException.conflict();
            repo.ledger().append(com.haitong.store.ExchangeLog.protocol(type),"IN","FILE_RECEIVE","RESULT","DUPLICATE",repo.ledger().fileContext(old));
            return new Accepted(old.id(),true);
        }
    }
    private void validateExtension(String name,String format) {
        String lower=name.toLowerCase(Locale.ROOT);
        boolean valid=switch(format) {
            case "JSONL" -> lower.endsWith(".jsonl") || lower.endsWith(".ndjson") || lower.endsWith(".json");
            case "CSV" -> lower.endsWith(".csv");
            case "BINARY" -> true;
            default -> false;
        };
        if(!valid) throw new RelayException(415,"FILE_FORMAT_NOT_ALLOWED","文件扩展名不符合通道配置");
    }
    private synchronized void reserve(long amount) throws IOException {
        ingress.capacity(1);
        if(repo.pendingFiles()>=p.getLimits().getMaxPendingFiles()) throw RelayException.capacity();
        Path root=Path.of(p.getWorkDir());long used;
        try(var paths=Files.walk(root)) {
            used=paths.filter(path->Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)).mapToLong(path->{try{return Files.size(path);}catch(IOException e){throw new UncheckedIOException(e);}}).sum();
        }
        if(used+reservedBytes+amount>p.getLimits().getMaxWorkBytes()) throw RelayException.capacity();
        reservedBytes+=amount;
    }
    private synchronized void release(long amount) {reservedBytes-=amount;}
    @Scheduled(fixedDelayString="${relay.ftp.scan.interval-ms:30000}",initialDelayString="${relay.ftp.scan.interval-ms:30000}")
    public void scheduledScan() {
        if(!p.getWorker().isEnabled() || !p.getFtp().getScan().isEnabled()) return;
        try {scan();} catch(Exception e) {log.warn("FTP scan unavailable: errorClass={}",e.getClass().getSimpleName());}
    }
    public synchronized Map<String,Object> scan() throws Exception {
        long started=repo.now();Map<String,Object> c=new LinkedHashMap<>();c.put("attemptId",UUID.randomUUID().toString());
        c.put("routeCode",p.getFtp().getScan().getChannel());c.put("owner","system:ftp");c.put("sourceRef",p.getFtp().getScan().getInputDir());c.put("startedAt",started);
        repo.ledger().append("FTP","IN","FILE_SCAN","START","RUNNING",c);
        try {Map<String,Object> result=scanInternal();c.put("details",result);c.put("durationMs",Math.max(0,repo.now()-started));
            repo.ledger().append("FTP","IN","FILE_SCAN","FINISH","FINISHED",c);return result;
        }catch(Exception e){c.put("errorCode","FTP_SCAN_FAILED");c.put("durationMs",Math.max(0,repo.now()-started));
            repo.ledger().append("FTP","IN","FILE_SCAN","FINISH","FAILED",c);throw e;}
    }
    private Map<String,Object> scanInternal() throws Exception {
        if(!p.getFtp().isEnabled() || !p.getFtp().getScan().isEnabled()) throw new RelayException(409,"FTP_SCAN_DISABLED","请先在配置中启用 FTP 及扫描");
        var config=p.getFtp().getScan();RoutePlan plan=catalog.route(config.getChannel());FtpAddress address=catalog.ftp();
        String input=Rules.remoteDirectory(config.getInputDir());int accepted=0,skipped=0,failed=0,processed=0;
        for(String markerName:ftp.markers(address,input)) {
            if(processed>=config.getMaxFilesPerScan()) break;
            try {
                Map<String,Object> marker=json.object(ftp.marker(address,input,markerName));
                String name=Rules.fileName(Objects.toString(marker.get("fileName"),""));
                if(!markerName.equals(name+".done")) throw RelayException.invalid("完成清单名称必须为原文件名加 .done");
                String fileId=Rules.key(Objects.toString(marker.get("fileId"),""));
                String hash=Objects.toString(marker.get("sha256"),"").toLowerCase(Locale.ROOT);
                long size=Long.parseLong(Objects.toString(marker.get("size"),"-1"));
                if(!hash.matches("[0-9a-f]{64}") || size<0 || size>p.getLimits().getMaxFileBytes()) throw RelayException.invalid("FTP 清单大小或摘要不合法");
                Long expected=marker.get("recordCount")==null?null:Long.valueOf(marker.get("recordCount").toString());
                if(expected!=null && expected<0)throw RelayException.invalid("recordCount 不能为负数");
                String format=Objects.toString(marker.get("format"),plan.fileFormat());
                if(!format.equals(plan.fileFormat())) throw RelayException.invalid("清单格式与通道路由不一致");
                validateExtension(name,format);
                String key=Rules.sha256((input+"/"+name+"|"+fileId).getBytes(StandardCharsets.UTF_8));
                var old=repo.findFile("ftp:main",plan.code(),key);
                if(old.isPresent()) {
                    if(!old.get().sha256().equals(hash) || old.get().size()!=size) throw RelayException.conflict();
                    skipped++;continue;
                }
                processed++;reserve(size);
                try {
                    long started=repo.now();Map<String,Object> c=new LinkedHashMap<>();c.put("attemptId",UUID.randomUUID().toString());c.put("owner","system:ftp");
                    c.put("routeCode",plan.code());c.put("sourceRef",input+"/"+name);c.put("sha256",hash);c.put("payloadBytes",size);c.put("startedAt",started);
                    Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("fileName",name);metadata.put("sourceFileId",fileId);metadata.put("format",format);
                    if(expected!=null)metadata.put("expectedRecords",expected);c.put("details",metadata);
                    repo.ledger().append("FTP","IN","FILE_DOWNLOAD","START","RUNNING",c);
                    try {
                        var saved=ftp.download(address,input,name,Math.min(size,p.getLimits().getMaxFileBytes()));
                        if(saved.size()!=size || !saved.sha256().equals(hash)) {Files.deleteIfExists(saved.path());throw RelayException.invalid("FTP 文件与完成清单不一致");}
                        Accepted a=register("system:ftp","FTP_FILE","ftp:main",key,name,saved,plan,metadata,format.equals("BINARY")?null:expected,input+"/"+name);
                        c.put("fileId",a.id());c.put("durationMs",Math.max(0,repo.now()-started));
                        repo.ledger().append("FTP","IN","FILE_DOWNLOAD","FINISH","VERIFIED",c);accepted++;
                    }catch(Exception e){c.put("errorCode","FTP_DOWNLOAD_OR_REGISTER_FAILED");c.put("durationMs",Math.max(0,repo.now()-started));
                        repo.ledger().append("FTP","IN","FILE_DOWNLOAD","FINISH","FAILED",c);throw e;}

                } finally {release(size);}
            } catch(Exception e) {
                Map<String,Object> errorContext=new LinkedHashMap<>();errorContext.put("owner","system:ftp");errorContext.put("routeCode",plan.code());
                errorContext.put("sourceRef",input);errorContext.put("errorCode",e instanceof RelayException re?re.code():"FTP_SOURCE_ENTRY_FAILED");
                errorContext.put("details",Map.of("markerNameSha256",Rules.sha256(markerName.getBytes(StandardCharsets.UTF_8)),"exceptionType",e.getClass().getSimpleName()));
                repo.ledger().append("FTP","IN","SOURCE_ENTRY","RESULT","FAILED",errorContext);
                failed++;log.warn("FTP source entry rejected: markerHash={}, errorClass={}",Rules.sha256(markerName.getBytes(StandardCharsets.UTF_8)).substring(0,12),e.getClass().getSimpleName());
                if(e instanceof RelayException ex && ex.status()==503) break;
            }
        }
        return Map.of("accepted",accepted,"skipped",skipped,"failed",failed,"sourceFilesDeleted",0);
    }
    @Scheduled(fixedDelay=500,initialDelay=1000)
    public void scheduledFile() {
        if(!p.getWorker().isEnabled()) return;
        try {runOne();} catch(Exception e) {log.warn("File worker unavailable: errorClass={}",e.getClass().getSimpleName());}
    }
    public synchronized boolean runOne() {
        var selected=repo.claimFile(p.getWorker().getLeaseSeconds(),p.getWorker().getMaxAttempts());if(selected.isEmpty()) return false;
        FileRow file=selected.get();AtomicBoolean valid=new AtomicBoolean(true);
        ScheduledFuture<?> renewal=heartbeat.scheduleAtFixedRate(()->{
            try {if(!repo.renewFile(file,p.getWorker().getLeaseSeconds())) valid.set(false);} catch(RuntimeException e) {valid.set(false);}
        },10,10,TimeUnit.SECONDS);
        try {parse(file,valid);}
        catch(Exception e) {
            boolean permanent=e instanceof NoSuchFileException || e instanceof java.nio.charset.CharacterCodingException ||
                e instanceof RelayException re && re.status()!=503;
            if(valid.get()) repo.failFile(file,e instanceof RelayException re?re.code():permanent?"FILE_INVALID_OR_MISSING":"FILE_PROCESSING_INTERRUPTED",permanent,p.getWorker().getMaxAttempts());
            log.warn("File processing failed: id={}, errorClass={}",file.id(),e.getClass().getSimpleName());
        } finally {renewal.cancel(false);}
        return true;
    }
    private void parse(FileRow file,AtomicBoolean valid) throws Exception {
        Path path=Path.of(file.path()).toRealPath();
        if(!path.startsWith(Path.of(p.getWorkDir()).toRealPath())) throw RelayException.invalid("工作文件路径越界");
        if(Files.size(path)!=file.size() || !PayloadFiles.sha256(path).equals(file.sha256())) throw RelayException.invalid("持久化文件已损坏或发生变化");
        RoutePlan plan=json.read(file.snapshot(),RoutePlan.class);
        if(file.format().equals("BINARY")) {
            ingress.capacity(plan.targets().size());
            tx.executeWithoutResult(status->{repo.lockFile(file);ingress.binaryFileRecord(file,plan);repo.advanceFile(file,1);});
            repo.finishFile(file,1);return;
        }
        List<Item> batch=new ArrayList<>();long no=0;
        if(file.format().equals("JSONL")) {
            try(Reader reader=PayloadFiles.utf8Reader(path)) {
                String line;boolean first=true;
                while((line=PayloadFiles.readLine(reader,p.getLimits().getMaxMessageBytes()))!=null) {
                    if(first) {first=false;if(line.startsWith("\uFEFF")) line=line.substring(1);}
                    if(line.isBlank()) continue;
                    no++;if(no<=file.lastRecordNo()) continue;
                    batch.add(new Item(no,line.getBytes(StandardCharsets.UTF_8)));
                    if(batch.size()>=p.getWorker().getFileBatchSize()) flush(file,plan,batch,valid);
                }
            }
        } else {
            try(CsvRecords csv=new CsvRecords(PayloadFiles.utf8Reader(path),p.getLimits().getMaxMessageBytes())) {
                List<String> headers=csv.next();
                if(headers!=null) {
                    if(headers.stream().anyMatch(String::isBlank) || new HashSet<>(headers).size()!=headers.size()) throw RelayException.invalid("CSV 表头不能为空或重复");
                    List<String> fields;
                    while((fields=csv.next())!=null) {
                        no++;if(no<=file.lastRecordNo()) continue;
                        if(fields.size()!=headers.size()) throw RelayException.invalid("CSV 列数与表头不一致");
                        Map<String,Object> object=new LinkedHashMap<>();for(int i=0;i<headers.size();i++) object.put(headers.get(i),fields.get(i));
                        batch.add(new Item(no,json.bytes(object)));
                        if(batch.size()>=p.getWorker().getFileBatchSize()) flush(file,plan,batch,valid);
                    }
                }
            }
        }
        flush(file,plan,batch,valid);
        if(!valid.get()) throw new IllegalStateException("文件租约失效");
        repo.finishFile(file,no);
    }
    private void flush(FileRow file,RoutePlan plan,List<Item> batch,AtomicBoolean valid) {
        if(batch.isEmpty()) return;
        if(!valid.get()) throw new IllegalStateException("文件租约失效");
        ingress.capacity((long)batch.size()*plan.targets().size());
        tx.executeWithoutResult(status->{
            repo.lockFile(file);
            for(Item item:batch) ingress.fileRecord(file,item.no(),item.bytes(),plan);
            repo.advanceFile(file,batch.getLast().no());
        });
        batch.clear();
    }
    public Map<String,Object> status(String id,String owner,boolean admin) {
        FileRow f=repo.file(id).orElseThrow(RelayException::notFound);if(!admin && !f.owner().equals(owner)) throw RelayException.notFound();
        return status(f,repo.fileStats(id));
    }
    public List<Map<String,Object>> statusesForAdminList(List<FileRow> rows) {
        if(rows.isEmpty()) return List.of();
        Map<String,Map<String,Long>> counts=repo.fileStatsBatch(rows.stream().map(FileRow::id).toList());
        return rows.stream().map(f->status(f,counts.get(f.id()))).toList();
    }
    private Map<String,Object> status(FileRow f,Map<String,Long> counts) {
        String id=f.id(),state=f.status();
        if(f.eof()) {
            boolean failed=counts.getOrDefault("quarantined",0L)>0 || counts.getOrDefault("FAILED",0L)>0 || counts.getOrDefault("UNKNOWN",0L)>0;
            boolean pending=counts.getOrDefault("PENDING",0L)+counts.getOrDefault("RUNNING",0L)+counts.getOrDefault("RETRY_WAIT",0L)>0;
            state=failed?"PARTIAL_FAILURE":pending?"INGESTED":"COMPLETED";
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("fileId",id);result.put("fileName",f.name());result.put("status",state);
        result.put("lastRecordNo",f.lastRecordNo());result.put("totalRecords",f.eof()?f.totalRecords():null);result.put("eof",f.eof());
        result.put("counts",counts);result.put("errorCode",f.errorCode());return result;
    }
    @PreDestroy public void close() {heartbeat.shutdownNow();}
}

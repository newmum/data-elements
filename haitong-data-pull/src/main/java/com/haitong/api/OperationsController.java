package com.haitong.api;

import com.haitong.config.*;
import com.haitong.connector.*;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/internal/v1")
public class OperationsController {
    private final RelayRepository repo;private final RelayProperties p;private final RouteCatalog catalog;private final Json json;
    private final TransactionTemplate tx;private final KafkaConnector kafka;private final FtpConnector ftp;private final FileService files;
    public OperationsController(RelayRepository repo,RelayProperties p,RouteCatalog catalog,Json json,TransactionTemplate tx,KafkaConnector kafka,FtpConnector ftp,FileService files) {
        this.repo=repo;this.p=p;this.catalog=catalog;this.json=json;this.tx=tx;this.kafka=kafka;this.ftp=ftp;this.files=files;
    }
    @GetMapping("/overview")
    public Map<String,Object> overview() throws java.io.IOException {
        return Map.of("deliveryCounts",repo.counts(),"oldestPendingAgeMs",repo.oldestPendingAgeMs(),
            "pendingFiles",repo.pendingFiles(),"databaseTime",repo.now(),
            "workDirUsableBytes",Files.getFileStore(Path.of(p.getWorkDir())).getUsableSpace());
    }
    @GetMapping("/config-summary")
    public Map<String,Object> config() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("editFile","config/application.yml");result.put("restartRequired",true);
        result.put("kafka",Map.of("enabled",p.getKafka().isEnabled(),"bootstrapServers",p.getKafka().getBootstrapServers(),"producerTopic",p.getKafka().getProducerTopic(),"consumerEnabled",p.getKafka().getConsumer().isEnabled(),"consumerTopic",p.getKafka().getConsumer().getTopic(),"groupId",p.getKafka().getConsumer().getGroupId()));
        result.put("ftp",Map.of("enabled",p.getFtp().isEnabled(),"protocol",p.getFtp().getProtocol(),"host",p.getFtp().getHost(),"port",p.getFtp().getPort(),"outputDir",p.getFtp().getOutputDir(),"scanEnabled",p.getFtp().getScan().isEnabled(),"inputDir",p.getFtp().getScan().getInputDir()));
        result.put("gateway",Map.of("publicBaseUrl",p.getGateway().getPublicBaseUrl(),"additionalKeyRequired",!p.getGateway().getSharedKey().isBlank()));
        result.put("routes",p.getRoutes().entrySet().stream().filter(e->e.getValue().isEnabled()).map(e->Map.of("code",e.getKey(),"targets",e.getValue().getTargets(),"fileFormat",e.getValue().getFileFormat())).toList());
        result.put("actions",p.getActions().entrySet().stream().filter(e->e.getValue().isEnabled()).map(e->Map.of("code",e.getKey(),"mode",e.getValue().getMode(),"handler",e.getValue().getHandler())).toList());
        return result;
    }
    @GetMapping("/deliveries")
    public List<Map<String,Object>> deliveries(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        bounds(limit,offset);
        if(!status.isBlank() && !Set.of("PENDING","RUNNING","RETRY_WAIT","FAILED","UNKNOWN","SUCCEEDED").contains(status)) throw RelayException.invalid("未知状态");
        return repo.listDeliveries(status,limit,offset).stream().map(d->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("deliveryId",d.id());m.put("messageId",d.messageId());m.put("targetCode",d.targetCode());m.put("type",d.kind());
            m.put("status",d.status());m.put("attempts",d.attempts());m.put("maxAttempts",d.maxAttempts());m.put("nextAttemptAt",d.nextAttemptAt());m.put("errorCode",d.errorCode());return m;
        }).toList();
    }
    @PostMapping("/deliveries/{id}/retry")
    public Map<String,Object> retry(@PathVariable String id,@RequestBody Map<String,Object> input) {
        String reason=reason(input);
        tx.executeWithoutResult(status->{
            lock(id);Delivery before=repo.delivery(id).orElseThrow();String audit=appendAudit(repo.audit(id),"RETRY",reason,"");
            if(!repo.retry(id,audit)) throw new RelayException(409,"RETRY_NOT_ALLOWED","仅允许重试可安全重试的 FAILED 任务；成功或 UNKNOWN 任务不可直接重试");
            manualEvent(before,"MANUAL_RETRY",reason,"");
        });
        return Map.of("deliveryId",id,"status","RETRY_WAIT");
    }
    @PostMapping("/deliveries/{id}/resolve")
    public Map<String,Object> resolve(@PathVariable String id,@RequestBody Map<String,Object> input) {
        String reason=reason(input),decision=Objects.toString(input.get("decision"),""),evidence=Objects.toString(input.get("evidence"),"").strip();
        if(!Set.of("MARK_SUCCEEDED","RETRY_CONFIRMED").contains(decision) || evidence.length()<5 || evidence.length()>2000) throw RelayException.invalid("必须给出决策和核实证据");
        boolean succeeded=decision.equals("MARK_SUCCEEDED");
        tx.executeWithoutResult(status->{
            lock(id);Delivery before=repo.delivery(id).orElseThrow();String audit=appendAudit(repo.audit(id),decision,reason,evidence);
            if(!repo.resolve(id,succeeded,audit,json.write(Map.of("manualResolution",decision,"evidence",evidence)))) throw new RelayException(409,"RESOLVE_NOT_ALLOWED","仅 UNKNOWN 任务需要人工核实");
            manualEvent(before,decision,reason,evidence);
        });
        return Map.of("deliveryId",id,"status",succeeded?"SUCCEEDED":"RETRY_WAIT");
    }
    @GetMapping("/files")
    public List<Map<String,Object>> fileList(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        bounds(limit,offset);return files.statusesForAdminList(repo.listFiles(limit,offset));
    }
    @PostMapping("/files/{id}/retry")
    public Map<String,Object> retryFile(@PathVariable String id,@RequestBody Map<String,Object> input) {
        String reason=reason(input);
        tx.executeWithoutResult(status->{
            var rows=repo.jdbc().queryForList("SELECT id FROM ht_file_checkpoint WHERE id=? FOR UPDATE",String.class,id);
            if(rows.isEmpty()) throw RelayException.notFound();
            String audit=repo.jdbc().queryForObject("SELECT retry_audit FROM ht_file_checkpoint WHERE id=?",String.class,id);
            if(repo.jdbc().update("UPDATE ht_file_checkpoint SET status='RETRY_WAIT',attempts=0,next_attempt_at=?,retry_audit=?,last_error_code=NULL WHERE id=? AND status='FAILED'",repo.now(),appendAudit(audit,"FILE_RETRY",reason,""),id)!=1)
                throw new RelayException(409,"RETRY_NOT_ALLOWED","仅失败文件可以补跑，已接管记录不会重复建任务");
            FileRow f=repo.file(id).orElseThrow();Map<String,Object> c=repo.ledger().fileContext(f);c.put("owner","admin");c.put("details",Map.of("reason",reason));
            repo.ledger().append(com.haitong.store.ExchangeLog.protocol(f.sourceType()),"ADMIN","FILE_RETRY","RESULT","RETRY_REQUESTED",c);
        });
        return Map.of("fileId",id,"status","RETRY_WAIT");
    }
    @PostMapping("/ftp-scans/{route}")
    public Map<String,Object> scan(@PathVariable String route) throws Exception {
        if(!route.equals(p.getFtp().getScan().getChannel())) throw RelayException.notFound();return files.scan();
    }
    @GetMapping("/connectors/{name}/health")
    public Map<String,Object> health(@PathVariable String name) throws Exception {
        return switch(name) {
            case "database" -> Map.of("connector","database","status","UP","time",repo.now());
            case "ftp" -> p.getFtp().isEnabled()?ftp.health(catalog.ftp()):Map.of("connector","ftp","status","DISABLED");
            case "kafka" -> {
                if(!p.getKafka().isEnabled()) yield Map.of("connector","kafka","status","DISABLED");
                Set<String> topics=new HashSet<>();
                for(var route:p.getRoutes().entrySet()) if(route.getValue().isEnabled()) for(var t:catalog.route(route.getKey()).targets()) if(t.kind()==Kind.KAFKA) topics.add(t.topic());
                if(p.getKafka().getConsumer().isEnabled()) topics.add(p.getKafka().getConsumer().getTopic());
                yield kafka.health(catalog.kafka(),topics);
            }
            default -> throw RelayException.notFound();
        };
    }
    @GetMapping("/quarantined")
    public List<Map<String,Object>> quarantined(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        bounds(limit,offset);return repo.quarantined(limit,offset).stream().map(m->Map.<String,Object>of("messageId",m.id(),"channel",m.routeCode(),"sourceType",m.sourceType(),"errorCode",m.errorCode(),"createdAt",m.createdAt())).toList();
    }
    @GetMapping("/messages/{id}/payload")
    public ResponseEntity<Resource> payload(@PathVariable String id) throws Exception {
        Message m=repo.message(id).orElseThrow(RelayException::notFound);Resource resource;
        if(m.fileRef()!=null && !m.fileRef().isBlank()) {
            Path path=Path.of(m.fileRef()).toRealPath();if(!path.startsWith(Path.of(p.getWorkDir()).toRealPath())) throw RelayException.notFound();resource=new FileSystemResource(path);
        } else {
            if(m.payload()==null) throw new RelayException(410,"PAYLOAD_PURGED","原文已按保留策略清理，去重记录仍保留");
            resource=new ByteArrayResource(Base64.getDecoder().decode(m.payload()));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition","attachment; filename=message-"+UUID.fromString(id)+".bin").body(resource);
    }
    @GetMapping(value="/openapi",produces="application/yaml")
    public Resource openapi() {return new ClassPathResource("openapi.yaml");}
    private void manualEvent(Delivery d,String operation,String reason,String evidence){
        Map<String,Object> c=repo.ledger().deliveryContext(repo.message(d.messageId()).orElseThrow(),d);
        c.put("owner","admin");c.put("details",Map.of("reason",reason,"evidence",evidence,"operation",operation));
        repo.ledger().append(com.haitong.store.ExchangeLog.protocol(d.kind().name()),"ADMIN","MANUAL_OPERATION","RESULT",operation,c);
    }
    private void lock(String id) {if(repo.jdbc().queryForList("SELECT id FROM ht_delivery WHERE id=? FOR UPDATE",String.class,id).isEmpty()) throw RelayException.notFound();}
    @SuppressWarnings("unchecked")
    private String appendAudit(String old,String operation,String reason,String evidence) {
        List<Object> list=new ArrayList<>(json.read(old,List.class));
        if(list.size()>=200) throw new RelayException(409,"AUDIT_LIMIT","此任务已超过人工处理次数限制，请核查根因");
        list.add(Map.of("at",repo.now(),"actor","admin","operation",operation,"reason",reason,"evidence",evidence));return json.write(list);
    }
    private static String reason(Map<String,Object> input) {
        String reason=Objects.toString(input.get("reason"),"").strip();
        if(reason.length()<5 || reason.length()>1000) throw RelayException.invalid("reason 必填，长度 5–1000");return reason;
    }
    private static void bounds(int limit,int offset) {if(limit<1 || limit>200 || offset<0 || offset>1000000) throw RelayException.invalid("分页参数不合法");}
}

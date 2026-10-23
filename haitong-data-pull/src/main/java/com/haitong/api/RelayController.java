package com.haitong.api;

import com.haitong.config.*;
import com.haitong.connector.ActionConnector;
import com.haitong.core.*;
import com.haitong.core.Models.*;
import com.haitong.store.RelayRepository;
import com.haitong.registry.RegistrationSchemaClient;
import com.haitong.registry.StandalonePushSchema;
import com.haitong.registry.StandaloneRegistryStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class RelayController {
    private final RelayProperties p;private final IngressService ingress;private final FileService files;
    private final RelayRepository repo;private final RouteCatalog catalog;private final ActionConnector actions;private final Json json;
    private final RegistrationSchemaClient registration;
    private final StandalonePushSchema standalone;
    private final StandaloneRegistryStore standaloneRegistry;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<com.haitong.files.FileUploadService> fileUploads;
    public RelayController(RelayProperties p,IngressService ingress,FileService files,RelayRepository repo,RouteCatalog catalog,ActionConnector actions,Json json,RegistrationSchemaClient registration,StandalonePushSchema standalone,StandaloneRegistryStore standaloneRegistry) {
        this.p=p;this.ingress=ingress;this.files=files;this.repo=repo;this.catalog=catalog;this.actions=actions;this.json=json;this.registration=registration;this.standalone=standalone;this.standaloneRegistry=standaloneRegistry;
    }
    @PostMapping(path="/ingest/{channel}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> ingest(@PathVariable String channel,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        Secrets.Identity caller=AuthenticationFilter.identity(request);allowChannel(caller,channel);
        Accepted accepted=ingress.http(caller.id(),channel,key,body(request));request.setAttribute("haitong.messageId",accepted.id());return receipt(accepted,caller);
    }
    /**
     * Unified public table-batch entry point.  The requested delivery type is
     * only a routing choice (FTP, KAFKA or LOG); destination addresses and
     * topics remain controlled by the registered datasource contract.
     */
    @PostMapping(path="/data-push",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> dataPush(@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        byte[] input=body(request);PushRoute route=pushRoute(input);
        if(route.registrySync()) return registrySync(key,request,input);
        return simpleIngest(route.channel(),route.destination(),key,request,input);
    }
    /**
     * Same controlled ingress flow, exposed only to the bundled Magic API
     * operations scripts.  The script supplies a parsed JSON object while the
     * transport authentication, idempotency, route checks and persistence stay
     * in this Java service.
     */
    public ResponseEntity<Map<String,Object>> magicDataPush(String key,HttpServletRequest request,Map<String,Object> input) throws IOException {
        byte[] bytes=json.bytes(input==null?Map.of():input);PushRoute route=pushRoute(bytes);
        if(route.registrySync()) return registrySync(key,request,bytes);
        return simpleIngest(route.channel(),route.destination(),key,request,bytes);
    }
    /**
     * Legacy aliases retained for existing clients.  New integrations must
     * use POST /api/v1/data-push with deliveryType instead.
     */
    @Deprecated
    @PostMapping(path="/simple/ftp",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> tableToFtp(@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        return simpleIngest("table-to-ftp","FTP",key,request);
    }
    @Deprecated
    @PostMapping(path="/simple/kafka",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> tableToKafka(@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        return simpleIngest("table-to-kafka","Kafka",key,request);
    }
    @Deprecated
    @PostMapping(path="/simple/log",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> tableToLog(@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        return simpleIngest("table-to-log","日志",key,request);
    }
    @GetMapping("/messages/{id}")
    public Map<String,Object> message(@PathVariable String id,HttpServletRequest request) {return messageStatus(id,AuthenticationFilter.identity(request));}
    public Map<String,Object> messageStatus(String id,Secrets.Identity caller) {
        Optional<Message> found=repo.message(id);
        if(found.isEmpty()&&fileUploads!=null){var service=fileUploads.getIfAvailable();if(service!=null)return service.pendingMessage(caller,id).orElseThrow(RelayException::notFound);}
        Message m=found.orElseThrow(RelayException::notFound);
        if(!caller.admin() && !m.owner().equals(caller.id())) throw RelayException.notFound();
        List<Delivery> list=repo.deliveries(id);List<String> states=list.stream().map(Delivery::status).toList();
        Map<String,Object> result=new LinkedHashMap<>();result.put("messageId",id);result.put("channel",m.routeCode());
        result.put("status",m.status().equals("QUARANTINED")?"QUARANTINED":Rules.aggregate(states));
        result.put("completed",states.stream().allMatch(Rules::terminal));result.put("errorCode",m.errorCode());
        result.put("deliveries",list.stream().map(d->{
            Map<String,Object> item=new LinkedHashMap<>();item.put("deliveryId",d.id());item.put("targetCode",d.targetCode());item.put("type",d.kind());
            item.put("status",d.status());item.put("attempts",d.attempts());item.put("errorCode",d.errorCode());
            if(d.result()==null)item.put("result",null);
            else if(caller.admin())item.put("result",json.read(d.result(),Object.class));
            else {Map<String,Object> safe=new LinkedHashMap<>();Map<String,Object> full=json.object(d.result().getBytes(StandardCharsets.UTF_8));
                for(String key:List.of("completion","httpStatus","fileName","sha256","size","bytes","partition","offset"))if(full.containsKey(key))safe.put(key,full.get(key));item.put("result",safe);}
            return item;
        }).toList());
        return result;
    }
    @PostMapping(path="/files/{channel}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,Object>> upload(@PathVariable String channel,@RequestHeader("Idempotency-Key") String key,
              @RequestPart("file") MultipartFile file,HttpServletRequest request) throws Exception {
        var caller=AuthenticationFilter.identity(request);allowChannel(caller,channel);
        if(file.getSize()>p.getLimits().getMaxFileBytes()) throw new RelayException(413,"FILE_TOO_LARGE","文件超过限制");
        Accepted accepted;
        try(InputStream stream=file.getInputStream()) {accepted=files.upload(caller.id(),channel,key,Objects.toString(file.getOriginalFilename(),"upload.bin"),stream);}
        request.setAttribute("haitong.fileId",accepted.id());String url=url("/api/v1/files/"+accepted.id());Map<String,Object> result=new LinkedHashMap<>(files.status(accepted.id(),caller.id(),caller.admin()));
        result.put("duplicate",accepted.duplicate());result.put("statusUrl",url);
        return ResponseEntity.status(accepted.duplicate()?200:202).location(URI.create(url)).body(result);
    }
    @GetMapping("/files/{id}")
    public Map<String,Object> file(@PathVariable String id,HttpServletRequest request) {
        var caller=AuthenticationFilter.identity(request);return files.status(id,caller.id(),caller.admin());
    }
    @PostMapping(path="/actions/{code}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> action(@PathVariable String code,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest request) throws Exception {
        var caller=AuthenticationFilter.identity(request);
        if(!caller.action(code)) throw new RelayException(403,"FORBIDDEN_ACTION","没有执行此操作的权限");
        var config=catalog.actionConfig(code);var action=catalog.action(code);byte[] bytes=body(request);
        if(config.getMode().equals("ASYNC")){Accepted a=ingress.action(caller.id(),key,bytes,action);request.setAttribute("haitong.messageId",a.id());return receipt(a,caller);}
        String id=key==null?UUID.randomUUID().toString():Rules.key(key);
        String stableKey="sync-"+Rules.sha256((caller.id()+"|"+code+"|"+id).getBytes(StandardCharsets.UTF_8));
        Map<String,Object> c=new LinkedHashMap<>();long started=repo.now();c.put("startedAt",started);c.put("attemptId",UUID.randomUUID().toString());
        c.put("requestId",request.getAttribute("haitong.requestId"));c.put("owner",caller.id());c.put("routeCode","action."+code);c.put("targetCode",code);
        c.put("destinationRef",code);c.put("sha256",Rules.sha256(bytes));c.put("payloadBytes",bytes.length);c.put("recordCount",1);
        repo.ledger().append("HTTP","OUT","SYNC_ACTION","START","RUNNING",c);
        try {Map<String,Object> result=actions.execute(action,bytes,stableKey,caller.id());c.put("httpStatus",result.get("httpStatus"));
            c.put("details",Map.of("handler",action.handler(),"completion",Objects.toString(result.get("completion"),"LOCAL_VALIDATION")));
            c.put("durationMs",Math.max(0,repo.now()-started));repo.ledger().append("HTTP","OUT","SYNC_ACTION","FINISH","SUCCEEDED",c);
            return ResponseEntity.ok(result);
        }catch(Exception e){c.put("errorCode",e instanceof DeliveryFailure f?f.code:"SYNC_ACTION_INTERRUPTED");c.put("durationMs",Math.max(0,repo.now()-started));
            repo.ledger().append("HTTP","OUT","SYNC_ACTION","FINISH",e instanceof DeliveryFailure f&&f.unknown?"UNKNOWN":"FAILED",c);throw e;}
    }
    private ResponseEntity<Map<String,Object>> receipt(Accepted accepted,Secrets.Identity caller) {
        Map<String,Object> result=new LinkedHashMap<>(messageStatus(accepted.id(),caller));String url=url("/api/v1/messages/"+accepted.id());
        result.put("code",accepted.duplicate()?"ALREADY_ACCEPTED":"ACCEPTED");result.put("duplicate",accepted.duplicate());result.put("statusUrl",url);
        int status=accepted.duplicate() && Boolean.TRUE.equals(result.get("completed"))?200:202;
        return ResponseEntity.status(status).location(URI.create(url)).body(result);
    }
    private byte[] body(HttpServletRequest req) throws IOException {
        if(req.getHeader("Content-Encoding")!=null && !req.getHeader("Content-Encoding").equalsIgnoreCase("identity")) throw new RelayException(415,"ENCODING_NOT_ALLOWED","本接口不接收压缩请求体");
        if(req.getContentLengthLong()>p.getLimits().getMaxMessageBytes()) throw new RelayException(413,"PAYLOAD_TOO_LARGE","请求体超过限制");
        return PayloadFiles.readLimited(req.getInputStream(),p.getLimits().getMaxMessageBytes());
    }
    private void allowChannel(Secrets.Identity caller,String channel) {
        Rules.code(channel);if(!caller.channel(channel)) throw new RelayException(403,"FORBIDDEN_CHANNEL","没有此数据通道的权限");
    }
    private ResponseEntity<Map<String,Object>> simpleIngest(String channel,String destination,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        return simpleIngest(channel,destination,key,request,body(request));
    }
    private ResponseEntity<Map<String,Object>> simpleIngest(String channel,String destination,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request,byte[] input) throws IOException {
        Secrets.Identity caller=AuthenticationFilter.identity(request);allowChannel(caller,channel);
        var route=p.getRoutes().get(channel);
        if(route==null || !route.isEnabled())
            throw new RelayException(503,"SCENE_NOT_READY",destination+"场景尚未配置完成；请按海通数据推送服务工程文档填写连接信息后启用。");
        boolean pushRoute=channel.equals("table-to-ftp") || channel.equals("table-to-kafka");
        boolean registrationManaged=pushRoute && route.isRegistrationManaged();
        validateTableBatch(input,pushRoute);
        Optional<Accepted> duplicate=ingress.existingHttp(caller.id(),channel,key,input);
        if(duplicate.isPresent()) {
            request.setAttribute("haitong.messageId",duplicate.get().id());
            return receipt(duplicate.get(),caller);
        }
        Accepted accepted;
        if(registrationManaged) {
            var resolved=registration.validate(caller.id(),channel,input);
            var plan=catalog.registrationManagedRoute(channel,resolved.kind(),resolved.destination());
            accepted=ingress.http(caller.id(),channel,key,input,plan,resolved.metadata());
        } else if(pushRoute) {
            var metadata=standalone.validate(caller.id(),input);
            accepted=ingress.http(caller.id(),channel,key,input,catalog.route(channel),metadata);
        } else {
            accepted=ingress.http(caller.id(),channel,key,input);
        }
        request.setAttribute("haitong.messageId",accepted.id());return receipt(accepted,caller);
    }
    private PushRoute pushRoute(byte[] input) {
        Map<String,Object> batch=json.object(input);Object value=batch.get("deliveryType");
        String type=value instanceof String text?text.trim().toUpperCase(Locale.ROOT):"";
        return switch(type) {
            case "FTP" -> new PushRoute("table-to-ftp","FTP",false);
            case "KAFKA" -> new PushRoute("table-to-kafka","Kafka",false);
            case "LOG" -> new PushRoute("table-to-log","日志",false);
            case "REGISTRY_SYNC" -> new PushRoute("standalone-registry-sync","独立登记配置",true);
            case "" -> throw RelayException.invalid("deliveryType 必填，仅支持 FTP、KAFKA、LOG 或 REGISTRY_SYNC");
            default -> throw new RelayException(422,"UNSUPPORTED_DELIVERY_TYPE","deliveryType 仅支持 FTP、KAFKA、LOG 或 REGISTRY_SYNC");
        };
    }
    private ResponseEntity<Map<String,Object>> registrySync(String key,HttpServletRequest request,byte[] input) {
        Secrets.Identity caller=AuthenticationFilter.identity(request);
        if(!p.getStandaloneRegistry().isSyncEnabled())
            throw new RelayException(503,"STANDALONE_REGISTRY_SYNC_NOT_READY","独立登记配置同步服务尚未启用");
        if(!caller.admin() && !caller.id().equals("platform-standalone-registry"))
            throw new RelayException(403,"FORBIDDEN_REGISTRY_SYNC","仅允许数据源平台同步独立登记配置");
        if (json.object(input).containsKey("datasources")) {
            var result=standaloneRegistry.synchronizeBatch(key,input);
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("code",result.duplicate()?"ALREADY_SYNCED":"REGISTRY_SYNCED");
            body.put("duplicate",result.duplicate());body.put("datasourceIds",result.datasourceIds());
            body.put("datasourceCount",result.datasourceIds().size());body.put("tableCount",result.tableCount());body.put("columnCount",result.columnCount());
            return ResponseEntity.status(result.duplicate()?HttpStatus.OK:HttpStatus.CREATED).body(body);
        }
        var result=standaloneRegistry.synchronize(key,input);
        Map<String,Object> body=new LinkedHashMap<>();body.put("code",result.duplicate()?"ALREADY_SYNCED":"REGISTRY_SYNCED");
        body.put("duplicate",result.duplicate());body.put("datasourceId",result.datasourceId());body.put("tableCount",result.tableCount());body.put("columnCount",result.columnCount());
        return ResponseEntity.status(result.duplicate()?HttpStatus.OK:HttpStatus.CREATED).body(body);
    }
    private record PushRoute(String channel,String destination,boolean registrySync) { }
    private void validateTableBatch(byte[] input,boolean requireDatasource) {
        Map<String,Object> batch=json.object(input);
        if(batch.containsKey("schemaVersion")||batch.containsKey("occurredAt"))
            throw RelayException.invalid("schemaVersion 和 occurredAt 已取消，请从表批次请求中移除");
        Object table=batch.get("tableName");
        if(!(table instanceof String name) || name.isBlank() || name.length()>200)
            throw RelayException.invalid("tableName 必须是 1 到 200 字符的表名");
        if(requireDatasource) {
            Object datasource=batch.get("datasourceId");
            if(!(datasource instanceof String sourceId) || !sourceId.matches("[A-Za-z0-9_-]{1,64}"))
                throw RelayException.invalid("datasourceId 必须是已登记数据源标识");
        }
        if(!(batch.get("records") instanceof List<?> rows) || rows.isEmpty())
            throw RelayException.invalid("records 必须是至少包含一条记录的 JSON 数组");
        if(rows.size()>10000) throw RelayException.invalid("单次 records 最多 10000 条，请拆分批次推送");
        if(rows.stream().anyMatch(row->!(row instanceof Map<?,?>)))
            throw RelayException.invalid("records 中的每一条记录必须是 JSON 对象");
    }
    private String url(String path) {return p.getGateway().getPublicBaseUrl().replaceAll("/+$","")+path;}
}

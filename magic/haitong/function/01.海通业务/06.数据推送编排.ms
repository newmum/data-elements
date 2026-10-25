{"id":"haitong-function-push","groupId":"haitong-business-functions","name":"06.数据推送编排","path":"/push","parameters":[{"name":"input","dataType":"Object"}],"description":"通道选择、登记权限与字段校验、幂等前置、配置同步和受理结果编排。调用通用持久队列/协议组件，不通过Java Controller转发。"}
================================
import java.nio.charset.StandardCharsets
import java.util.UUID
// 业务主流程：认证身份 -> 通道权限 -> 表契约 -> 幂等/容量 -> 持久队列 -> 可查询回执。
import '@/haitong/common' as common
import '@/haitong/table-contract' as tableContract
import '@/haitong/registry' as registry
import '@/haitong/message' as message
import com.haitong.core.Models
import com.haitong.core.Rules
var c=common();var _bean=c.bean;var _fail=c.fail;var _text=c.text;var _now=c.now;var _list=c.list;var _object=c.object;var _key=c.key;var _hash=c.hash;var _absolute=c.absolute;;var caller=input.caller;var req=input.request
var catalog=_bean('routeCatalog');var ingress=_bean('ingressService');var files=_bean('fileService')
var json=c.json;var key=input.key;var raw=input.raw;var action=input.action
var receipt=accepted=>{
    req.setAttribute('haitong.messageId',accepted.id())
    var result=message({id:accepted.id(),caller:caller})
    var location=_absolute('/api/v1/messages/'+accepted.id())
    result.code=accepted.duplicate()?'ALREADY_ACCEPTED':'ACCEPTED';result.duplicate=accepted.duplicate();result.statusUrl=location
    return {status:accepted.duplicate() && result.completed?200:202,body:result,location:location}
}
if(action=='upload'){
    var channel=input.channel;Rules.code(channel)
    if(!caller.channel(channel)){_fail(403,'FORBIDDEN_CHANNEL','没有此数据通道的权限')}
    var file=input.file
    if(file==null){_fail(400,'INVALID_REQUEST','缺少file文件字段')}
    if(file.getSize()>c.p.getLimits().getMaxFileBytes()){_fail(413,'FILE_TOO_LARGE','文件超过限制')}
    var stream=file.getInputStream();var accepted=null
    try{accepted=files.upload(caller.id(),channel,key,_text(file.getSubmittedFileName())==''?'upload.bin':file.getSubmittedFileName(),stream)}finally{stream.close()}
    req.setAttribute('haitong.fileId',accepted.id());var url=_absolute('/api/v1/files/'+accepted.id())
    var result=message({action:'file',id:accepted.id(),caller:caller});result.duplicate=accepted.duplicate();result.statusUrl=url
    return {status:accepted.duplicate()?200:202,body:result,location:url}
}
if(action=='ingest'){
    Rules.code(input.channel)
    if(!caller.channel(input.channel)){_fail(403,'FORBIDDEN_CHANNEL','没有此数据通道的权限')}
    return receipt(ingress.http(caller.id(),input.channel,key,raw))
}
if(action=='action'){
    var code=input.code
    if(!caller.action(code)){_fail(403,'FORBIDDEN_ACTION','没有执行此操作的权限')}
    var config=catalog.actionConfig(code);var plan=catalog.action(code)
    if(config.getMode()=='ASYNC'){return receipt(ingress.action(caller.id(),key,raw,plan))}
    var id=key==null?UUID.randomUUID().toString():_key(key)
    var stable='sync-'+_hash((caller.id()+'|'+code+'|'+id).getBytes(StandardCharsets.UTF_8))
    var started=_now();var ledger=_bean('exchangeLog');var ctx={startedAt:started,attemptId:UUID.randomUUID().toString(),requestId:req.getAttribute('haitong.requestId'),owner:caller.id(),routeCode:'action.'+code,targetCode:code,destinationRef:code,sha256:_hash(raw),payloadBytes:raw.length,recordCount:1}
    ledger.append('HTTP','OUT','SYNC_ACTION','START','RUNNING',ctx)
    try{
        var result=_bean('actionConnector').execute(plan,raw,stable,caller.id());ctx.httpStatus=result.httpStatus;ctx.details={handler:plan.handler(),completion:result.completion || 'LOCAL_VALIDATION'};ctx.durationMs=Math.max(0,_now()-started)
        ledger.append('HTTP','OUT','SYNC_ACTION','FINISH','SUCCEEDED',ctx);return {status:200,body:result}
    }catch(e){var failure=e;while(failure.getCause()!=null){failure=failure.getCause()};ctx.errorCode=failure instanceof Models.DeliveryFailure?failure.code:'SYNC_ACTION_INTERRUPTED';ctx.durationMs=Math.max(0,_now()-started);ledger.append('HTTP','OUT','SYNC_ACTION','FINISH',failure instanceof Models.DeliveryFailure && failure.unknown?'UNKNOWN':'FAILED',ctx);throw e}
}
var body=input.body
var delivery=action=='data-push'?_text(body.deliveryType).trim().toUpperCase():action
if(delivery=='REGISTRY_SYNC'){
    var synced=registry({caller:caller,key:key,body:body,raw:raw})
    return {status:synced.duplicate?200:201,body:synced}
}
var routes={FTP:'table-to-ftp',KAFKA:'table-to-kafka',LOG:'table-to-log'};var channel=routes[delivery]
if(channel==null){_fail(422,delivery==''?'INVALID_BODY':'UNSUPPORTED_DELIVERY_TYPE','deliveryType仅支持FTP、KAFKA、LOG或REGISTRY_SYNC')}
if(!caller.channel(channel)){_fail(403,'FORBIDDEN_CHANNEL','没有此数据通道的权限')}
var config=c.p.getRoutes().get(channel)
if(config==null || !config.isEnabled()){_fail(503,'SCENE_NOT_READY',delivery+'场景尚未配置完成')}
if(body.containsKey('schemaVersion') || body.containsKey('occurredAt')){_fail(422,'INVALID_BODY','schemaVersion和occurredAt已取消，请从表批次请求移除')}
if(!(body.tableName instanceof String) || body.tableName.trim()=='' || body.tableName.length()>200){_fail(422,'INVALID_BODY','tableName必须是1至200字符的表名')}
var push=delivery!='LOG'
if(push && (!(body.datasourceId instanceof String) || !body.datasourceId.matches('[A-Za-z0-9_-]{1,64}'))){_fail(422,'INVALID_BODY','datasourceId必须是已登记数据源标识')}
var rows=_list(body.records)
if(rows.size()==0 || rows.size()>10000){_fail(422,'INVALID_BODY','records必须有1至10000条记录')}
for(row in rows){_object(row)}
// 先复核幂等正文。已受理消息不因登记版本更新而被再次执行。
var duplicate=ingress.existingHttp(caller.id(),channel,key,raw)
if(duplicate.isPresent()){return receipt(duplicate.get())}
var accepted=null
if(push){
    // Runtime contracts are local; destination connections come only from this deployment's YAML.
    var metadata=tableContract({batch:body,caller:caller})
    accepted=ingress.http(caller.id(),channel,key,raw,catalog.route(channel),metadata)
}else{accepted=ingress.http(caller.id(),channel,key,raw)}
return receipt(accepted)

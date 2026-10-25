{"id":"haitong-function-operations","groupId":"haitong-business-functions","name":"07.运维查询与状态处理","path":"/operations","parameters":[{"name":"input","dataType":"Object"}],"description":"管理员分页查询、可安全重试及UNKNOWN人工核实。规则、审计上限和事务SQL在脚本中；协议探测调用技术客户端。"}
================================
import java.util.Base64
import java.util.List
// 管理接口：保留原管理员边界，失败任务安全重试和UNKNOWN核实在行锁事务内执行。
import '@/haitong/common' as common
import '@/haitong/message' as message
import java.util.HashSet
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
var c=common();var _bean=c.bean;var _fail=c.fail;var _text=c.text;var _bounds=c.bounds;var _now=c.now;var _uuid=c.uuid;var _object=c.object;;var action=input.action;var p=c.p;var jdbc=c.jdbc
if(!input.caller.admin()){_fail(403,'FORBIDDEN','仅管理员可操作')}
if(action=='config'){
    var k=p.getKafka();var f=p.getFtp();var g=p.getGateway();var routes=[];var actions=[]
    for(code,config in p.getRoutes()){if(config.isEnabled()){routes.add({code:code,targets:config.getTargets(),fileFormat:config.getFileFormat()})}}
    for(code,config in p.getActions()){if(config.isEnabled()){actions.add({code:code,mode:config.getMode(),handler:config.getHandler()})}}
    return {editFile:'config/application.yml',restartRequired:true,kafka:{enabled:k.isEnabled(),bootstrapServers:k.getBootstrapServers(),producerTopic:k.getProducerTopic(),consumerEnabled:k.getConsumer().isEnabled(),consumerTopic:k.getConsumer().getTopic(),groupId:k.getConsumer().getGroupId()},ftp:{enabled:f.isEnabled(),protocol:f.getProtocol(),host:f.getHost(),port:f.getPort(),outputDir:f.getOutputDir(),scanEnabled:f.getScan().isEnabled(),inputDir:f.getScan().getInputDir()},gateway:{publicBaseUrl:g.getPublicBaseUrl(),additionalKeyRequired:g.getSharedKey()!=''},routes:routes,actions:actions}
}
if(action=='overview'){
    var counts={};for(row in jdbc.queryForList('SELECT status,COUNT(*) n FROM ht_delivery GROUP BY status')){counts[row.status]=row.n}
    var age=jdbc.queryForList("SELECT MIN(created_at) first_time FROM ht_delivery WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')")[0].first_time
    var pending=jdbc.queryForList("SELECT COUNT(*) n FROM ht_file_checkpoint WHERE status IN ('READY','PROCESSING','RETRY_WAIT')")[0].n
    var now=_now();return {deliveryCounts:counts,oldestPendingAgeMs:age==null?0:Math.max(0,now-age::long),pendingFiles:pending,databaseTime:now,workDirUsableBytes:c.http.usableBytes(p.getWorkDir())}
}
if(action=='deliveries' || action=='quarantined'){
    _bounds(input.limit,input.offset,200);var sql='';var args=[]
    if(action=='deliveries'){
        sql='SELECT id,message_id,target_code,target_type,status,attempts,max_attempts,next_attempt_at,last_error_code FROM ht_delivery'
        var state=_text(input.status)
        if(state!=''){if(!['PENDING','RUNNING','RETRY_WAIT','FAILED','UNKNOWN','SUCCEEDED'].contains(state)){_fail(422,'INVALID_BODY','未知状态')}sql+=' WHERE status=?';args.add(state)}
        sql+=' ORDER BY created_at DESC,id'
    }else{sql="SELECT id,route_code,source_type,error_code,created_at FROM ht_message WHERE status='QUARANTINED' ORDER BY created_at DESC,id"}
    var rows=jdbc.queryForList(c.dialect.pageSql(sql),c.dialect.pageArgs(input.limit::int,input.offset::int,args.toArray()))
    if(action=='quarantined'){return rows.map(r=>({messageId:r.id,channel:r.route_code,sourceType:r.source_type,errorCode:r.error_code,createdAt:r.created_at}))}
    return rows.map(r=>({deliveryId:r.id,messageId:r.message_id,targetCode:r.target_code,type:r.target_type,status:r.status,attempts:r.attempts,maxAttempts:r.max_attempts,nextAttemptAt:r.next_attempt_at,errorCode:r.last_error_code}))
}
if(action=='files'){return message({action:'file-list',caller:input.caller,limit:input.limit,offset:input.offset})}
if(action=='scan'){
    if(input.route!=p.getFtp().getScan().getChannel()){_fail(404,'RESOURCE_NOT_FOUND','资源不存在')}
    return _bean('fileService').scan()
}
if(action=='health'){
    var catalog=_bean('routeCatalog')
    if(input.name=='database'){return {connector:'database',status:'UP',time:_now()}}
    if(input.name=='ftp'){return p.getFtp().isEnabled()?_bean('ftpConnector').health(catalog.ftp()):{connector:'ftp',status:'DISABLED'}}
    if(input.name=='kafka'){
        if(!p.getKafka().isEnabled()){return {connector:'kafka',status:'DISABLED'}}
        var topics=new HashSet()
        for(code,config in p.getRoutes()){if(config.isEnabled()){for(target in catalog.route(code).targets()){if(target.kind().name()=='KAFKA'){topics.add(target.topic())}}}}
        if(p.getKafka().getConsumer().isEnabled()){topics.add(p.getKafka().getConsumer().getTopic())}
        return _bean('kafkaConnector').health(catalog.kafka(),topics)
    }
    _fail(404,'RESOURCE_NOT_FOUND','资源不存在')
}
if(action=='openapi'){return ResponseEntity.ok().contentType(MediaType.valueOf('application/yaml')).body(new ClassPathResource('openapi.yaml'))}
if(action=='payload'){
    var rows=jdbc.queryForList('SELECT payload_ref,payload FROM ht_message WHERE id=?',[input.id].toArray())
    if(rows.size()==0){_fail(404,'RESOURCE_NOT_FOUND','资源不存在')}
    var row=rows[0];var resource=null
    if(_text(row.payload_ref)!=''){resource=new FileSystemResource(c.http.safePath(p.getWorkDir(),row.payload_ref))}
    else{if(row.payload==null){_fail(410,'PAYLOAD_PURGED','原文已按保留策略清理，去重记录仍保留')}resource=new ByteArrayResource(Base64.getDecoder().decode(row.payload))}
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header('Content-Disposition','attachment; filename=message-'+_uuid(input.id)+'.bin').body(resource)
}
var body=_object(input.body);var reason=_text(body.reason).trim()
if(reason.length()<5 || reason.length()>1000){_fail(422,'INVALID_BODY','reason必填，长度5至1000')}
var decision=_text(body.decision);var evidence=_text(body.evidence).trim()
if(action=='resolve' && (!['MARK_SUCCEEDED','RETRY_CONFIRMED'].contains(decision) || evidence.length()<5 || evidence.length()>2000)){_fail(422,'INVALID_BODY','必须给出决策和核实证据')}
return c.tx.execute(status=>{
    var table=action=='retry-file'?'ht_file_checkpoint':'ht_delivery'
    var rows=jdbc.queryForList('SELECT * FROM '+table+' WHERE id=? FOR UPDATE',[input.id].toArray())
    if(rows.size()==0){_fail(404,'RESOURCE_NOT_FOUND','资源不存在')}
    var row=rows[0];var audit=c.json.read(row.retry_audit,List)
    if(audit.size()>=200){_fail(409,'AUDIT_LIMIT','已超过人工处理次数限制，请核查根因')}
    var operation=action=='retry-file'?'FILE_RETRY':action=='retry'?'RETRY':decision;var now=_now()
    audit.add({at:now,actor:'admin',operation:operation,reason:reason,evidence:action=='resolve'?evidence:''})
    var newState='RETRY_WAIT';var changed=0
    if(action=='retry'){
        changed=jdbc.update("UPDATE ht_delivery SET status='RETRY_WAIT',max_attempts=attempts+10,next_attempt_at=?,last_error_code=NULL,last_error_message=NULL,retry_audit=?,updated_at=? WHERE id=? AND status='FAILED' AND retry_safe=1",[now,c.json.write(audit),now,input.id].toArray())
        if(changed!=1){_fail(409,'RETRY_NOT_ALLOWED','仅允许重试可安全重试的FAILED任务')}
    }else if(action=='resolve'){
        var succeeded=decision=='MARK_SUCCEEDED';newState=succeeded?'SUCCEEDED':'RETRY_WAIT'
        changed=jdbc.update("UPDATE ht_delivery SET status=?,max_attempts=attempts+10,next_attempt_at=?,retry_audit=?,result_metadata=?,last_error_code=NULL,last_error_message=NULL,completed_at=?,updated_at=? WHERE id=? AND status='UNKNOWN'",[newState,now,c.json.write(audit),c.json.write({manualResolution:decision,evidence:evidence}),succeeded?now:null,now,input.id].toArray())
        if(changed!=1){_fail(409,'RESOLVE_NOT_ALLOWED','仅UNKNOWN任务需要人工核实')}
    }else if(action=='retry-file'){
        changed=jdbc.update("UPDATE ht_file_checkpoint SET status='RETRY_WAIT',attempts=0,next_attempt_at=?,retry_audit=?,last_error_code=NULL WHERE id=? AND status='FAILED'",[now,c.json.write(audit),input.id].toArray())
        if(changed!=1){_fail(409,'RETRY_NOT_ALLOWED','仅失败文件可以补跑')}
    }else{_fail(404,'RESOURCE_NOT_FOUND','操作不存在')}
    // 审计与状态更新在同一事务，记录失败则一起回滚；无外部网络I/O持锁。
    var ledger=_bean('exchangeLog');var ctx={owner:'admin',details:{reason:reason,evidence:evidence,operation:operation}}
    if(action=='retry-file'){ctx.fileId=input.id;ctx.routeCode=row.route_code;ledger.append('HTTP','ADMIN','FILE_RETRY','RESULT','RETRY_REQUESTED',ctx)}
    else{ctx.deliveryId=input.id;ctx.messageId=row.message_id;ctx.targetCode=row.target_code;ledger.append('HTTP','ADMIN','MANUAL_OPERATION','RESULT',action=='retry'?'MANUAL_RETRY':decision,ctx)}
    return action=='retry-file'?{fileId:input.id,status:newState}:{deliveryId:input.id,status:newState}
})

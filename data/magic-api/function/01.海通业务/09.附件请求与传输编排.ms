{"id":"haitong-function-files","groupId":"haitong-business-functions","name":"09.附件请求与传输编排","path":"/files","parameters":[{"name":"input","dataType":"Object"}],"description":"附件请求契约、授权范围、批量状态汇总及HTTP编排。二进制流、分片租约、配额与后台合并校验使用持久传输内核。"}
================================
import '@/haitong/common' as common
import com.haitong.files.FileUploadService
import com.haitong.core.Rules
import java.util.HashSet
var c=common();var _bean=c.bean;var _fail=c.fail;var _required=c.required;var _integer=c.integer;var _query=c.query;var _bounds=c.bounds;var _unknown=c.unknown;var _media=c.media;var _list=c.list;var _object=c.object;;var req=input.request;var caller=input.caller;var action=input.action
var service=null
try{service=_bean('fileUploadService')}catch(ignored){_fail(404,'RESOURCE_NOT_FOUND','附件传输未启用')}
var repo=_bean('fileUploadRepository');var catalog=_bean('fileContractCatalog')
var optional=(body,key)=>{var value=body[key];if(value!=null && !(value instanceof String)){_fail(422,'INVALID_REQUEST','字段必须为字符串：'+key)}return value}
var basic=u=>{
    var result={uploadId:u.id(),fileId:u.id(),datasourceId:u.text('datasource_id'),collectionId:u.text('collection_id'),fileName:u.text('file_name'),size:u.size(),mediaType:u.text('media_type'),sha256:u.text('final_sha256'),partSize:u.number('part_size'),partCount:u.number('part_count'),state:u.state(),securityState:u.text('security_state'),contractVersion:u.number('contract_version'),messageId:u.text('message_id'),errorCode:u.text('error_code'),expiresAt:u.number('expires_at'),statusUrl:'/api/v1/file-uploads/'+u.id()}
    if(u.state()=='READY'){result.contentUrl='/api/v1/file-uploads/'+u.id()+'/content'}
    return result
}
var decorate=rows=>{
    var progress=repo.progress(rows);var deliveries=repo.deliveries(rows);var result=[]
    for(u in rows){
        var value=basic(u);var counts=progress[u.id()];value.putAll(counts==null?{completedParts:0L,confirmedBytes:0L}:counts)
        var targets=deliveries[u.id()];value.deliveries=targets==null?[]:targets
        value.deliveryStatus=u.text('published_at')==''?(u.text('message_id')==''?'NOT_SUBMITTED':'WAITING_FILE'):Rules.aggregate(value.deliveries.map(d=>d.status))
        result.add(value)
    }
    return result
}
var key=req.getHeader('Idempotency-Key')
if(action=='content'){
    var u=service.readable(caller,input.id)
    if(u.state()!='READY'){_fail(409,'FILE_NOT_READY','文件尚未就绪')}
    var storage=_bean('fileStorage');storage.acquire()
    try{return c.http.stream(c.p.getWorkDir()+'/unstructured',u.text('payload_path'),u.text('file_name'),u.text('media_type'),'"'+u.text('final_sha256')+'"',req,input.response)}finally{storage.release()}
}
if(action=='page'){
    var limit=_integer(_query(req,'limit','20'),20);var offset=_integer(_query(req,'offset','0'),0);_bounds(limit,offset,100)
    var page=repo.page(caller,limit::int,offset::int)
    return c.http.reply(200,{total:page.total,rows:decorate(page.rows),limit:limit,offset:offset})
}
if(action=='status' || action=='batch'){
    var ids=[input.id]
    if(action=='batch'){
        _media(req,'application/json');var body=c.http.object(req,65536);_unknown(body,['uploadIds']);ids=_list(body.uploadIds)
        for(id in ids){if(!(id instanceof String)){_fail(422,'INVALID_REQUEST','uploadIds必须为字符串数组')}}
    }
    if(ids.size()==0 || ids.size()>100 || new HashSet(ids).size()!=ids.size()){_fail(422,'INVALID_REQUEST','请提供1至100个不重复的上传ID')}
    // 一次按ID和当前服务器授权范围查询；其中任意ID不可访问时整批拒绝，不扩展租户范围。
    var rows=repo.authorized(caller,ids)
    if(rows.size()!=ids.size()){_fail(404,'RESOURCE_NOT_FOUND','资源不存在')}
    var values=decorate(rows)
    return c.http.reply(200,action=='status'?values[0]:values)
}
if(action=='single'){
    _media(req,'application/octet-stream')
    var allowed=['datasourceId','tableName','fieldName','fileName']
    for(name,values in req.getParameterMap()){if(!allowed.contains(name) || values.length!=1){_fail(422,'INVALID_REQUEST','请求包含未定义或重复参数')}}
    var source=_required(_query(req,'datasourceId',null),'datasourceId');var table=_required(_query(req,'tableName',null),'tableName');var field=_required(_query(req,'fieldName',null),'fieldName');var name=_required(_query(req,'fileName',null),'fileName')
    return c.http.reply(202,service.single(caller,key,new FileUploadService.Single(source,table,field,name),req.getContentLengthLong(),req.getInputStream()))
}
if(action=='part'){
    _media(req,'application/octet-stream')
    return c.http.reply(200,service.putPart(caller,input.id,input.part,req.getHeader('X-Part-SHA256'),req.getContentLengthLong(),req.getInputStream()))
}
if(action=='parts'){
    var after=_integer(_query(req,'after','0'),0);var limit=_integer(_query(req,'limit','100'),100)
    if(after<0 || limit<1 || limit>100){_fail(422,'INVALID_REQUEST','分片分页参数不正确')}
    service.readable(caller,input.id)
    return c.http.reply(200,{uploadId:input.id,parts:repo.partPage(input.id,after::int,limit::int)})
}
if(action=='init'){
    _media(req,'application/json');var body=c.http.object(req,65536);_unknown(body,['datasourceId','collectionId','fileName','size','sha256'])
    var size=_integer(body.size,-1)
    return c.http.reply(202,service.initialize(caller,key,new FileUploadService.Init(_required(body.datasourceId,'datasourceId'),_required(body.collectionId,'collectionId'),_required(body.fileName,'fileName'),size,optional(body,'sha256'))))
}
if(action=='submit'){
    _media(req,'application/json');var body=c.http.object(req,65536);_unknown(body,['businessKey','metadata'])
    return c.http.reply(202,service.submit(caller,input.id,key,new FileUploadService.Submission(_required(body.businessKey,'businessKey'),_object(body.metadata))))
}
if(action=='receipt'){
    _media(req,'application/json');var body=c.http.object(req,65536);_unknown(body,['deliveryId','sha256','status','businessRef'])
    return c.http.reply(200,service.receipt(caller,input.id,key,new FileUploadService.Receipt(_required(body.deliveryId,'deliveryId'),_required(body.sha256,'sha256'),_required(body.status,'status'),optional(body,'businessRef'))))
}
if(action=='complete'){return c.http.reply(202,service.complete(caller,input.id))}
if(action=='abort'){return c.http.reply(200,service.abort(caller,input.id))}
_fail(404,'RESOURCE_NOT_FOUND','接口操作不存在')

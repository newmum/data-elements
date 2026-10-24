{"id":"haitong-function-message","groupId":"haitong-business-functions","name":"03.消息状态与文件统计","path":"/message","parameters":[{"name":"input","dataType":"Object"}],"description":"消息归属校验、投递结果脱敏和文件统计；列表统计按本页ID批量聚合，不逐行补查。"}
================================
import java.lang.Object
// 功能：统一消息状态和旧文件检查点状态；普通调用方只能查询自己的消息。
import '@/haitong/common' as common
import com.haitong.core.Rules
var c=common();var _bean=c.bean;var _fail=c.fail;var _bounds=c.bounds;
var aggregate=values=>Rules.aggregate(values)
if(input.action=='file-list' || input.action=='file'){
    var rows=[]
    if(input.action=='file'){
        rows=c.jdbc.queryForList('SELECT * FROM ht_file_checkpoint WHERE id=?',[input.id].toArray())
        if(rows.size()==0 || (!input.caller.admin() && rows[0].owner!=input.caller.id())){_fail(404,'RESOURCE_NOT_FOUND','资源不存在')}
    }else{
        _bounds(input.limit,input.offset,200)
        rows=c.jdbc.queryForList(c.dialect.pageSql('SELECT * FROM ht_file_checkpoint ORDER BY created_at DESC,id'),c.dialect.pageArgs(input.limit::int,input.offset::int))
    }
    if(rows.size()==0){return []}
    var ids=rows.map(r=>r.id);var marks=ids.map(id=>'?').join(',');var stats={}
    for(id in ids){stats[id]={recordsAccepted:0L,quarantined:0L}}
    var totals=c.jdbc.queryForList("SELECT file_id,COUNT(*) n,SUM(CASE WHEN status='QUARANTINED' THEN 1 ELSE 0 END) bad FROM ht_message WHERE file_id IN ("+marks+") GROUP BY file_id",ids.toArray())
    var states=c.jdbc.queryForList('SELECT m.file_id,d.status,COUNT(*) n FROM ht_delivery d JOIN ht_message m ON d.message_id=m.id WHERE m.file_id IN ('+marks+') GROUP BY m.file_id,d.status',ids.toArray())
    for(row in totals){stats[row.file_id].recordsAccepted=row.n;stats[row.file_id].quarantined=row.bad}
    for(row in states){stats[row.file_id][row.status]=row.n}
    var result=[]
    for(row in rows){
        var counts=stats[row.id];var state=row.status;var eof=row.eof_reached::int==1
        if(eof){var bad=(counts.quarantined || 0)+(counts.FAILED || 0)+(counts.UNKNOWN || 0);var pending=(counts.PENDING || 0)+(counts.RUNNING || 0)+(counts.RETRY_WAIT || 0);state=bad>0?'PARTIAL_FAILURE':pending>0?'INGESTED':'COMPLETED'}
        result.add({fileId:row.id,fileName:row.original_name,status:state,lastRecordNo:row.last_record_no,totalRecords:eof?row.total_records:null,eof:eof,counts:counts,errorCode:row.last_error_code})
    }
    return input.action=='file'?result[0]:result
}
var rows=c.jdbc.queryForList('SELECT id,owner,route_code,status,error_code FROM ht_message WHERE id=?',[input.id].toArray())
if(rows.size()==0){
    var service=null
    try{service=_bean('fileUploadService')}catch(ignored){}
    if(service!=null){var pending=service.pendingMessage(input.caller,input.id);if(pending.isPresent()){return pending.get()}}
    _fail(404,'RESOURCE_NOT_FOUND','资源不存在')
}
var row=rows[0]
if(!input.caller.admin() && row.owner!=input.caller.id()){_fail(404,'RESOURCE_NOT_FOUND','资源不存在')}
var deliveries=c.jdbc.queryForList('SELECT id,target_code,target_type,status,attempts,last_error_code,result_metadata FROM ht_delivery WHERE message_id=? ORDER BY target_code',[input.id].toArray())
var states=deliveries.map(d=>d.status);var output=[]
for(d in deliveries){
    var value=null
    if(d.result_metadata!=null){
        var full=c.json.read(d.result_metadata,Object)
        if(input.caller.admin()){value=full}else{value={};for(k in ['completion','httpStatus','fileName','sha256','size','bytes','partition','offset']){if(full.containsKey(k)){value[k]=full[k]}}}
    }
    output.add({deliveryId:d.id,targetCode:d.target_code,type:d.target_type,status:d.status,attempts:d.attempts,errorCode:d.last_error_code,result:value})
}
return {messageId:row.id,channel:row.route_code,status:row.status=='QUARANTINED'?'QUARANTINED':aggregate(states),completed:states.filter(s=>!Rules.terminal(s)).size()==0,errorCode:row.error_code,deliveries:output}

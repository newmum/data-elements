{"id":"haitong-function-reconciliation","groupId":"haitong-business-functions","name":"08.交换台账与对账汇总","path":"/reconciliation","parameters":[{"name":"input","dataType":"Object"}],"description":"管理员按UTC毫秒左闭右开窗口查询本地传输台账，最大31天；汇总SQL固定3次，传输确认与业务回执明确区分。"}
================================
// 只读：按消息受理时间聚合，不将FTP/Kafka传输成功当作下游业务入库成功。
import '@/haitong/common' as common
var c=common();var _fail=c.fail;var _text=c.text;var _integer=c.integer;var _bounds=c.bounds;var _now=c.now;var _uuid=c.uuid;
if(!input.caller.admin()){_fail(403,'FORBIDDEN','仅管理员可操作')}
var from=_integer(input.from,-1);var to=_integer(input.to,-1)
if(from<0 || to<=from || to-from>31L*86400000){_fail(422,'INVALID_BODY','时间窗口采用UTC毫秒，左闭右开，最长31天')}
if(input.action=='logs'){
    _bounds(input.limit,input.offset,200);var protocol=_text(input.protocol);var id=_text(input.messageId)
    if(protocol!='' && !['HTTP','FTP','KAFKA','LOCAL','LOG'].contains(protocol)){_fail(422,'INVALID_BODY','protocol不合法')}
    if(id!=''){_uuid(id)}
    var sql='SELECT * FROM ht_exchange_log WHERE created_at>=? AND created_at<?';var args=[from,to]
    if(protocol!=''){sql+=' AND protocol=?';args.add(protocol)}
    if(id!=''){sql+=' AND message_id=?';args.add(id)}
    sql+=' ORDER BY created_at DESC,id'
    return c.jdbc.queryForList(c.dialect.pageSql(sql),c.dialect.pageArgs(input.limit::int,input.offset::int,args.toArray()))
}
var inputs=c.jdbc.queryForList("""
    SELECT route_code,source_type,status,COUNT(*) n FROM ht_message
    WHERE created_at>=? AND created_at<?
    GROUP BY route_code,source_type,status ORDER BY route_code,source_type,status
    """,[from,to].toArray())
var targets=c.jdbc.queryForList("""
    SELECT m.route_code,d.target_code,d.target_type,COUNT(*) expected,
           SUM(CASE WHEN d.status='SUCCEEDED' THEN 1 ELSE 0 END) confirmed,
           SUM(CASE WHEN d.status='FAILED' THEN 1 ELSE 0 END) failed,
           SUM(CASE WHEN d.status='UNKNOWN' THEN 1 ELSE 0 END) uncertain,
           SUM(CASE WHEN d.status IN ('PENDING','RUNNING','RETRY_WAIT') THEN 1 ELSE 0 END) pending
    FROM ht_message m JOIN ht_delivery d ON d.message_id=m.id
    WHERE m.created_at>=? AND m.created_at<?
    GROUP BY m.route_code,d.target_code,d.target_type ORDER BY m.route_code,d.target_code
    """,[from,to].toArray())
var files=c.jdbc.queryForList("""
    SELECT route_code,content_format,status,COUNT(*) n FROM ht_file_checkpoint
    WHERE created_at>=? AND created_at<?
    GROUP BY route_code,content_format,status ORDER BY route_code,content_format,status
    """,[from,to].toArray())
return {scope:'LOCAL_TRANSPORT_LEDGER',businessReceiptCompared:false,provisional:true,windowFromInclusive:from,windowToExclusive:to,windowBasis:'ht_message.created_at UTC epoch milliseconds',generatedAt:_now(),
    inputs:inputs.map(r=>({route:r.route_code,sourceType:r.source_type,acceptance:r.status,messageCount:r.n})),
    targets:targets.map(r=>({route:r.route_code,target:r.target_code,type:r.target_type,expectedDeliveries:r.expected,transportConfirmed:r.confirmed,failed:r.failed,unknown:r.uncertain,pending:r.pending})),
    files:files.map(r=>({route:r.route_code,format:r.content_format,ingestionStatus:r.status,fileCount:r.n}))}

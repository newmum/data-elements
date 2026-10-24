{"id":"haitong-internal-14","groupId":"haitong-operations-api","name":"14.传输对账汇总","path":"/reconciliation/summary","method":"GET","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _query=c.query;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/reconciliation' as operation
var input={action:'summary',caller:caller}
input.from=_query(req,'from',null);input.to=_query(req,'to',null);input.protocol=_query(req,'protocol','');input.messageId=_query(req,'messageId','')
var result=operation(input)
return c.http.reply(200,result)

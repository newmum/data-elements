{"id":"haitong-internal-6","groupId":"haitong-operations-api","name":"06.导入文件分页","path":"/files","method":"GET","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _integer=c.integer;var _query=c.query;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/operations' as operation
var input={action:'files',caller:caller}
input.limit=_integer(_query(req,'limit','50'),50);input.offset=_integer(_query(req,'offset','0'),0)
input.status=_query(req,'status','')
var result=operation(input)
return c.http.reply(200,result)

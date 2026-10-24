{"id":"haitong-internal-7","groupId":"haitong-operations-api","name":"07.导入文件重试","path":"/files/{id}/retry","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _media=c.media;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/operations' as operation
var input={action:'retry-file',caller:caller}
input.id=path.id
_media(req,'application/json');input.body=c.http.object(req,65536)
var result=operation(input)
return c.http.reply(200,result)

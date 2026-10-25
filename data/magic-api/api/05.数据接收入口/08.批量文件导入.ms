{"id":"haitong-public-8","groupId":"haitong-ingress-api","name":"08.批量文件导入","path":"/files/{channel}","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _media=c.media;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/push' as push
_media(req,'multipart/form-data')
var result=push({action:'upload',caller:caller,request:req,channel:path.channel,key:req.getHeader('Idempotency-Key'),file:req.getFile('file')})
return result.location==null?c.http.reply(result.status::int,result.body):c.http.located(result.status::int,result.body,result.location)

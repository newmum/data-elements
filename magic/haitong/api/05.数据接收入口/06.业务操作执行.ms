{"id":"haitong-public-6","groupId":"haitong-ingress-api","name":"06.业务操作执行","path":"/actions/{code}","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _media=c.media;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/push' as push
_media(req,'application/json')
var raw=c.http.raw(req,c.p.getLimits().getMaxMessageBytes())
var result=push({action:'action',caller:caller,request:req,raw:raw,body:c.json.object(raw),key:req.getHeader('Idempotency-Key'),code:path.code})
return result.location==null?c.http.reply(result.status::int,result.body):c.http.located(result.status::int,result.body,result.location)

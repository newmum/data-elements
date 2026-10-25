{"id":"haitong-credential-issue","groupId":"haitong-credential-api","name":"01.业务密钥签发","path":"/issue","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _media=c.media;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/credentials' as issue
_media(req,'application/json')
return c.http.reply(200,{code:0,message:'success',data:issue({caller:caller,body:c.http.object(req,65536)})})

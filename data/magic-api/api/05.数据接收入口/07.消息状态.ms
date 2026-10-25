{"id":"haitong-public-7","groupId":"haitong-ingress-api","name":"07.消息状态","path":"/messages/{id}","method":"GET","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/message' as message
return c.http.reply(200,message({id:path.id,caller:caller}))

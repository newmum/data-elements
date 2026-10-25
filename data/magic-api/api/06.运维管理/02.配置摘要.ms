{"id":"haitong-internal-2","groupId":"haitong-operations-api","name":"02.配置摘要","path":"/config-summary","method":"GET","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/operations' as operation
var input={action:'config',caller:caller}
var result=operation(input)
return c.http.reply(200,result)

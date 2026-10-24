{"id":"haitong-internal-8","groupId":"haitong-operations-api","name":"08.主动FTP扫描","path":"/ftp-scans/{route}","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/operations' as operation
var input={action:'scan',caller:caller}
input.route=path.route
var result=operation(input)
return c.http.reply(200,result)

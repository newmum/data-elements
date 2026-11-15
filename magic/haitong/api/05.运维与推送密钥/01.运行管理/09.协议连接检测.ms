{
  "id": "haitong-internal-9",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "09.协议连接检测",
  "path": "/connectors/{name}/health",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"
}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/operations' as operation
var input={action:'health',caller:caller}
input.name=path.name
var result=operation(input)
return c.http.reply(200,result)

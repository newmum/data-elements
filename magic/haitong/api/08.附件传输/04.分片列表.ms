{"id": "haitong-files-4", "groupId": "haitong-files-api", "name": "04.分片列表", "path": "/file-uploads/{id}/parts", "method": "GET", "parameters": [], "options": [], "headers": [], "paths": [], "description": "海通正式接口。认证由统一过滤器执行；业务规则在海通业务函数中维护。保留原HTTP状态及JSON契约。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/files' as files
import response
var input={action:'parts',caller:caller,request:req,response:c.http.response()}
input.id=path.id
return files(input)

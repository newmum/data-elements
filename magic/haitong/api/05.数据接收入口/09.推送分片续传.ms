{"id": "haitong-file-push-parts", "groupId": "haitong-ingress-api", "name": "09.推送分片续传", "path": "/data-push/uploads/{id}/parts", "method": "GET", "parameters": [], "options": [], "headers": [], "paths": [], "description": "统一文件推送；复用持久上传、完整性校验、调用方授权及可靠投递。原file-uploads协议保持兼容。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;var _integer=c.integer;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/files' as files
var input={action:'parts',caller:caller,request:req,response:c.http.response(),id:path.id}
return files(input)

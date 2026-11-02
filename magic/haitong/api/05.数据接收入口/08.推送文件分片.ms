{"id": "haitong-file-push-part", "groupId": "haitong-ingress-api", "name": "08.推送文件分片", "path": "/data-push/uploads/{id}/parts/{part}", "method": "PUT", "parameters": [], "options": [], "headers": [], "paths": [], "description": "统一文件推送；复用持久上传、完整性校验、调用方授权及可靠投递。原file-uploads协议保持兼容。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;var _integer=c.integer;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/files' as files
var input={action:'part',caller:caller,request:req,response:c.http.response(),id:path.id,part:_integer(path.part,-1)::int}
return files(input)

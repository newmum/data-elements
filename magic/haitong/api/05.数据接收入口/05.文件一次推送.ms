{"id": "haitong-file-push-single", "groupId": "haitong-ingress-api", "name": "05.文件一次推送", "path": "/data-push", "method": "PUT", "parameters": [], "options": [], "headers": [], "paths": [], "description": "统一文件推送；复用持久上传、完整性校验、调用方授权及可靠投递。原file-uploads协议保持兼容。"}
================================
import '@/haitong/common' as common
import request
var c=common();var _caller=c.caller;var _integer=c.integer;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/files' as files
var input={action:'pushSingle',caller:caller,request:req,response:c.http.response()}
return files(input)

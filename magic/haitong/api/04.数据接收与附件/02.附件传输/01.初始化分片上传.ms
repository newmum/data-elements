{
  "id": "haitong-files-1",
  "groupId": "haitong-attachment-api",
  "name": "01.初始化分片上传",
  "path": "/init",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "登记附件并初始化分片会话，返回可续传的上传标识。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/file/init。登记附件并初始化分片会话，返回可续传的上传标识。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'init', caller: caller, request: req, response: context.http.response()}

return files(input)

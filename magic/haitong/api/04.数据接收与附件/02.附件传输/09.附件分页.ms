{
  "id": "haitong-files-9",
  "groupId": "haitong-attachment-api",
  "name": "09.附件分页",
  "path": "/page",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "按当前调用方的租户和附件权限分页读取。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// GET /api/file/page。按当前调用方的租户和附件权限分页读取。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'page', caller: caller, request: req, response: context.http.response()}

return files(input)

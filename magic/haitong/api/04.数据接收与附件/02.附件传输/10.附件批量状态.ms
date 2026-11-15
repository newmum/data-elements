{
  "id": "haitong-files-10",
  "groupId": "haitong-attachment-api",
  "name": "10.附件批量状态",
  "path": "/batch",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "按一组上传标识查询状态，对每个对象校验范围。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/file/batch。按一组上传标识查询状态，对每个对象校验范围。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'batch', caller: caller, request: req, response: context.http.response()}

return files(input)

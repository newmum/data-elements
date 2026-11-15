{
  "id": "haitong-files-2",
  "groupId": "haitong-attachment-api",
  "name": "02.单次附件上传",
  "path": "/single",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "按已登记的表和字段接收整文件原始字节。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/file/single。按已登记的表和字段接收整文件原始字节。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'single', caller: caller, request: req, response: context.http.response()}

return files(input)

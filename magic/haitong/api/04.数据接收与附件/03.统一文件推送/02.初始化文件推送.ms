{
  "id": "haitong-file-push-init",
  "groupId": "haitong-unified-file-push",
  "name": "02.初始化文件推送",
  "path": "/init",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "按已登记的文件类别初始化上传会话，返回分片大小和数量。 复用 /haitong/files 的调用方授权及传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/push/init。按已登记的文件类别初始化上传会话，返回分片大小和数量。
// 由 /haitong/files 与其他文件入口共用权限与传输流程。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'pushInit', caller: caller, request: req, response: context.http.response()}

return files(input)

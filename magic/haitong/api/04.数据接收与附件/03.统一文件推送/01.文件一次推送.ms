{
  "id": "haitong-file-push-single",
  "groupId": "haitong-unified-file-push",
  "name": "01.文件一次推送",
  "path": "/single",
  "method": "PUT",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "接收 multipart 文件和业务参数，校验并登记异步投递。 复用 /haitong/files 的调用方授权及传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// PUT /api/push/single。接收 multipart 文件和业务参数，校验并登记异步投递。
// 由 /haitong/files 与其他文件入口共用权限与传输流程。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'pushSingle', caller: caller, request: req, response: context.http.response()}

return files(input)

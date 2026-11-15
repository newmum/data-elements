{
  "id": "haitong-file-push-status",
  "groupId": "haitong-unified-file-push",
  "name": "06.文件推送结果",
  "path": "/status",
  "method": "GET",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "读取调用方有权限查看的上传与投递结果。 复用 /haitong/files 的调用方授权及传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// GET /api/push/status。读取调用方有权限查看的上传与投递结果。
// 由 /haitong/files 与其他文件入口共用权限与传输流程。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'pushStatus', caller: caller, request: req, response: context.http.response()}
var requireValue = context.required
input.id = requireValue(req.getParameter('id'), 'id')

return files(input)

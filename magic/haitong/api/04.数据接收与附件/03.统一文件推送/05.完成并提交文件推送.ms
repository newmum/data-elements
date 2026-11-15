{
  "id": "haitong-file-push-complete",
  "groupId": "haitong-unified-file-push",
  "name": "05.完成并提交文件推送",
  "path": "/complete",
  "method": "POST",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "登记合并及投递意图，文件就绪后异步提交。 复用 /haitong/files 的调用方授权及传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/push/complete。登记合并及投递意图，文件就绪后异步提交。
// 由 /haitong/files 与其他文件入口共用权限与传输流程。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'pushComplete', caller: caller, request: req, response: context.http.response()}
var requireValue = context.required
input.id = requireValue(req.getParameter('id'), 'id')

return files(input)

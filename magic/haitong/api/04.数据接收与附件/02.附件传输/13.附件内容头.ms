{
  "id": "haitong-files-13",
  "groupId": "haitong-attachment-api",
  "name": "13.附件内容头",
  "path": "/content",
  "method": "HEAD",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "校验当前调用方权限后返回与下载对应的响应头。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// HEAD /api/file/content。校验当前调用方权限后返回与下载对应的响应头。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'content', caller: caller, request: req, response: context.http.response()}
var requireValue = context.required
input.id = requireValue(req.getParameter('id'), 'id')

return files(input)

{
  "id": "haitong-file-push-part",
  "groupId": "haitong-unified-file-push",
  "name": "03.推送文件分片",
  "path": "/part",
  "method": "PUT",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}, {"name": "part", "required": false, "dataType": "String", "description": "必填查询参数 part"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "写入指定分片并校验原始字节及 SHA-256。 复用 /haitong/files 的调用方授权及传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// PUT /api/push/part。写入指定分片并校验原始字节及 SHA-256。
// 由 /haitong/files 与其他文件入口共用权限与传输流程。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'part', caller: caller, request: req, response: context.http.response()}
var requireValue = context.required
input.id = requireValue(req.getParameter('id'), 'id')
var parseInteger = context.integer
input.part = parseInteger(req.getParameter('part'), -1)::int

return files(input)

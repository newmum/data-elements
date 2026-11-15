{
  "id": "haitong-files-3",
  "groupId": "haitong-attachment-api",
  "name": "03.写入分片",
  "path": "/part",
  "method": "PUT",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}, {"name": "part", "required": false, "dataType": "String", "description": "必填查询参数 part"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "向现有会话写入指定分片，校验大小和 SHA-256。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// PUT /api/file/part。向现有会话写入指定分片，校验大小和 SHA-256。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

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

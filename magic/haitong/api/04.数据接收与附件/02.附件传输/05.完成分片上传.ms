{
  "id": "haitong-files-5",
  "groupId": "haitong-attachment-api",
  "name": "05.完成分片上传",
  "path": "/complete",
  "method": "POST",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "申请合并和校验已上传的全部分片。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/file/complete。申请合并和校验已上传的全部分片。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'complete', caller: caller, request: req, response: context.http.response()}
var requireValue = context.required
input.id = requireValue(req.getParameter('id'), 'id')

return files(input)

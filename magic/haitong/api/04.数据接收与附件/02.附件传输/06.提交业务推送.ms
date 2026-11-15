{
  "id": "haitong-files-6",
  "groupId": "haitong-attachment-api",
  "name": "06.提交业务推送",
  "path": "/submit",
  "method": "POST",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "登记业务键和元数据，等待就绪后投递附件。 沿用 /haitong/files 的附件授权和传输流程。"
}
================================
import '@/haitong/common' as common
import '@/haitong/files' as files
import request

// POST /api/file/submit。登记业务键和元数据，等待就绪后投递附件。
// 与其他附件入口共用 /haitong/files 的权限校验和传输逻辑。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var input = {action: 'submit', caller: caller, request: req, response: context.http.response()}
var requireValue = context.required
input.id = requireValue(req.getParameter('id'), 'id')

return files(input)

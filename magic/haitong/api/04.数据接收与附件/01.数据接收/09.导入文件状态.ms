{
  "id": "haitong-public-9",
  "groupId": "haitong-files-api",
  "name": "09.导入文件状态",
  "path": "/file",
  "method": "GET",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "按文件 ID 查询当前调用方可见的历史导入状态。 复用 /haitong/message 查询权限。"
}
================================
import '@/haitong/common' as common
import '@/haitong/message' as message
import request

// GET /api/v1/file。按文件 ID 查询当前调用方可见的历史导入状态。
// 与同组入口共用 /haitong/message 的调用方授权及业务处理。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var requireValue = context.required
var id = requireValue(req.getParameter('id'), 'id')
return context.http.reply(200, message({action: 'file', id: id, caller: caller}))

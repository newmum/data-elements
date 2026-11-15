{
  "id": "haitong-internal-7",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "07.导入文件重试",
  "path": "/fileretry",
  "method": "POST",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。按文件 ID 重试失败文件，请求体包含 reason。与同组入口共用 /haitong/operations。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/operations' as operation

// POST /internal/v1/fileretry。仅管理员。按文件 ID 重试失败文件，请求体包含 reason。
// 由 /haitong/operations 与同组入口共用权限及业务处理。

var commonContext = common()
var checkMedia = commonContext.media
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'retry-file', caller: caller}
var requireValue = commonContext.required
// ID 来自查询参数，业务函数仍按原对象范围与状态处理。
input.id = requireValue(req.getParameter('id'), 'id')
// 管理员写操作保留原 JSON 请求体和大小限制。
checkMedia(req, 'application/json')
input.body = commonContext.http.object(req, 65536)
var result = operation(input)
return commonContext.http.reply(200, result)

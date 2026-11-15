{
  "id": "haitong-internal-9",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "09.协议连接检测",
  "path": "/health",
  "method": "GET",
  "parameters": [{"name": "name", "required": false, "dataType": "String", "description": "必填查询参数 name"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。按 name 检查指定协议连接，支持 database、ftp 和 kafka。与同组入口共用 /haitong/operations。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/operations' as operation

// GET /internal/v1/health。仅管理员。按 name 检查指定协议连接，支持 database、ftp 和 kafka。
// 由 /haitong/operations 与同组入口共用权限及业务处理。

var commonContext = common()
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'health', caller: caller}
var requireValue = commonContext.required
input.name = requireValue(req.getParameter('name'), 'name')
var result = operation(input)
return commonContext.http.reply(200, result)

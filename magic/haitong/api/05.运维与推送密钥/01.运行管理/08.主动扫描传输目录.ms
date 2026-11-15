{
  "id": "haitong-internal-8",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "08.主动扫描传输目录",
  "path": "/scan",
  "method": "POST",
  "parameters": [{"name": "route", "required": false, "dataType": "String", "description": "必填查询参数 route"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。扫描已启用的 FTP 传输目录，route 为服务端已配置通道。与同组入口共用 /haitong/operations。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/operations' as operation

// POST /internal/v1/scan。仅管理员。扫描已启用的 FTP 传输目录，route 为服务端已配置通道。
// 由 /haitong/operations 与同组入口共用权限及业务处理。

var commonContext = common()
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'scan', caller: caller}
var requireValue = commonContext.required
input.route = requireValue(req.getParameter('route'), 'route')
var result = operation(input)
return commonContext.http.reply(200, result)

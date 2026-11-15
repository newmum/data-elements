{
  "id": "haitong-internal-13",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "13.交换日志分页",
  "path": "/logs",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。按 UTC 毫秒区间及可选筛选项分页读取交换日志。与同组入口共用 /haitong/reconciliation。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/reconciliation' as operation

// GET /internal/v1/logs。仅管理员。按 UTC 毫秒区间及可选筛选项分页读取交换日志。
// 由 /haitong/reconciliation 与同组入口共用权限及业务处理。

var commonContext = common()
var parseInteger = commonContext.integer
var readQuery = commonContext.query
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'logs', caller: caller}
input.limit = parseInteger(readQuery(req, 'limit', '50'), 50)
input.offset = parseInteger(readQuery(req, 'offset', '0'), 0)
input.status = readQuery(req, 'status', '')
input.from = readQuery(req, 'from', null)
input.to = readQuery(req, 'to', null)
input.protocol = readQuery(req, 'protocol', '')
input.messageId = readQuery(req, 'messageId', '')
var result = operation(input)
return commonContext.http.reply(200, result)

{
  "id": "haitong-internal-14",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "14.传输对账汇总",
  "path": "/reconcile",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。按 UTC 毫秒区间及可选筛选项汇总传输对账。与同组入口共用 /haitong/reconciliation。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/reconciliation' as operation

// GET /internal/v1/reconcile。仅管理员。按 UTC 毫秒区间及可选筛选项汇总传输对账。
// 由 /haitong/reconciliation 与同组入口共用权限及业务处理。

var commonContext = common()
var readQuery = commonContext.query
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'summary', caller: caller}
input.from = readQuery(req, 'from', null)
input.to = readQuery(req, 'to', null)
input.protocol = readQuery(req, 'protocol', '')
input.messageId = readQuery(req, 'messageId', '')
var result = operation(input)
return commonContext.http.reply(200, result)

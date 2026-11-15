{
  "id": "haitong-internal-11",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "11.消息原文下载",
  "path": "/payload",
  "method": "GET",
  "parameters": [{"name": "id", "required": false, "dataType": "String", "description": "必填查询参数 id"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。按消息 ID 返回二进制原文及附件响应头。与同组入口共用 /haitong/operations。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/operations' as operation

// GET /internal/v1/payload。仅管理员。按消息 ID 返回二进制原文及附件响应头。
// 由 /haitong/operations 与同组入口共用权限及业务处理。

var commonContext = common()
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'payload', caller: caller}
var requireValue = commonContext.required
// ID 来自查询参数，业务函数仍按原对象范围与状态处理。
input.id = requireValue(req.getParameter('id'), 'id')
var result = operation(input)
return result

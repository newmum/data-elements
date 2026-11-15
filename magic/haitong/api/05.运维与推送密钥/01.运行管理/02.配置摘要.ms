{
  "id": "haitong-internal-2",
  "groupId": "bec8576a6f0c5793aac91b6f24574747",
  "name": "02.配置摘要",
  "path": "/config",
  "method": "GET",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "仅管理员。读取脱敏运行配置，返回通道、目标和动作摘要。与同组入口共用 /haitong/operations。"
}
================================
import '@/haitong/common' as common
import request
import '@/haitong/operations' as operation

// GET /internal/v1/config。仅管理员。读取脱敏运行配置，返回通道、目标和动作摘要。
// 由 /haitong/operations 与同组入口共用权限及业务处理。

var commonContext = common()
var resolveCaller = commonContext.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)

var input = {action: 'config', caller: caller}
var result = operation(input)
return commonContext.http.reply(200, result)

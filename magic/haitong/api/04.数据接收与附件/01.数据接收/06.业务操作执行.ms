{
  "id": "haitong-public-6",
  "groupId": "haitong-files-api",
  "name": "06.业务操作执行",
  "path": "/action",
  "method": "POST",
  "parameters": [{"name": "code", "required": false, "dataType": "String", "description": "必填查询参数 code"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "执行当前调用方有权限使用的已登记业务操作。 复用 /haitong/push 的鉴权、幂等和接收处理。"
}
================================
import '@/haitong/common' as common
import '@/haitong/push' as push
import request

// POST /api/v1/action。执行当前调用方有权限使用的已登记业务操作。
// 与同组入口共用 /haitong/push 的调用方授权及业务处理。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var requireValue = context.required
var code = requireValue(req.getParameter('code'), 'code')
var checkMedia = context.media
// 保留媒体类型与原始字节，避免重试时改变摘要语义。
checkMedia(req, 'application/json')
var raw = context.http.raw(req, context.p.getLimits().getMaxMessageBytes())
var result = push({action: 'action', caller: caller, request: req, raw: raw, body: context.json.object(raw), key: req.getHeader('Idempotency-Key'), code: code})
return result.location == null ? context.http.reply(result.status::int, result.body)
    : context.http.located(result.status::int, result.body, result.location)

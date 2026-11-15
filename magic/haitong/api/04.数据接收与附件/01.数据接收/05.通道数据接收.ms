{
  "id": "haitong-public-5",
  "groupId": "haitong-files-api",
  "name": "05.通道数据接收",
  "path": "/ingest",
  "method": "POST",
  "parameters": [{"name": "channel", "required": false, "dataType": "String", "description": "必填查询参数 channel"}],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "接收授权通道的数据，校验原字节幂等键后提交异步投递。 复用 /haitong/push 的鉴权、幂等和接收处理。"
}
================================
import '@/haitong/common' as common
import '@/haitong/push' as push
import request

// POST /api/v1/ingest。接收授权通道的数据，校验原字节幂等键后提交异步投递。
// 与同组入口共用 /haitong/push 的调用方授权及业务处理。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var requireValue = context.required
var channel = requireValue(req.getParameter('channel'), 'channel')
var checkMedia = context.media
// 保留媒体类型与原始字节，避免重试时改变摘要语义。
checkMedia(req, 'application/json')
var raw = context.http.raw(req, context.p.getLimits().getMaxMessageBytes())
var result = push({action: 'ingest', caller: caller, request: req, raw: raw, body: context.json.object(raw), key: req.getHeader('Idempotency-Key'), channel: channel})
return result.location == null ? context.http.reply(result.status::int, result.body)
    : context.http.located(result.status::int, result.body, result.location)

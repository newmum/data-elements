{
  "id": "haitong-public-1",
  "groupId": "haitong-files-api",
  "name": "01.统一数据推送",
  "path": "/push",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "按可信调用方和已登记数据源接收表批次或同步登记快照。 复用 /haitong/push 的鉴权、幂等和接收处理。"
}
================================
import '@/haitong/common' as common
import '@/haitong/push' as push
import request

// POST /api/v1/push。按可信调用方和已登记数据源接收表批次或同步登记快照。
// 与同组入口共用 /haitong/push 的调用方授权及业务处理。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var checkMedia = context.media
// 保留媒体类型与原始字节，避免重试时改变摘要语义。
checkMedia(req, 'application/json')
var raw = context.http.raw(req, context.p.getLimits().getMaxMessageBytes())
var result = push({action: 'data-push', caller: caller, request: req, raw: raw, body: context.json.object(raw), key: req.getHeader('Idempotency-Key')})
return result.location == null ? context.http.reply(result.status::int, result.body)
    : context.http.located(result.status::int, result.body, result.location)

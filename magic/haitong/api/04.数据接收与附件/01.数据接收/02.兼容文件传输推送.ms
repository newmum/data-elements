{
  "id": "haitong-public-2",
  "groupId": "haitong-files-api",
  "name": "02.兼容文件传输推送",
  "path": "/ftp",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "兼容文件传输推送，按登记范围校验表批次后提交 FTP 投递。 复用 /haitong/push 的鉴权、幂等和接收处理。"
}
================================
import '@/haitong/common' as common
import '@/haitong/push' as push
import request

// POST /api/v1/ftp。兼容文件传输推送，按登记范围校验表批次后提交 FTP 投递。
// 与同组入口共用 /haitong/push 的调用方授权及业务处理。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var checkMedia = context.media
// 保留媒体类型与原始字节，避免重试时改变摘要语义。
checkMedia(req, 'application/json')
var raw = context.http.raw(req, context.p.getLimits().getMaxMessageBytes())
var result = push({action: 'FTP', caller: caller, request: req, raw: raw, body: context.json.object(raw), key: req.getHeader('Idempotency-Key')})
return result.location == null ? context.http.reply(result.status::int, result.body)
    : context.http.located(result.status::int, result.body, result.location)

{
  "properties": {},
  "id": "haitong-public-8",
  "script": null,
  "groupId": "haitong-files-api",
  "name": "08.批量文件导入",
  "createTime": null,
  "updateTime": 1792981130061,
  "lock": null,
  "createBy": null,
  "updateBy": "haitong-admin",
  "path": "/import",
  "method": "POST",
  "parameters": [{"name": "channel", "required": false, "dataType": "String", "description": "必填查询参数 channel"}],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按通道接收 multipart 文件，提交历史批量导入。 复用 /haitong/push 的鉴权、幂等和接收处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/haitong/common' as common
import '@/haitong/push' as push
import request

// POST /api/v1/import。按通道接收 multipart 文件，提交历史批量导入。
// 与同组入口共用 /haitong/push 的调用方授权及业务处理。

var context = common()
var resolveCaller = context.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var requireValue = context.required
var channel = requireValue(req.getParameter('channel'), 'channel')
var checkMedia = context.media
// 保留媒体类型与原始字节，避免重试时改变摘要语义。
checkMedia(req, 'multipart/form-data')
var result = push({action: 'upload', caller: caller, request: req, channel: channel, key: req.getHeader('Idempotency-Key'), file: req.getPart('file')})
return result.location == null ? context.http.reply(result.status::int, result.body)
    : context.http.located(result.status::int, result.body, result.location)

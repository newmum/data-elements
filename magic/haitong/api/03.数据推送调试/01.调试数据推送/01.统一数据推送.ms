{
  "id": "haitong-push-operations-push-01",
  "script": null,
  "groupId": "be213b207d6a5a4c981b3e406033e695",
  "name": "01.统一数据推送",
  "createTime": null,
  "updateTime": 1792800658298,
  "lock": null,
  "createBy": null,
  "updateBy": "haitong-admin",
  "path": "/send",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "Magic API 调试入口。使用与 POST /api/v1/data-push 完全相同的认证、幂等、独立登记契约校验和异步投递逻辑；请求头必须提供 X-Api-Key 与 Idempotency-Key。deliveryType 支持 FTP、KAFKA、LOG、REGISTRY_SYNC；后者只接受 X-Standalone-Registry-Key。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import '@/haitong/common' as common
import request
import '@/haitong/push' as push

// POST /push/debug/send。由 /haitong/push 与正式数据推送接口共用登记、幂等和异步投递逻辑。登记同步使用专用服务身份。

var c = common()
var resolveCaller = c.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var requireMedia = c.media
requireMedia(req, 'application/json')
var normalized = c.http.object(req, c.p.getLimits().getMaxMessageBytes())
var raw = c.json.bytes(normalized)
return push({
    action: 'data-push',
    caller: caller,
    request: req,
    raw: raw,
    body: normalized,
    key: req.getHeader('Idempotency-Key')
}).body

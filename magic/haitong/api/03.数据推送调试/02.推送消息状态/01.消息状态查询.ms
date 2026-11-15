{
  "id": "haitong-push-operations-status-02",
  "script": null,
  "groupId": "f60fbc37cec250dcae3796beae651b2e",
  "name": "01.消息状态查询",
  "createTime": null,
  "updateTime": 1792800563851,
  "lock": null,
  "createBy": null,
  "updateBy": "haitong-admin",
  "path": "/status",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "Magic API 调试入口。按 messageId 查询当前调用方拥有的异步投递状态；请求体为 { messageId: \"...\" }。非管理员不会看到目的端敏感配置或完整结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import '@/haitong/common' as common
import request
import '@/haitong/message' as message

// POST /push/message/status。按 messageId 查询当前调用方可见的消息。与正式消息查询共用 /haitong/message。

var c = common()
var resolveCaller = c.caller
var req = request.get().getRequest()
var caller = resolveCaller(req)
var value = c.http.object(req, 65536)
var requireValue = c.required
return message({caller: caller, id: requireValue(value.messageId, 'messageId')})

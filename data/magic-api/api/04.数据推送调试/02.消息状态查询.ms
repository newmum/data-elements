{"properties":{},"id":"haitong-push-operations-status-02","groupId":"haitong-push-operations-04","name":"02.消息状态查询","path":"/message-status","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"Magic API 调试入口。按 messageId 查询当前调用方拥有的异步投递状态；请求体为 { messageId: \"...\" }。非管理员不会看到目的端敏感配置或完整结果。","requestBodyDefinition":null,"responseBodyDefinition":null}
================================
import '@/haitong/common' as common
import request
var c=common();var _required=c.required;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/message' as message
var value=c.http.object(req,65536)
return message({caller:caller,id:_required(value.messageId,'messageId')})

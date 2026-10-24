{"properties":{},"id":"haitong-push-operations-push-01","groupId":"haitong-push-operations-04","name":"01.统一数据推送","path":"/data-push","method":"POST","parameters":[],"options":[],"headers":[],"paths":[],"description":"Magic API 调试入口。使用与 POST /api/v1/data-push 完全相同的认证、幂等、独立登记契约校验和异步投递逻辑；请求头必须提供 X-Api-Key 与 Idempotency-Key。deliveryType 支持 FTP、KAFKA、LOG、REGISTRY_SYNC；后者只接受 X-Standalone-Registry-Key。","requestBodyDefinition":null,"responseBodyDefinition":null}
================================
import '@/haitong/common' as common
import request
var c=common();var _media=c.media;var _caller=c.caller;;var req=request.get().getRequest();var caller=_caller(req)
import '@/haitong/push' as push
_media(req,'application/json')
var normalized=c.http.object(req,c.p.getLimits().getMaxMessageBytes());var raw=c.json.bytes(normalized)
return push({action:'data-push',caller:caller,request:req,raw:raw,body:normalized,key:req.getHeader('Idempotency-Key')}).body

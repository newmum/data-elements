{
  "id": "haitong-validation-12",
  "groupId": "haitong-validation-api",
  "name": "12.业务消息状态查询",
  "path": "/messages/status",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [
    {
      "name": "Content-Type",
      "value": "application/json",
      "required": true,
      "dataType": "String"
    },
    {
      "name": "X-Api-Key",
      "value": "",
      "required": true,
      "dataType": "String",
      "description": "本登记源第五步签发的业务密钥；不填写编辑器密码"
    }
  ],
  "paths": [],
  "requestBody": "{\n  \"messageId\": \"0fe9f583-a2fc-424a-851c-2c48f97612c4\"\n}",
  "description": "按当前调用方范围查询业务数据的最终投递状态。"
}
================================
import '@/haitong/common' as common
import request
var c=common();var callerOf=c.caller;var media=c.media;var req=request.get().getRequest();var caller=callerOf(req)
media(req,'application/json')
import '@/haitong/message' as message
var value=c.http.object(req,65536);var required=c.required
return message({caller:caller,id:required(value.messageId,'messageId')})

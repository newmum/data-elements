{
  "id": "haitong-validation-11",
  "groupId": "haitong-validation-api",
  "name": "11.附件状态查询",
  "path": "/attachments/status",
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
  "requestBody": "{\n  \"fileId\": \"add63bb1-2f0d-4be1-a622-0cf79d2bdd66\"\n}",
  "description": "查询附件状态、SHA-256和每个目标投递状态。READY表示文件就绪，deliveryStatus=SUCCEEDED表示附件已经投递成功。"
}
================================
import '@/haitong/common' as common
import request
var c=common();var callerOf=c.caller;var media=c.media;var req=request.get().getRequest();var caller=callerOf(req)
media(req,'application/json')
import '@/haitong/files' as files
var value=c.http.object(req,65536);var required=c.required
return files({action:'status',caller:caller,request:req,response:c.http.response(),id:required(value.fileId,'fileId')})

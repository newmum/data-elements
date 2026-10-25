{
  "id": "haitong-validation-10",
  "groupId": "haitong-validation-api",
  "name": "10.附件提交投递",
  "path": "/attachments/submit",
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
    },
    {
      "name": "Idempotency-Key",
      "value": "validation-submit-person-1",
      "required": true,
      "dataType": "String",
      "description": "修改正文时更换此值；重放相同正文保留原值"
    }
  ],
  "paths": [],
  "requestBody": "{\n  \"fileId\": \"add63bb1-2f0d-4be1-a622-0cf79d2bdd66\",\n  \"businessKey\": \"VALIDATION-PERSON-001\",\n  \"metadata\": {\n    \"recordId\": \"VALIDATION-PERSON-001\"\n  }\n}",
  "description": "将已上传附件提交到已配置文件存储；异步受理后必须通过11.附件状态查询确认最终投递成功。"
}
================================
import '@/haitong/common' as common
import request
var c=common();var callerOf=c.caller;var media=c.media;var req=request.get().getRequest();var caller=callerOf(req)
media(req,'application/json')
import com.haitong.files.FileUploadService
var value=c.http.object(req,65536);var required=c.required;var object=c.object;var unknown=c.unknown;var bean=c.bean
unknown(value,['fileId','businessKey','metadata'])
return bean('fileUploadService').submit(caller,required(value.fileId,'fileId'),req.getHeader('Idempotency-Key'),new FileUploadService.Submission(required(value.businessKey,'businessKey'),object(value.metadata)))

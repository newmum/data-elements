{
  "id": "haitong-validation-upload-person",
  "groupId": "haitong-validation-api",
  "name": "07.人员附件上传",
  "path": "/attachments/person",
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
      "value": "validation-upload-person-1",
      "required": true,
      "dataType": "String",
      "description": "修改正文时更换此值；重放相同正文保留原值"
    }
  ],
  "paths": [],
  "requestBody": "{\n  \"datasourceId\": \"2113960660861288448\",\n  \"fileName\": \"person-1.png\",\n  \"base64\": \"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=\"\n}",
  "description": "Magic编辑器的小附件测试入口，原文最多64KiB，固定登记附件字段。正常大文件使用正式流式/分片接口。返回fileId后先查询至READY，再调用10.附件提交投递，最终核对SFTP原文件。"
}
================================
import '@/haitong/common' as common
import request
var c=common();var callerOf=c.caller;var media=c.media;var req=request.get().getRequest();var caller=callerOf(req)
media(req,'application/json')
import com.haitong.files.FileUploadService
import java.util.Base64
import java.io.ByteArrayInputStream
var value=c.http.object(req,131072);var fail=c.fail;var required=c.required;var unknown=c.unknown;var bean=c.bean
unknown(value,['datasourceId','fileName','base64'])
var encoded=required(value.base64,'base64')
if(encoded.length()>87384){fail(413,'PAYLOAD_TOO_LARGE','编辑器附件测试最多64KiB，请使用正式分片接口')}
var bytes=Base64.getDecoder().decode(encoded)
if(bytes.length<1 || bytes.length>65536){fail(413,'PAYLOAD_TOO_LARGE','测试附件大小须为1至65536字节')}
var service=bean('fileUploadService')
return service.single(caller,req.getHeader('Idempotency-Key'),new FileUploadService.Single(required(value.datasourceId,'datasourceId'),'validation_push_person','attachment_file_id',required(value.fileName,'fileName')),bytes.length,new ByteArrayInputStream(bytes))

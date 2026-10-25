{
  "id": "haitong-validation-push-vehicle",
  "groupId": "haitong-validation-api",
  "name": "04.车辆登记数据推送",
  "path": "/push/vehicle",
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
      "value": "validation-push-vehicle-001",
      "required": true,
      "dataType": "String",
      "description": "修改正文时更换此值；重放相同正文保留原值"
    }
  ],
  "paths": [],
  "requestBody": "{\n  \"datasourceId\": \"2113960660861288448\",\n  \"batchId\": \"VALIDATION-VEHICLE-001\",\n  \"records\": [\n    {\n      \"record_id\": \"VALIDATION-VEHICLE-001\",\n      \"code\": \"01\",\n      \"name\": \"车辆登记验证1\",\n      \"status_code\": \"01\",\n      \"amount\": 10,\n      \"updated_at\": \"2026-10-24 10:00:01\",\n      \"business_time\": \"2026-10-24 09:00:01\",\n      \"attachment_file_id\": \"5b628dba-a8ed-4100-95ee-ca01e52382c4\"\n    },\n    {\n      \"record_id\": \"VALIDATION-VEHICLE-002\",\n      \"code\": \"02\",\n      \"name\": \"车辆登记验证2\",\n      \"status_code\": \"02\",\n      \"amount\": 20,\n      \"updated_at\": \"2026-10-24 10:00:02\",\n      \"business_time\": \"2026-10-24 09:00:02\",\n      \"attachment_file_id\": \"70a79d5c-3544-4590-bd75-c6e038bdfbdd\"\n    },\n    {\n      \"record_id\": \"VALIDATION-VEHICLE-003\",\n      \"code\": \"03\",\n      \"name\": \"车辆登记验证3\",\n      \"status_code\": \"01\",\n      \"amount\": 30,\n      \"updated_at\": \"2026-10-24 10:00:03\",\n      \"business_time\": \"2026-10-24 09:00:03\"\n    },\n    {\n      \"record_id\": \"VALIDATION-VEHICLE-004\",\n      \"code\": \"04\",\n      \"name\": \"车辆登记验证4\",\n      \"status_code\": \"02\",\n      \"amount\": 40,\n      \"updated_at\": \"2026-10-24 10:00:04\",\n      \"business_time\": \"2026-10-24 09:00:04\"\n    },\n    {\n      \"record_id\": \"VALIDATION-VEHICLE-005\",\n      \"code\": \"05\",\n      \"name\": \"车辆登记验证5\",\n      \"status_code\": \"01\",\n      \"amount\": 50,\n      \"updated_at\": \"2026-10-24 10:00:05\",\n      \"business_time\": \"2026-10-24 09:00:05\"\n    },\n    {\n      \"record_id\": \"VALIDATION-VEHICLE-006\",\n      \"code\": \"06\",\n      \"name\": \"车辆登记验证6\",\n      \"status_code\": \"02\",\n      \"amount\": 60,\n      \"updated_at\": \"2026-10-24 10:00:06\",\n      \"business_time\": \"2026-10-24 09:00:06\"\n    }\n  ]\n}",
  "description": "与结构化数据与附件Excel模板对应，固定表名validation_push_vehicle。先完成登记与节点管理同步，使用对应业务密钥。关联附件先通过本目录接口上传并提交，再回填fileId。受理后查询消息状态，SUCCEEDED才表示最终投递成功。"
}
================================
import '@/haitong/common' as common
import request
var c=common();var callerOf=c.caller;var media=c.media;var req=request.get().getRequest();var caller=callerOf(req)
media(req,'application/json')
import '@/haitong/push' as push
var value=c.http.object(req,c.p.getLimits().getMaxMessageBytes())
var unknown=c.unknown;unknown(value,['datasourceId','batchId','records'])
value.deliveryType='FTP'
value.tableName='validation_push_vehicle'
return push({action:'data-push',caller:caller,request:req,body:value,raw:c.json.bytes(value),key:req.getHeader('Idempotency-Key')}).body

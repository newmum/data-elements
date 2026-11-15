{
  "properties": {},
  "id": "0fe55571b03e3e49b1118b04c7be31cb",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "20.HTTP服务_2120532997333159936",
  "createTime": 1794409443010,
  "updateTime": 1794409442864,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/published/__tenant/2084109831682699264/services/2120532997333159936",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "HTTP服务编排发布入口",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/common/serviceFlowManage' as manage
return manage({action:'invoke',flowId:'2120532997333159936',body:body || parameter || {}})

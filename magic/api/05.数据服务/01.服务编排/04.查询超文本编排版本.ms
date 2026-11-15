{
  "properties": {},
  "id": "297753ebb8fe51eda48002a10d26ff1e",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "04.查询超文本编排版本",
  "path": "/versions",
  "method": "GET",
  "description": "按认证租户和关联服务权限查询HTTP编排版本",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "当前租户的编排流程标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：查询HTTP编排版本。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'versions',flowId:param.id,body:{}})

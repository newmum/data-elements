{
  "properties": {},
  "id": "297753ebb8fe51eda48002a10d26ff1e",
  "script": null,
  "groupId": "d7eb5795e6f4585cb5d2d04a40885de2",
  "name": "04.查询HTTP编排版本",
  "path": "/flows/{id}/versions",
  "method": "GET",
  "description": "按认证租户和关联服务权限查询HTTP编排版本",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：查询HTTP编排版本。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'versions',flowId:id,body:{}})

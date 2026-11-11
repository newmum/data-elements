{
  "properties": {},
  "id": "492aa26450c85c2ca40ea4c7e77d33d3",
  "script": null,
  "groupId": "d7eb5795e6f4585cb5d2d04a40885de2",
  "name": "02.调试HTTP编排",
  "path": "/flows/{id}/debug",
  "method": "POST",
  "description": "按认证租户和关联服务权限调试HTTP编排",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：调试HTTP编排。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'debug',flowId:id,body:body})

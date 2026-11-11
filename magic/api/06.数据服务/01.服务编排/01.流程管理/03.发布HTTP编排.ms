{
  "properties": {},
  "id": "c0573134cc5c524492bc5b8fb882e168",
  "script": null,
  "groupId": "d7eb5795e6f4585cb5d2d04a40885de2",
  "name": "03.发布HTTP编排",
  "path": "/flows/{id}/publish",
  "method": "POST",
  "description": "按认证租户和关联服务权限发布HTTP编排",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：发布HTTP编排。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'publish',flowId:id,body:body})

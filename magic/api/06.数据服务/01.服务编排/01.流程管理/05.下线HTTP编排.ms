{
  "properties": {},
  "id": "3d31df37f14553e48a5721cf3e073309",
  "script": null,
  "groupId": "d7eb5795e6f4585cb5d2d04a40885de2",
  "name": "05.下线HTTP编排",
  "path": "/flows/{id}/offline",
  "method": "POST",
  "description": "按认证租户和关联服务权限下线HTTP编排",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：下线HTTP编排。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'offline',flowId:id,body:body})

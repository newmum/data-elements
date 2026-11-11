{
  "properties": {},
  "id": "334f3354e13e57c89cbbf359dcf39e9c",
  "script": null,
  "groupId": "d7eb5795e6f4585cb5d2d04a40885de2",
  "name": "01.保存HTTP编排草稿",
  "path": "/flows/{id}",
  "method": "PUT",
  "description": "按认证租户和关联服务权限保存HTTP编排草稿",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": []
}
================================
import '@/common/serviceFlowManage' as manage

// 接口：保存HTTP编排草稿。
// 关联服务范围及流程状态由共用函数校验。
return manage({action:'save',flowId:id,body:body})

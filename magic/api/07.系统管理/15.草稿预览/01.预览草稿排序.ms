{
  "properties": {},
  "id": "sym_draft_preview_order_v1",
  "script": null,
  "groupId": "sym_draft_preview_group_v1",
  "name": "01.预览草稿排序",
  "path": "/order",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "校验行业登录会话后，调用 @/common/previewDraftOrder 对浏览器提交的草稿项排序分页。返回标识和总数，无业务数据读写。"
}
================================
import '@/common/previewDraftOrder' as previewDraftOrder
import cn.dev33.satoken.stp.StpUtil

// POST /sym/table/order 为行业应用的草稿表格排序，返回当前页标识和总数。
// 排序、分页和请求校验与平台工作区共用 @/common/previewDraftOrder，无数据库读写。
StpUtil.checkLogin()
return previewDraftOrder(body)

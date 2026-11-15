{
  "properties": {},
  "id": "idaas_draft_preview_order_v1",
  "script": null,
  "groupId": "af2400b9cd0d518c8a01dfc98665b222",
  "name": "02.预览草稿排序",
  "path": "/order",
  "method": "POST",
  "parameters": [],
  "options": [],
  "headers": [],
  "paths": [],
  "description": "校验平台操作员会话后，调用 @/common/previewDraftOrder 对工作区浏览器提交的草稿项排序分页。返回标识和总数，无目录或租户数据读写。"
}
================================
import '@/common/previewDraftOrder' as previewDraftOrder
import platformSession

// POST /idaas/workspace/order 为平台工作区草稿表格排序，返回当前页标识和总数。
// 独立平台会话验证后，使用行业草稿入口共用的 @/common/previewDraftOrder。
platformSession.id()
return previewDraftOrder(body)

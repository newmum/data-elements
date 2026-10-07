{
  "properties": {},
  "id": "3e9673599d77f92c0a646abf469aa9e2",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "03.治安巡控活动分页服务",
  "createTime": 1781574559968,
  "updateTime": 1781574559916,
  "lock": null,
  "createBy": null,
  "updateBy": "flowserve",
  "path": "/police/patrol/activity-page",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按页返回治安巡控活动、辖区、警力、处置数量和验收状态，支撑公开查询与业务查询。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.治安巡控活动分页服务
// 路由：GET /dws/flowserve/published/police/patrol/activity-page
// 业务说明：按页返回治安巡控活动、辖区、警力、处置数量和验收状态，支撑公开查询与业务查询。
// === 自动生成，请勿手改；flowId: fl_t6xg9ldazp ===


// 节点: script [script_release] 生成放流活动清单
var activities = [{ id:1, seaArea:'厦门湾', species:'大黄鱼', quantity:120000, accepted:true }, { id:2, seaArea:'湄洲湾', species:'黑鲷', quantity:80000, accepted:false }, { id:3, seaArea:'东山湾', species:'花鲈', quantity:60000, accepted:true }];

// 节点: filter [filter_release] 过滤有效活动
var validActivities = activities;
// filter predicate: item.quantity > 0

// 节点: paginate [page_release] 活动分页
var __page = page == null ? 1 : page;
var __size = size == null ? 10 : size;
var pagedActivities = { page: __page, size: __size, list: validActivities };

// 节点: response [response] 标准响应
return { code: 0, message: 'success', data: pagedActivities };

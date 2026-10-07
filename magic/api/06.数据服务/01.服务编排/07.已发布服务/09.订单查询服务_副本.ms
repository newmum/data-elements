{
  "properties": {},
  "id": "289bb9b793969546781e6e4238e36074",
  "script": null,
  "groupId": "e5ce028f1797312d57c628b66ffcf657",
  "name": "09.订单查询服务_副本",
  "createTime": 1780823417641,
  "updateTime": 1781318876893,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/secure/order-summary",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按用户ID查询示例订单数据。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.订单查询服务_副本
// 路由：GET /dws/flowserve/published/secure/order-summary
// 业务说明：按用户ID查询示例订单数据。
// === FlowServe 自动生成，请勿手改；flowId: fl_4975al6gof ===

if (userId == null) {
    return response.json(400, { code: 400, message: 'userId 不能为空' });
}

// 节点: sql-query [node_7qoeb4m2qt] 查询订单
var orders = db.chengtian.select("select\n  id as orderId,\n  user_id as userId,\n  product_name as productName,\n  amount,\n  status,\n  date_format(created_at, '%Y-%m-%d %H:%i:%s') as createdAt\nfrom fs_demo_orders\nwhere user_id = #{userId}\norder by created_at desc\nlimit 20");

// 节点: response [node_u5vix24ryi] 返回订单列表
return { code: 0, message: 'success', data: orders };

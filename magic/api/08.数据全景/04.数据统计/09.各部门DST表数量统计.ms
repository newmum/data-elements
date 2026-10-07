{
  "properties": {},
  "id": "a1b2c3d4e5f678901234567890abcdef",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "09.各部门DST表数量统计",
  "createTime": 1784523373047,
  "updateTime": 1787153758127,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/dstTableByOrg",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "DST 表：db_table_t.business_type = \"业务表\"；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：09.各部门DST表数量统计
// 路由：GET /scene/data-count/dstTableByOrg
// 业务说明：DST 表：db_table_t.business_type = "业务表"；读取或计算后返回业务结果。
/*
 * 各部门 DST 表数量统计
 *
 * DST 表：db_table_t.business_type = "业务表"
 * 部门：取所属库 db_datasource_t.org_id
 * 返回 orgId + tableCount，前端用组织树匹配部门名称
 */

var sql = "SELECT d.org_id AS orgId, COUNT(*) AS tableCount " +
    "FROM db_table_t t " +
    "JOIN db_datasource_t d ON t.datasource_id = d.tid " +
    "WHERE t.is_del = 0 AND d.is_del = 0 AND t.business_type = \"业务表\" " +
    "GROUP BY d.org_id " +
    "ORDER BY tableCount DESC"

var result = db.select(sql).query()

return result

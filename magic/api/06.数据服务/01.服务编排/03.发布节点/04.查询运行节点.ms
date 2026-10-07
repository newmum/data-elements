{
  "properties": {},
  "id": "ac00197306be80d43fe508fe80d2d232",
  "script": null,
  "groupId": "9243108b5fe0f78c8fe28aac46f7ebf3",
  "name": "04.查询运行节点",
  "createTime": 1784826968034,
  "updateTime": 1784826968034,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/runtime-nodes",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询本机及远程 Magic 运行节点。示例：GET /dws/flowserve/runtime-nodes。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：04.查询运行节点
// 路由：GET /dws/flowserve/runtime-nodes
// 业务说明：查询本机及远程 Magic 运行节点。示例：GET /dws/flowserve/runtime-nodes。
// 查询运行节点：处理 GET /runtime-nodes 的核心查询和组装返回数据逻辑。
var rows = db.select("""
select id,name,business_domain business_domain,service_type service_type, base_url base_url,
       management_url management_url, case when deploy_token is null
        or deploy_token='' then false else true
        end deploy_token_set,
       local_node local_node,default_node default_node,enabled, date_format(created_at,
        '%Y-%m-%d %H:%i:%s') created_at
from fs_runtime_node
order by default_node desc,created_at desc
""")
return rows

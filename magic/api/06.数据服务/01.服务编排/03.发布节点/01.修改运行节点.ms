{
  "properties": {},
  "id": "c34565c33645efce10c7a339e70510cf",
  "script": null,
  "groupId": "9243108b5fe0f78c8fe28aac46f7ebf3",
  "name": "01.修改运行节点",
  "createTime": 1784826968098,
  "updateTime": 1784826968098,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/runtime-nodes/{id}",
  "method": "PUT",
  "parameters": [],
  "options": [],
  "requestBody": "{\"name\":\"本机节点\",\"businessDomain\":\"平台数据服务\",\"serviceType\":\"Magic API\",\"baseUrl\":\"http://127.0.0.1:8088\",\"localNode\":true,\"defaultNode\":true,\"enabled\":true}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "修改 Magic 运行节点；空令牌保留原值。请求示例同新增接口；使用请求参数 defaultNode、name、businessDomain、serviceType、baseUrl、managementUrl、localNode、enabled；涉及 fs_runtime_node 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.修改运行节点
// 路由：PUT /dws/flowserve/runtime-nodes/{id}
// 业务说明：修改 Magic 运行节点；空令牌保留原值。请求示例同新增接口；使用请求参数 defaultNode、name、businessDomain、serviceType、baseUrl、managementUrl、localNode、enabled；涉及 fs_runtime_node 等业务表；执行数据库写入并返回处理结果。
// 修改运行节点：处理 PUT /runtime-nodes/{id} 的核心写入或状态变更逻辑。
if (body.defaultNode == true) { db.table('fs_runtime_node').where().ne('id',id).update({default_node:0}) }
var patch = {name:body.name,business_domain:body.businessDomain,service_type:body.serviceType,
  base_url:body.baseUrl,management_url:body.managementUrl,local_node:body.localNode?1:0,
  default_node:body.defaultNode?1:0,enabled:body.enabled==false?0:1}
if (body.deployToken != null && body.deployToken != '') { patch.deploy_token=body.deployToken }
db.table('fs_runtime_node').where().eq('id',id).update(patch)
body = null
var row = db.selectOne("""
select id,name,business_domain business_domain,service_type service_type, base_url base_url,
       management_url management_url, case when deploy_token is null
        or deploy_token='' then false else true
        end deploy_token_set,
       local_node local_node,default_node default_node,enabled,created_at created_at
from fs_runtime_node
where id=#{id}
""")
return row

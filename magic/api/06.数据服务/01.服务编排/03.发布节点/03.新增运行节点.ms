{
  "properties": {},
  "id": "b7602a392da7cabbe42a90e4783a5f0c",
  "script": null,
  "groupId": "9243108b5fe0f78c8fe28aac46f7ebf3",
  "name": "03.新增运行节点",
  "createTime": 1784826968066,
  "updateTime": 1784826968066,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/runtime-nodes",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"name\":\"本机节点\",\"businessDomain\":\"平台数据服务\",\"serviceType\":\"Magic API\",\"baseUrl\":\"http://127.0.0.1:8088\",\"localNode\":true,\"defaultNode\":true,\"enabled\":true}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "新增 Magic 运行节点。请求示例包含 name、baseUrl、managementUrl；涉及 fs_runtime_node 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.新增运行节点
// 路由：POST /dws/flowserve/runtime-nodes
// 业务说明：新增 Magic 运行节点。请求示例包含 name、baseUrl、managementUrl；涉及 fs_runtime_node 等业务表；执行数据库写入并返回处理结果。
// 新增运行节点：处理 POST /runtime-nodes 的核心写入或状态变更逻辑。
import flowDataSource
var newId = flowDataSource.newId('rt')
if (body.defaultNode == true) { db.table('fs_runtime_node').update({default_node:0}) }
db.table('fs_runtime_node').insert({id:newId,name:body.name,business_domain:body.businessDomain,
  service_type:body.serviceType,base_url:body.baseUrl,management_url:body.managementUrl,
  deploy_token:body.deployToken,local_node:body.localNode?1:0,default_node:body.defaultNode?1:0,
  enabled:body.enabled==false?0:1})
body = null
var row = db.selectOne("""
select id,name,business_domain business_domain,service_type service_type, base_url base_url,
       management_url management_url, case when deploy_token is null
        or deploy_token='' then false else true
        end deploy_token_set,
       local_node local_node,default_node default_node,enabled,created_at created_at
from fs_runtime_node
where id=#{newId}
""")
return row

{
  "properties": {},
  "id": "5c95aa066571db36b089e44f948a9924",
  "script": null,
  "groupId": "962e0e1e86609a165de79f1eb5294c2f",
  "name": "08.查询流程列表",
  "createTime": 1784826967365,
  "updateTime": 1784826967365,
  "createBy": "flowserve",
  "updateBy": "flowserve",
  "path": "/flows",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "查询全部未删除的数据服务编排及调用次数。示例：GET /dws/flowserve/flows。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：08.查询流程列表
// 路由：GET /dws/flowserve/flows
// 业务说明：查询全部未删除的数据服务编排及调用次数。示例：GET /dws/flowserve/flows。
// 查询流程基础信息，并按调用日志聚合调用次数。
var rows = db.select("""
select f.id as flow_id, f.name, f.flow_group as 'group', f.status, f.current_version as version,
       date_format(f.updated_at, '%Y-%m-%d %H:%i:%s') as updated_at, coalesce(c.calls,
        0) as calls
from fs_flow f
left join
    (select flow_id,count(*) calls
     from fs_call_log
     group by flow_id) c on c.flow_id=f.id
where f.deleted=0
order by f.updated_at desc
""")
return rows

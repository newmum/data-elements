{
  "properties": {},
  "id": "bc7ab46f70354dab95ca7deb785d72fd",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "15.超市详情",
  "createTime": 1782955344192,
  "updateTime": 1784521647429,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/detail",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"85368b91972c4a35b83b8fa73e34e740\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"未找到该资产\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "按目录标识复用数据超市列表查询，并返回匹配资产的详细展示资料。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：15.超市详情
// 路由：POST /dws/market/detail
// 业务说明：按目录标识复用数据超市列表查询，并返回匹配资产的详细展示资料。
import "@post:/dws/market/list" as marketList;

body = {
    "query": [
        {
            "term": {
                // 解析请求体并为可选参数设置默认值。
                "tid": body.tid
            }
        }
    ]
}

var {list} = marketList();
if (!list?.size()){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "未找到该资产"  
}

// 返回当前业务分支的标准结果。
return list[0]

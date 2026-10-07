{
  "properties": {},
  "id": "db35431a36b34bc88b3b48914d7ba0ff",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "12.资产申请单详情",
  "createTime": 1782955344166,
  "updateTime": 1786191947649,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applyForm/queryById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"b71ef91ddcc940a99b220cc3d7cd9120\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": \"0d50a16cf9b5458398a22f6fa158ea57\"\n}",
  "description": "applyResource 返回申请目录详细；使用请求参数 tid；读取或计算后返回业务结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：12.资产申请单详情
// 路由：POST /dws/market/applyForm/queryById
// 业务说明：applyResource 返回申请目录详细；使用请求参数 tid；读取或计算后返回业务结果。
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import jsons
import "@post:/dws/market/list" as marketList;
import tenantRuntime
// 获取请求参数
var tid = body?.tid
if(tid == null){
    // 参数或业务状态不满足要求时立即终止请求。
    exit -1, "tid不能为空";
}

var tenantId = tenantRuntime.id()

//查询es 获取主表数据
var applyData = esCommonService.getDocument("dataassets_apply_form", tid)

if (!applyData){
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "未找到申请单信息"
}

var catalogIds =  applyData.catalogIds.split(",")
// applyResource 返回申请目录详细
body = {
    "query": [
        {
            "terms": {
                "tid": catalogIds
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
return {
  ...applyData,
  applyResource: list
}

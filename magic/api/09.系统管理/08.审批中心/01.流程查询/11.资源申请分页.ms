{
  "properties": {},
  "id": "ce1844634b80491c8aeb9c7da6ebec7e",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "11.资源申请分页",
  "createTime": 1782955344554,
  "updateTime": 1784657246159,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/applyPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20,\n    \"sortField\": \"createdTime\",\n    \"sortDir\": \"desc\",\n    \"conditions\": [\n        {\n            \"field\": \"flowStatus\",\n            \"value\": \"5\",\n            \"type\": \"match\"\n        },\n        {\n            \"field\": \"applyName\",\n            \"value\": \"测试\",\n            \"type\": \"like\"\n        }\n    ]\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按请求目标与业务状态执行资源申请分页，返回本次操作结果；使用请求参数 pageNum、pageSize、sortField、sortDir、conditions；涉及 data_apply_form_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：11.资源申请分页
// 路由：POST /sym/approval/query/applyPage
// 业务说明：按请求目标与业务状态执行资源申请分页，返回本次操作结果；使用请求参数 pageNum、pageSize、sortField、sortDir、conditions；涉及 data_apply_form_t 等业务表。
/*
 * 审批中心 - 资源申请分页
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */
import com.linewell.dataelement.elasticsearch.EsCommonService as esCommonService
import com.linewell.dataelement.elasticsearch.EsPageParam
import com.linewell.dataelement.elasticsearch.ConditionDTO
import "@post:/sym/user/me" as me;
import '@/common/localCache' as localCache
import jsons
import "@post:/dst/catalog/detail" as queryById

var userInfo = me()
var orgId = userInfo?.orgId

body == null ? body = {}: body

// 从 MySQL 业务表读取当前有效数据。
count = db.table("data_apply_form_t").count();
if(count == 0){
  // 返回当前业务分支的标准结果。
  return {
        "pageNum": 1,
        "pageSize": 20,
        "total": 0,
        "list":[]
    }
}

EsPageParam es = new EsPageParam();
es.setIndex("dataassets_apply_form");

// 解析请求体并为可选参数设置默认值。
var pageNum = body?.pageNum::int(1)
var pageSize =  body?.pageSize::int(10)
es.setPageNum(pageNum);
es.setPageSize(pageSize);

if(body?.sortField != null)
{
  es.setSortField(body?.sortField)
}
if(body?.sortDir != null)
{
  es.setSortDir(body?.sortDir)  
}

if(body?.conditions != null){
    var list = new ArrayList();
    var conditions = body?.conditions;
    for(item in conditions){
        var temp = new ConditionDTO()
        temp.setField(item.field)
        temp.setType(item.type)
        temp.setValue(item.value)
        list.add(temp)
    }
    es.setConditions(list)
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
existInxex = esCommonService.indexExists("dataassets_apply_form");
if(!existInxex){
    // 返回当前业务分支的标准结果。
    return {
        "pageNum": 1,
        "pageSize": 20,
        "total": 0,
        "list":[]
    }
}

// 执行 Elasticsearch 查询或索引同步，保持检索结果与主数据一致。
const {total, list} = esCommonService.page(es);

list.forEach(item => {
   if (item.catalogIds){
        body = {tid: item.catalogIds}

        var obj = item["detail"] = queryById();  

        if (obj.assetType == "catalog"){
            var res = []
            !!obj.targetTableId && res.push("table")
            !!obj.apiId && res.push("api")
            !!obj.fileId && res.push("file")
            !!obj.dirId && res.push("dir")
            obj["resType"] = res
            item["detail"]["resType"] = res
        }

        item.businessId = item.tid
        // 从 MySQL 业务表读取当前有效数据。
        var flowInfo = db.selectOne(`
            SELECT
              fd.flow_code,
              fd.flow_name,
              fi.business_id,
              fi.id AS instance_id,
              fi.definition_id AS definition_id,
              ft.id AS task_id
            FROM flow_instance fi
            LEFT JOIN flow_definition fd
              ON fi.definition_id = fd.id
            LEFT JOIN flow_task ft
              ON fi.id = ft.instance_id
            WHERE
              fi.business_id = '${item.businessId}'
              AND fi.del_flag = '0'
              AND fd.del_flag = '0'
              AND ft.del_flag = '0'
              AND ft.node_type = 1
            ORDER BY ft.create_time DESC
          `)
        if (flowInfo){
          item.taskId = flowInfo.taskId + ""
          item.approveType = flowInfo.flowCode
        }
   }
})

// 返回当前业务分支的标准结果。
return {
  list, total
}

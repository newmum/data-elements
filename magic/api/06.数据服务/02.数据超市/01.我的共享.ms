{
  "properties": {},
  "id": "ba237629207c463496217f6f8ddb5e27",
  "script": null,
  "groupId": "dataservice_resource_group_01",
  "name": "01.我的共享",
  "createTime": 1782955344058,
  "updateTime": 1787153756999,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/myShare",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"total\": 1,\n        \"list\": [\n            {\n                \"tid\": \"14254e9a22e941168bc119e4581fa1e0\",\n                \"applyFormId\": \"dadce475dea5404fa11cf0d8348b6317\",\n                \"catalogId\": \"85368b91972c4a35b83b8fa73e34e740\",\n                \"sortNo\": 0,\n                \"tenantId\": null,\n                \"revision\": null,\n                \"createdTime\": \"2026-03-24 16:11:56\",\n                \"updatedBy\": \"1995681734511165441\",\n                \"updatedTime\": \"2026-03-24 16:11:56\",\n                \"createdBy\": \"1995681734511165441\",\n                \"isDel\": 0,\n                \"catalogOrgId\": \"2026503859392086017\",\n                \"catalogOrgPath\": null,\n                \"catalogName\": \"生成目录信息(sys_gen_info)\"\n            }\n        ]\n    }\n}",
  "description": "按当前登录人员筛选其发起或参与的共享申请，结合关键词与流程状态返回分页列表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.我的共享
// 路由：POST /dws/market/myShare
// 业务说明：按当前登录人员筛选其发起或参与的共享申请，结合关键词与流程状态返回分页列表。
import "@post:/sym/user/me" as me;
import "@/common/localCache" as localCache


// 解析请求体并为可选参数设置默认值。
var pageNum = body?.pageNum::int(1)
var pageSize = body?.pageSize::int(20)
var keyword = body?.keyword
var flowStatus = body?.flowStatus

var offsetLimit = pageNum == 0 ? 0 : (pageNum - 1) * pageSize

var userInfo = me()
var sharelyOrgId = userInfo?.orgId

if (sharelyOrgId == null || sharelyOrgId == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, "当前用户组织信息为空"
}

var sql = """
    select * from data_apply_form_catalog_rela  rela
    where 
    rela.catalog_org_id = #{sharelyOrgId}
    and is_del = 0
    order by created_time
"""

// 从 MySQL 业务表读取当前有效数据。
var pageList = db.page(sql,pageSize,offsetLimit)

if(pageList != null && pageList.list != null){
    pageList.list.each( item ->{
        item.catalogName = localCache(item.catalogId, "catalog")
    })
}

// 返回当前业务分支的标准结果。
return {
    total: pageList.total,
    list: pageList.list
}

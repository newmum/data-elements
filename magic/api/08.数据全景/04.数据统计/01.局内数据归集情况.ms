{
  "properties": {},
  "id": "d28034db5ada48bcbdf74959880f1a2e",
  "script": null,
  "groupId": "df01177ff7604bbfb813175969adba1b",
  "name": "01.局内数据归集情况",
  "createTime": 1782955343551,
  "updateTime": 1784437771675,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/innerStat",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": [\n        {\n            \"departId\": \"2058490591645663234\",\n            \"departName\": \"办公室\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 1\n        },\n        {\n            \"departId\": \"2058552064266862593\",\n            \"departName\": \"海洋经济处\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 2\n        },\n        {\n            \"departId\": \"2058552152884117505\",\n            \"departName\": \"防灾减灾处\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2058552216662704130\",\n            \"departName\": \"渔业与质量监督处\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 1\n        },\n        {\n            \"departId\": \"2058552271218016258\",\n            \"departName\": \"渔船渔港管理处\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2058552438872735746\",\n            \"departName\": \"科技与对外合作处\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2058552749586776065\",\n            \"departName\": \"福建省渔业资源监测中心\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2058552847548940290\",\n            \"departName\": \"福建省海洋预报台\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2058552907896586242\",\n            \"departName\": \"福建省海洋经济运行监测与评估中心\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2067450426957762560\",\n            \"departName\": \"海洋与渔业数据中心\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        },\n        {\n            \"departId\": \"2058493826078998529\",\n            \"departName\": \"福建省渔业减灾中心\",\n            \"dataHjCount\": 25148984,\n            \"categoryHjCount\": 84\n        },\n        {\n            \"departId\": \"2069338822711939072\",\n            \"departName\": \"福建省水产研究所\",\n            \"dataHjCount\": 0,\n            \"categoryHjCount\": 0\n        }\n    ]\n}",
  "description": "读取归集应用接口结果，筛出局内机构的数据归集情况并转换为大屏展示结构。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.局内数据归集情况
// 路由：GET /scene/data-count/innerStat
// 业务说明：读取归集应用接口结果，筛出局内机构的数据归集情况并转换为大屏展示结构。
import "@get:/scene/collect/app" as collectApp

// 解析 HTTP 导入响应（兼容 magic-api 自动包裹的 {code, data} 格式）
var unwrap = (result) => {
    if (result == null) { return null; }
    // 如果响应被 magic-api 框架包裹为 { code, message, data } 格式，提取 data 部分
    if (result.data != null && result.code != null) { return result.data; }
    // 返回当前业务分支的标准结果。
    return result;
}

// ==================== 构造 city_depart_hj（局内机构 → 部门汇总） ====================
var buildCityDepartHj = (jnResult) => {
    var data = unwrap(jnResult)
    var list = []
    if (data != null && data.list != null) {
        list = data.list
    }
    var cityDepartHj = []
    for (item in list) {
        cityDepartHj.add({
            "departId": item.orgId != null ? item.orgId : "",
            "departName": item.orgName != null ? item.orgName : "",
            "dataHjCount": item.dataHjCount != null ? item.dataHjCount : 0,
            "categoryHjCount": item.categoryHjCount != null ? item.categoryHjCount : 0
        })
    }
    // 返回当前业务分支的标准结果。
    return cityDepartHj
}
var pageSize = 20
// 返回当前业务分支的标准结果。
return buildCityDepartHj(collectApp())

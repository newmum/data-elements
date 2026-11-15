{
  "properties": {},
  "id": "9793b13bdb0d4d09bc1f909e7d1bd122",
  "script": null,
  "groupId": "af8d73aa8a2244a88474123d966e8ae5",
  "name": "04.角色兼容分页",
  "createTime": 1782660716040,
  "updateTime": 1786711183521,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/pageRole",
  "method": "GET",
  "parameters": [
    {
      "name": "ids",
      "value": null,
      "description": "真实数据示例参数：ids",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "appId",
      "value": null,
      "description": "真实数据示例参数：appId",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "1995678661281710081",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "codeNum",
      "value": null,
      "description": "真实数据示例参数：codeNum",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": "角色编码",
      "expression": null,
      "children": null
    },
    {
      "name": "name",
      "value": null,
      "description": "真实数据示例参数：name",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": "角色名称",
      "expression": null,
      "children": null
    },
    {
      "name": "offset",
      "value": null,
      "description": "真实数据示例参数：offset",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "0",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "limit",
      "value": null,
      "description": "真实数据示例参数：limit",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": "10",
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "options": [],
  "requestBody": "{\n    \"pageNum\": 1,\n    \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "管理页默认查询当前租户全部角色；仅在调用方明确传入 appId 时按应用筛选；涉及 rm_role_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "0",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "success",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "true",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": [
          {
            "name": "total",
            "value": "1",
            "description": "",
            "required": false,
            "dataType": "Integer",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": []
          },
          {
            "name": "list",
            "value": "",
            "description": "",
            "required": false,
            "dataType": "Array",
            "type": null,
            "defaultValue": null,
            "validateType": "",
            "error": "",
            "expression": "",
            "children": [
              {
                "name": "",
                "value": "",
                "description": "",
                "required": false,
                "dataType": "Object",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": [
                  {
                    "name": "id",
                    "value": "1995679968881147906",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "createId",
                    "value": "ECF8E1513080B87F51B9285F491915EA",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "updateId",
                    "value": "ECF8E1513080B87F51B9285F491915EA",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "deleted",
                    "value": "0",
                    "description": "",
                    "required": false,
                    "dataType": "Integer",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "createTime",
                    "value": "2025-12-02 10:22:37",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "updateTime",
                    "value": "2025-12-02 10:22:37",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "status",
                    "value": "0",
                    "description": "",
                    "required": false,
                    "dataType": "Integer",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "name",
                    "value": "管理员",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "codeNum",
                    "value": "admin",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "description",
                    "value": "admin",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "appId",
                    "value": "1995678661281710081",
                    "description": "",
                    "required": false,
                    "dataType": "String",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "sortNum",
                    "value": "0",
                    "description": "",
                    "required": false,
                    "dataType": "Integer",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  },
                  {
                    "name": "rowId",
                    "value": "1",
                    "description": "",
                    "required": false,
                    "dataType": "Integer",
                    "type": null,
                    "defaultValue": null,
                    "validateType": "",
                    "error": "",
                    "expression": "",
                    "children": []
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
================================
// 接口：04.角色兼容分页
// 路由：GET /sym/role/pageRole
// 业务说明：管理页默认查询当前租户全部角色；仅在调用方明确传入 appId 时按应用筛选；涉及 rm_role_t 等业务表。
import tenantAccounts
import cn.hutool.db.PageResult
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.NumberUtil;


try{
  tenantAccounts.session();
}catch(e) {
  // 参数或业务状态不满足要求时立即终止请求。
  exit -1, "账号未登入或登入已过期";  
}

// 查询参数
// 管理页默认查询当前租户全部角色；仅在调用方明确传入 appId 时按应用筛选。
var requestedAppId = body?.appId
var pageSizeValue = (pageSize || limit || 10).asInt()
var pageNo = (page || 1).asInt()
var offsetValue = (offset || ((pageNo - 1) * pageSizeValue)).asInt()

// 构建查询条件
var result = db.table("rm_role_t").where().eq("deleted", 0);
if (requestedAppId != null && (requestedAppId + '').trim() != '') {
    result = result.eq("app_id", requestedAppId);
}

// 根据ID列表查询
if (ids && ids != "") {
    var idList = ids.split(",");
    result = result.in("id", idList);
}

// 角色编码模糊查询
if (codeNum && codeNum != "") {
    result = result.like("code_num", "%" + codeNum + "%");
}

// 角色名称模糊查询
if (name && name != "") {
    result = result.like("name", "%" + name + "%");
}

// 分页查询
var roleList = result.orderBy("sort_num")
                    .page(pageSizeValue, offsetValue);

// 返回结果
return roleList;

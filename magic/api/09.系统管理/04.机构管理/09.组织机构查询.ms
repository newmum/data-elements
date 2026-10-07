{
  "properties": {},
  "id": "96eadc03d49e46c9a9a039b368a5a5be",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "09.组织机构查询",
  "createTime": 1782660716261,
  "updateTime": 1784437771359,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"parentId\": \"2026503859392086017\"\n}",
  "headers": [
    {
      "name": "token",
      "value": "dddb9792-6028-4a3f-9ce4-e2b371101033",
      "description": null,
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "paths": [],
  "responseBody": null,
  "description": "按当前租户和父机构标识读取未删除的组织机构，返回层级列表。",
  "requestBodyDefinition": {
    "name": "root",
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
        "name": "parentId",
        "value": "002004",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  },
  "responseBodyDefinition": null
}
================================
// 接口：09.组织机构查询
// 路由：POST /sym/org/list
// 业务说明：按当前租户和父机构标识读取未删除的组织机构，返回层级列表。
import log
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.NumberUtil

// 没有入参就注销退出登录
// }
var parentId = body?.parentId? body.parentId : null
var orgSQL;
if(parentId == null) {
    //查询机构根节点
    orgSQL = """
        select * from rm_org_t where LENGTH(serial_number) = 8
        order by sort_num asc
    """
}else{
    //查询子级机构
    orgSQL = """
        select * from rm_org_t where parent_id = #{parentId}
        order by sort_num asc
    """
}

//先读取数据库，后面考虑读取缓存
var orgList = db.select(orgSQL)
// 返回当前业务分支的标准结果。
return orgList

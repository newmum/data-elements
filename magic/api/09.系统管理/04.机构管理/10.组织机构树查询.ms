{
  "properties": {},
  "id": "14c675168c134d4ea7d0c3ac3bc98131",
  "script": null,
  "groupId": "c1736799c6af43359a75e6764601931f",
  "name": "10.组织机构树查询",
  "createTime": 1782660716280,
  "updateTime": 1787283925821,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getOrgTree",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "机构树查询：仅返回当前根机构下的有效子树；不包含虚拟“全部机构”节点；使用请求参数 parentId；涉及 rm_org_t 等业务表。",
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
                "value": "2026504929417756673",
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
                "name": "name",
                "value": "省厅",
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
                "name": "parentId",
                "value": "2026503859392086017",
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
                "name": "nature",
                "value": "3",
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
                "name": "serialNumber",
                "value": "00060001",
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
                "name": "orgPath",
                "value": "福建省公安/省厅",
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
                "name": "description",
                "value": "",
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
                "name": "dataSrc",
                "value": "null",
                "description": "",
                "required": false,
                "dataType": "Object",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "deptType",
                "value": "null",
                "description": "",
                "required": false,
                "dataType": "Object",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "deptCode",
                "value": "",
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
                "name": "nationalCode",
                "value": "",
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
                "name": "nationalName",
                "value": "",
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
                "name": "regionCode",
                "value": "",
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
                "name": "nodeType",
                "value": "",
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
                "name": "originTypes",
                "value": "null",
                "description": "",
                "required": false,
                "dataType": "Object",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "originIds",
                "value": "",
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
                "name": "label",
                "value": "省厅",
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
                "name": "leaderOrgId",
                "value": "null",
                "description": "",
                "required": false,
                "dataType": "Object",
                "type": null,
                "defaultValue": null,
                "validateType": "",
                "error": "",
                "expression": "",
                "children": []
              },
              {
                "name": "value",
                "value": "2026504929417756673",
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
                "name": "children",
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
                        "value": "2026504991392792578",
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
                        "name": "name",
                        "value": "情指中心",
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
                        "name": "parentId",
                        "value": "2026504929417756673",
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
                        "name": "nature",
                        "value": "2",
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
                        "name": "serialNumber",
                        "value": "000600010001",
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
                        "name": "orgPath",
                        "value": "福建省公安/省厅/情指中心",
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
                        "name": "description",
                        "value": "",
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
                        "name": "dataSrc",
                        "value": "null",
                        "description": "",
                        "required": false,
                        "dataType": "Object",
                        "type": null,
                        "defaultValue": null,
                        "validateType": "",
                        "error": "",
                        "expression": "",
                        "children": []
                      },
                      {
                        "name": "deptType",
                        "value": "null",
                        "description": "",
                        "required": false,
                        "dataType": "Object",
                        "type": null,
                        "defaultValue": null,
                        "validateType": "",
                        "error": "",
                        "expression": "",
                        "children": []
                      },
                      {
                        "name": "deptCode",
                        "value": "",
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
                        "name": "nationalCode",
                        "value": "",
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
                        "name": "nationalName",
                        "value": "",
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
                        "name": "regionCode",
                        "value": "",
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
                        "name": "nodeType",
                        "value": "",
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
                        "name": "originTypes",
                        "value": "null",
                        "description": "",
                        "required": false,
                        "dataType": "Object",
                        "type": null,
                        "defaultValue": null,
                        "validateType": "",
                        "error": "",
                        "expression": "",
                        "children": []
                      },
                      {
                        "name": "originIds",
                        "value": "",
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
                        "name": "label",
                        "value": "情指中心",
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
                        "name": "leaderOrgId",
                        "value": "null",
                        "description": "",
                        "required": false,
                        "dataType": "Object",
                        "type": null,
                        "defaultValue": null,
                        "validateType": "",
                        "error": "",
                        "expression": "",
                        "children": []
                      },
                      {
                        "name": "value",
                        "value": "2026504991392792578",
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
                        "name": "children",
                        "value": "",
                        "description": "",
                        "required": false,
                        "dataType": "Array",
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
    ]
  }
}
================================
// 接口：10.组织机构树查询
// 路由：POST /sym/org/getOrgTree
// 业务说明：机构树查询：仅返回当前根机构下的有效子树；不包含虚拟“全部机构”节点；使用请求参数 parentId；涉及 rm_org_t 等业务表。
// 机构树查询：仅返回当前根机构下的有效子树；不包含虚拟“全部机构”节点。
import "@post:/sym/user/me" as me

var rootId = body?.parentId || orgRootId || me().orgRootId || 'ROOT';
var rows = db.table('rm_org_t').where().eq('deleted', 0).orderBy('sort_num').select();
var nodes = {};
for (row in rows) {
    var node = {};
    node.putAll(row);
    node.value = node.id;
    node.label = node.name;
    node.children = [];
    nodes[node.id] = node;
}

var roots = [];
for (nodeId in nodes.keySet()) {
    var node = nodes[nodeId];
    if (node.parentId == rootId) {
        roots.add(node);
    } else if (nodes[node.parentId] != null) {
        nodes[node.parentId].children.add(node);
    }
}
return roots;

{
  "properties": {},
  "id": "9daad772b4da4fc0923d0d97bd719d22",
  "script": "// 共享函数：11.列表字段分组映射\n// 函数路径：/common/list-group-map\n// 业务说明：遍历输入列表并按指定字段构建分组映射，返回每个字段值对应的原始记录集合。\n// 函数输入：list：待处理列表；key：键字段名。\nvar map = {\n\n};\nfor (val in list) {\n    var tmp = val[key];\n    if (!!!map[tmp]) {\n        map[tmp] = new ArrayList();\n    }\n    map[tmp].add(val);\n}\nreturn map\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "11.列表字段分组映射",
  "createTime": 1782660730420,
  "updateTime": 1667552802718,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list-group-map",
  "description": "遍历输入列表并按指定字段构建分组映射，返回每个字段值对应的原始记录集合。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "list",
      "value": null,
      "description": "待处理列表",
      "required": false,
      "dataType": "String",
      "type": "java.util.Collection",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "key",
      "value": null,
      "description": "键字段名",
      "required": false,
      "dataType": "String",
      "type": "java.lang.String",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
{
  "properties": {},
  "id": "0a3fae28bd16437aa2473eb2c31f6a7b",
  "script": "// 共享函数：12.列表转键值映射\n// 函数路径：/common/list-to-map\n// 业务说明：以指定字段作为键，将列表转为对象映射；可选择整行或指定值字段，空键记录被跳过。\n// 函数输入：list：待处理列表；key：键字段名；value：值字段名。\nvar res = {}\nfor (val in list) {\n    var tmpKey = val[key];\n    if (!!!tmpKey) {\n        continue;\n    }\n    if (!!!value) {\n        res[tmpKey] = val\n        continue;\n    }\n    res[tmpKey] = val[value]\n}\nreturn res;\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "12.列表转键值映射",
  "createTime": 1782660730429,
  "updateTime": 1662010846696,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list-to-map",
  "description": "以指定字段作为键，将列表转为对象映射；可选择整行或指定值字段，空键记录被跳过。",
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
    },
    {
      "name": "value",
      "value": null,
      "description": "值字段名",
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
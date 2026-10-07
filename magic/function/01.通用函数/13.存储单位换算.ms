{
  "properties": {},
  "id": "655d56e94cdc4238ba0a7f7f942c8cb3",
  "script": "// 共享函数：13.存储单位换算\n// 函数路径：/common/unit-compute\n// 业务说明：按单位类别和输入数值换算存储容量等计量值，返回转换后的数值与单位。\n// 函数输入：countNum：待换算数量；typeCode：单位类别；unit：如张 、 条 个。\nimport java.math.BigDecimal\nvar res = {\n\n}\nif (typeCode == 'storage') {\n    if (countNum < 1024L) {\n        res.value = countNum;\n        res.unit = 'Kb';\n        return res;\n    }\n    if (countNum < 1024 * 1024L) {\n        res.value = new BigDecimal(countNum).divide(new BigDecimal(1024L), 2,BigDecimal.ROUND_HALF_UP);\n        res.unit = 'Mb';\n        return res;\n    }\n    if (countNum < 1024 * 1024 * 1024L) {\n        res.value = new BigDecimal(countNum).divide(new BigDecimal(1024 * 1024L), 2,BigDecimal.ROUND_HALF_UP);\n        res.unit = 'Gb';\n        return res;\n    }\n    res.value = new BigDecimal(countNum).divide(new BigDecimal(1024 * 1024 * 1024L), 2,BigDecimal.ROUND_HALF_UP);\n    res.unit = 'Tb';\n    \n    return res;\n}\nif (!!!unit) {\n    unit = '';\n}\nif (countNum < 10000) {\n    res.value = countNum;\n    res.unit = unit;\n    return res;\n}\nif (countNum < 100000000L) {\n    res.value = new BigDecimal(countNum).divide(new BigDecimal(10000L), 2,BigDecimal.ROUND_HALF_UP);\n    res.unit = '万'+unit;\n    return res;\n}\nif (countNum < 1000000000000L) {\n    res.value = new BigDecimal(countNum).divide(new BigDecimal(100000000L), 2,BigDecimal.ROUND_HALF_UP);\n    res.unit = '亿'+unit;\n    return res;\n}\nif (countNum < 10000000000000000L) {\n    res.value = new BigDecimal(countNum).divide(new BigDecimal(1000000000000L), 2,BigDecimal.ROUND_HALF_UP);\n    res.unit = '万亿'+unit;\n    return res;\n}\nres.value = new BigDecimal(countNum).divide(new BigDecimal(10000000000000000L), 2,BigDecimal.ROUND_HALF_UP);\nres.unit = '亿亿'+unit;\nreturn res\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "13.存储单位换算",
  "createTime": 1782660730439,
  "updateTime": 1724119382041,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/unit-compute",
  "description": "按单位类别和输入数值换算存储容量等计量值，返回转换后的数值与单位。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "countNum",
      "value": null,
      "description": "待换算数量",
      "required": false,
      "dataType": "String",
      "type": "java.lang.Number",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "typeCode",
      "value": null,
      "description": "单位类别",
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
      "name": "unit",
      "value": null,
      "description": "如张 、 条 个",
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
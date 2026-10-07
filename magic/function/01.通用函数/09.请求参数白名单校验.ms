{
  "properties": {},
  "id": "69f8ec0221c341a1ad8ac18138b5428d",
  "script": "// 共享函数：09.请求参数白名单校验\n// 函数路径：/common/check-params\n// 业务说明：逐项比较请求参数名与允许列表；出现未登记参数时抛出业务错误，合法或空参数返回校验通过。\n// 函数输入：params：待校验的请求参数；validParams：允许的参数名集合。\nimport com.linewell.dataasset.common.exception.BizException;\nif (!!!params) {\n  return true;\n}\nif (!!!validParams || validParams.size() <= 0) {\n  return true;\n}\nfor (key,value in params) {\n  if (!validParams.contains(key)) {\n    throw new BizException(10081,\"参数不合法\",\"参数不合法\")\n  }\n}\nreturn true\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "09.请求参数白名单校验",
  "createTime": 1782660730398,
  "updateTime": 1724118197703,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "check-params",
  "description": "逐项比较请求参数名与允许列表；出现未登记参数时抛出业务错误，合法或空参数返回校验通过。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "params",
      "value": null,
      "description": "待校验的请求参数",
      "required": false,
      "dataType": "String",
      "type": "java.util.Map",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "validParams",
      "value": null,
      "description": "允许的参数名集合",
      "required": false,
      "dataType": "String",
      "type": "java.util.Collection",
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
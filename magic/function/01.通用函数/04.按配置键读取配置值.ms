{
  "properties": {},
  "id": "59fc7ba68a7e4231a211e23423261c56",
  "script": "// 共享函数：04.按配置键读取配置值\n// 函数路径：/common/getBykey\n// 业务说明：按 configureKey 从 sys_configure 查询配置值，使用 Magic 缓存减少重复查询；配置不存在时返回空字符串。\n// 函数输入：configureKey：sys_configure 中的配置键。\nvar configure = db.cache(`configure:${configureKey}`).selectOne(\"\"\"\n     select configure_value from sys_configure where configure_key = #{configureKey}\n\"\"\")\nreturn configure == null ? \"\" : configure.get(\"configureValue\")\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "04.按配置键读取配置值",
  "createTime": 1782660730390,
  "updateTime": 1763253090092,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/getBykey",
  "description": "按 configureKey 从 sys_configure 查询配置值，使用 Magic 缓存减少重复查询；配置不存在时返回空字符串。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "configureKey",
      "value": null,
      "description": "sys_configure 中的配置键",
      "required": false,
      "dataType": "String",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ]
}
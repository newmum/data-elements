{
  "properties": {},
  "id": "a5a47aad30264c5da7990b3244f2a1d7",
  "script": "// 共享函数：16.读取通用配置项\n// 函数路径：/common/configData\n// 业务说明：读取公共和非敏感运行配置；敏感凭据由对应集成接口按需读取 Nacos，不在共享函数中返回。\n// 函数输入：无显式参数。\nimport systemConfig\n\nvar config = systemConfig.runtimeSettings()\nreturn config\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "16.读取通用配置项",
  "createTime": 1782660730502,
  "updateTime": 1783321121140,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/configData",
  "description": "读取公共和非敏感运行配置；敏感凭据由对应集成接口按需读取 Nacos，不在共享函数中返回。",
  "returnType": null,
  "mappingPath": null,
  "parameters": []
}
{
  "properties" : { },
  "id" : "adf705ad4a2d4a1c9b8c131a3e5866e4",
  "script" : "import metadata;\r\nimport cn.hutool.core.codec.Base64;\r\nimport log;\r\n\r\n// 86环境的dataassets_survey库\r\nvar datasource = db.table(\"asset_datasource_t\").where().eq(\"tid\",'1934562413636751360').selectOne();\r\n// 获取这个库下的所有表信息\r\n\r\nvar tables = metadata.listTables(datasource.dbType, {\r\n    \"host\": datasource.dbIp,\r\n    \"port\": datasource.dbPort.asInt(),\r\n    \"user\": datasource.dbUser,\r\n    \"password\": Base64.decodeStr(datasource.dbPassword),\r\n    \"database\": datasource.dbName,\r\n    \"charset\": \"UTF-8\"\r\n});\r\nlog.debug(\"tables->{}\",tables)  \r\n\r\nvar result = metadata.deleteDataAll(tables, datasource.dbType, {\r\n    \"host\": datasource.dbIp,\r\n    \"port\": datasource.dbPort.asInt(),\r\n    \"user\": datasource.dbUser,\r\n    \"password\": Base64.decodeStr(datasource.dbPassword),\r\n    \"database\": datasource.dbName,\r\n    \"charset\": \"UTF-8\"\r\n});\r\nlog.debug(\"执行清理结果{}张表\",result)  // 返回1表示已清理表的个数\r\nreturn result;\r\n",
  "groupId" : "75d46bd7e4d74e538aad41249f8a50a6",
  "name" : "自动清理某数据库下的所有表数据",
  "createTime" : 1782660730513,
  "updateTime" : 1755829692298,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "clearTableData",
  "cron" : "0 0 0 * * ?",
  "enabled" : false,
  "description" : null
}
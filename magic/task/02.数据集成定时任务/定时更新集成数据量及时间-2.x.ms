{
  "properties" : { },
  "id" : "83507b9a014244d0b5120cfc75e2c963",
  "script" : "var integrationData = db.firmament.camel().select(\r\n    \"\"\"\r\n    SELECT\r\n        tts.processor_state as processor_state,\r\n        itc.data_count as data_count,\r\n        concat(itc.schema_name,'.',itc.table_name_en) as table_name\r\n    FROM\r\n        integration_table_time_statistics tts\r\n        JOIN integration_table_count itc on tts.process_group_id = itc.job_id\r\n    \"\"\"\r\n)\r\n\r\nvar assetTable = db.camel().select(\"\"\"\r\nSELECT\r\n  concat(datasource.`name`,'.',data_table.`name`) as table_name,\r\n  t.row_total as data_count,\r\n  t.htProcessorState as processor_state\r\nFROM\r\n  dataassets.da_dataasset_table_t t\r\n  JOIN dataassets.da_dataasset_t datasource ON t.datasource_id = datasource.tid\r\n  JOIN dataassets.da_dataasset_t data_table on t.tid = data_table.tid \r\n\"\"\")\r\n\r\nvar list = []\r\n\r\nfor(integrationVal in integrationData){\r\n    for(assetVal in assetTable){\r\n        if (integrationVal.tableName == assetVal.tableName){\r\n            if (integrationVal.processorState != assetVal.processorState && integrationVal.dataCount != assetVal.dataCount){\r\n                list.add(integrationVal)\r\n            }\r\n        }\r\n    }\r\n}\r\nfor(val in list){\r\n    db.update(\"\"\"\r\n    UPDATE dataassets.da_dataasset_table_t AS t\r\n    JOIN da_dataasset_t AS datasource ON t.datasource_id = datasource.tid\r\n    JOIN da_dataasset_t AS data_table ON t.tid = data_table.tid\r\n    SET \r\n        t.row_total = #{val.dataCount}, \r\n        t.htProcessorState =#{val.processorState}\r\n    WHERE \r\n        CONCAT(datasource.`name`, '.', data_table.`name`) = #{val.tableName};\r\n    \"\"\")\r\n}\r\n\r\n",
  "groupId" : "4caa8ec9cb88479aa42cbbc42ea4771d",
  "name" : "定时更新集成数据量及时间-2.x",
  "createTime" : 1782660730525,
  "updateTime" : 1746695647882,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "/dataCount2",
  "cron" : "* * 0/2 * * ? ",
  "enabled" : false,
  "description" : "定时更新集成的数据量和时间到资产table表中"
}
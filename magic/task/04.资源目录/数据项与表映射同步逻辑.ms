{
  "properties" : { },
  "id" : "d21d01d33743450bbd322bdfd8c5ff67",
  "script" : "import log;\r\n\r\n// 数据库id 仅操作的数据库范围\r\nvar dbId = '1934822166271102976';\r\n\r\nvar sql = `select \r\nc.tid as cid,c.target_table_id as tid\r\nfrom dataassets_registration.asset_data_catalog_t c\r\nINNER JOIN dataassets_registration.asset_table_t t on t.table_name = c.source_name_en\r\nwhere t.belong_data_source_id = #{dbId} and c.target_table_id is not null\r\n`;\r\n\r\nvar res = db.camel().select(sql)\r\n\r\nfor (item in res) {\r\n    log.debug(\"{}\",item);\r\n    var getColSql = `SELECT  c.tid  AS cid,i.tid  AS tid,LOWER(i.col_en) AS i_col_en\r\n            FROM    dataassets_registration.asset_table_column_t      AS c\r\n            JOIN    dataassets_registration.asset_data_catalog_item_t AS i\r\n            ON LOWER(c.column_name_en) = LOWER(i.col_en)\r\n            WHERE   c.table_id        = #{item.tid}\r\n            AND   i.data_catalog_id = #{item.cid}`;\r\n    var tempCol = db.camel().select(getColSql)\r\n    log.debug(\"{}\",tempCol);\r\n\r\n    // 修改当前资源目录与表大小写匹配的映射关系 tid = 资源目录id | cid = 表id\r\n    for (col in tempCol) {\r\n        db.update(\"\"\"\r\n         UPDATE dataassets_registration.asset_table_column_t AS t\r\n         SET t.data_catalog_item_mount_id = #{col.tid} \r\n         WHERE t.tid = #{col.cid}\r\n          \"\"\")\r\n    }\r\n    \r\n}\r\n\r\nreturn res",
  "groupId" : "34f1d1bf50ba4aa58228a74ad8ce0738",
  "name" : "数据项与表映射同步逻辑",
  "createTime" : 1782660730547,
  "updateTime" : 1754466415447,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "syncTableAndCategoryCol",
  "cron" : "0 0 0 * * ?",
  "enabled" : false,
  "description" : null
}
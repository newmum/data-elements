{
  "properties" : { },
  "id" : "copy1754456251041d79064",
  "script" : "import log;\r\n\r\nvar dbId = '1934822166271102976';\r\n\r\nvar sql = `select \r\nc.tid as cid,t.tid as tid\r\nfrom dataassets_registration.asset_data_catalog_t c\r\nINNER JOIN dataassets_registration.asset_table_t t on t.table_name = c.source_name_en\r\nwhere t.belong_data_source_id = #{dbId}`;\r\n\r\nvar res = db.camel().select(sql)\r\nfor (item in res) {\r\n    log.debug(\"{}\",item);\r\n    db.update(\"\"\"\r\n        UPDATE dataassets_registration.asset_data_catalog_t AS t\r\n        SET t.target_table_id = #{item.tid} \r\n        WHERE t.tid = #{item.cid} and t.target_table_id is null\r\n    \"\"\")\r\n}\r\n\r\nreturn res",
  "groupId" : "34f1d1bf50ba4aa58228a74ad8ce0738",
  "name" : "某库与目前登记的资源目录匹配挂接",
  "createTime" : 1782660730558,
  "updateTime" : 1756779312955,
  "lock" : null,
  "createBy" : null,
  "updateBy" : null,
  "path" : "syncTableAndCategory",
  "cron" : "0 0 0 * * ?",
  "enabled" : false,
  "description" : null
}
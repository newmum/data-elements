{
  "properties": {},
  "id": "222af8050ddb4935be8d47a0669bce62",
  "script": "// 共享函数：03.模型条件查询\n// 函数路径：/common/model-search\n// 业务说明：根据模型字段和筛选条件构造查询结果，供模型驱动的数据列表接口复用。\n// 函数输入：modelCode：数据模型名；columns：查询字段，以,隔开；conditions：查询条件集合。\nconst dbKey = dbKey || \"\";\nvar model = db[dbKey].selectOne(\"\"\"\nselect model_script\nfrom dataassets_baseline.db_model_t\nwhere model_code=#{modelCode}\n\"\"\");\nreturn model.modelScript + \"\"\"\n<!-- 所有有效的查询条件 -->\n  <if test=\"conditions != null and conditions.size() > 0\">\n    <trim prefix=\"AND (\" suffix=\")\" prefixOverrides=\"AND\" suffixOverrides=\"AND\">\n      <foreach collection=\"conditions\" item=\"item\">\n        <if test=\"item.condition == 'eq'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} = #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'ne'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} &lt;&gt; #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'lt'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} &lt; #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'gt'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} &gt; #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'lte'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} &lt;= #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'gte'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} &gt;= #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'in'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} IN\n          <foreach collection=\"item.value\" item=\"val\" open=\"(\" separator=\",\" close=\")\">\n            #{val}\n          </foreach> AND\n        </if>\n        <if test=\"item.condition == 'notIn'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} NOT IN\n          <foreach collection=\"item.value\" item=\"val\" open=\"(\" separator=\",\" close=\")\">\n            #{val}\n          </foreach> AND\n        </if>\n        <if test=\"item.condition == 'like'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} LIKE #{item.value} AND\n        </if>\n        <if test=\"item.condition == 'notLike'\">\n          ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} NOT LIKE #{item.value} AND\n        </if>\n      </foreach>\n    </trim>\n  </if>\n\n<!-- 分组查询 -->\n<if test=\"conditions != null and conditions.size() > 0 \n    and conditions.some(item => item.condition == 'groupBy')\">\n  GROUP BY \n  <trim suffixOverrides=\",\">\n    <foreach collection=\"conditions\" item=\"item\" separator=\",\">\n      <if test=\"item.condition == 'groupBy'\">\n        ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()}\n      </if>\n    </foreach>\n  </trim>\n</if>\n\n<!-- 排序查询 -->\n<if test=\"conditions != null and conditions.size() > 0 \n    and conditions.some(item => item.condition == 'orderBy')\">\n  ORDER BY \n  <trim suffixOverrides=\",\">\n    <foreach collection=\"conditions\" item=\"item\" separator=\",\">\n      <if test=\"item.condition == 'orderBy'\">\n        ${item.field.replaceAll('([A-Z])', '_$1').toLowerCase()} ${item.value}\n      </if>\n    </foreach>\n  </trim>\n</if>\n\"\"\"\n",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "03.模型条件查询",
  "createTime": 1782660730368,
  "updateTime": 1762877706217,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/model-search",
  "description": "根据模型字段和筛选条件构造查询结果，供模型驱动的数据列表接口复用。",
  "returnType": null,
  "mappingPath": null,
  "parameters": [
    {
      "name": "modelCode",
      "value": null,
      "description": "数据模型名",
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
      "name": "columns",
      "value": null,
      "description": "查询字段，以,隔开",
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
      "name": "conditions",
      "value": null,
      "description": "查询条件集合",
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
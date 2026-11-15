{
  "properties": {},
  "id": "cc92b11b7eb845768205468cb139d247",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "76.元数据目录查询范围",
  "path": "/metadataCatalogScope",
  "description": "共用当前会话的角色数据范围。",
  "returnType": "Object",
  "parameters": [
    {
      "name": "alias",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "scope",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import tableRead

// 元数据目录和字段浏览共用当前会话的数据源权限条件，覆盖计数、分页和批量校验。
if (scope?.scope == 'ALL') {
    return tableRead.all([])
}
if (scope?.scope == 'ORG') {
    return tableRead.in(tableRead.column(alias, 'org_id', 'TEXT'), scope.organizationIds || [])
}
if (scope?.scope == 'OWNER') {
    return tableRead.equal(tableRead.column(alias, 'created_by', 'TEXT'), scope.userId)
}
return tableRead.any([])

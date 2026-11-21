{
  "id": "collect_fn_tablepage_v1",
  "groupId": "collection_fn_group_v1",
  "name": "06.装配采集表清单",
  "path": "/tablepage",
  "parameters": [
    {
      "name": "page",
      "dataType": "Object",
      "required": false,
      "description": "结构化分页结果"
    },
    {
      "name": "args",
      "dataType": "Object",
      "required": false,
      "description": "请求分页参数"
    }
  ],
  "description": "已保存表和新增表共用原分页响应。"
}
================================
// /collect/tablepage 返回当前页表头，保留首选中文名和原接口的记录数缺省值。
var first = values => {
    for (value in values) {
        if (value != null && (value + '').trim() != '') {
            return (value + '').trim()
        }
    }
    return ''
}
var rows = page.rows.map(row => ({
    tid: first([row.tid]), tableName: first([row.table_name]),
    tableNameCn: first([row.table_name_cn, row.table_comment, row.table_name]),
    tableType: first([row.table_type]), fieldCount: row.field_count == null ? 0 : row.field_count,
    recordCount: 0, createdAt: row.created_time, updatedAt: row.updated_time
}))
return {pageNo: args.pageNo, pageSize: args.pageSize, total: page.total, rows: rows, list: rows}

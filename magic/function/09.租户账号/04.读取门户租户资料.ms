{
  "id": "portal_fn_profile_v1",
  "groupId": "ga_local_accounts_functions_01",
  "name": "04.读取门户租户资料",
  "path": "/profile",
  "parameters": [
    {
      "name": "tenantId",
      "dataType": "Object",
      "required": false,
      "description": "已经过授权的租户ID"
    },
    {
      "name": "activeOnly",
      "dataType": "Object",
      "required": false,
      "description": "切换时只读取启用租户"
    }
  ],
  "description": "当前会话、可用租户和切换共用控制库租户投影。按可信租户主键查询，复用原字段及空对象契约。"
}
================================
import db
import identityRuntime

// /tenant-account/profile 读取当前已授权的租户资料。切换时只返回启用租户。
return identityRuntime.control(() => {
    var query = db.normal().table('sym_tenant_t').columns('tid', 'code', 'name', 'status', 'init_flag', 'isolation_mode')
        .where().eq('tid', tenantId).eq('is_del', 0)
    if (activeOnly) {
        query.eq('status', 1)
    }
    var row = query.selectOne()
    if (row == null) {
        return {}
    }
    var result = {tid: row.tid, code: row.code, name: row.name}
    for (field in ['status', 'init_flag', 'isolation_mode']) {
        if (row[field] != null) {
            result[field] = row[field]
        }
    }
    return result
})

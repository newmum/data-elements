{
  "properties": {},
  "id": "a1000000000000000000000000000017",
  "script": null,
  "groupId": "d5ce0d4911e94d7bb3c22a9f67547406",
  "name": "18.责任组织选项",
  "createTime": 1790495138845,
  "updateTime": 1790498525706,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/options/organizations",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前登录租户读取质量整改责任组织选项，无需请求参数。排除 ROOT 和已删除记录，兼容删除标志为空的历史记录，按组织序列编号和名称升序返回组织 ID、名称及父组织 ID。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import tenantRuntime

// POST /dwm/quality/options/organizations 为质量整改提供责任组织选项。
// 无需请求参数，返回当前租户组织的 tid、name 和 parentId，无可选组织时返回空数组。

// 只使用登录租户，兼容删除标志为空的历史组织并排除根占位节点。
var tenantId = tenantRuntime.id()
var organizations = db.table('rm_org_t')
    .columns('id', 'name', 'parent_id')
    .where()
    .eq('tenant_id', tenantId)
    .and(active => active.isNull('deleted').or().eq('deleted', 0))
    .ne('id', 'ROOT')
    .orderBy('serial_number', 'asc')
    .orderBy('name', 'asc')
    .select()

// 保持工单选择器沿用的组织 ID 字段名。
var options = []
for (organization in organizations) {
    options.add({
        tid: organization.id,
        name: organization.name,
        parentId: organization.parentId
    })
}
return options

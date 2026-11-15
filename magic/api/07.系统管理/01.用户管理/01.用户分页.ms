{
  "properties": {
    "localAccountRetirement": "v1"
  },
  "id": "f75db35437b546f8a8c8e4eda6740e33",
  "script": null,
  "groupId": "1ac60b4ccbc142e89dc1bf575e674d20",
  "name": "01.用户分页",
  "createTime": 1782660716553,
  "updateTime": 1787153759518,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n  \"pageNum\": 1,\n  \"pageSize\": 20\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n  \"list\": [],\n  \"total\": 0\n}",
  "description": "当前租户本地用户分页。orgId 缩小机构范围。includeOnline 为 true 时返回在线标记，online 为布尔值时在分页前筛选有效会话或离线用户。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import db
import java.lang.Boolean
import java.util.HashSet
import tableRead
import tableSort
import tenantAccounts
import tenantRuntime

// 按当前租户和机构分页读取本地账号。用户管理可在分页前按有效会话筛选，
// 角色和主机构只按当前页用户 ID 批量补充。
try {
    tenantAccounts.session()
} catch (e) {
    exit -1, "账号未登录或登录已过期"
}

var filters = body || {}
var tenantId = tenantRuntime.id()
if (!tenantId) { exit 401, '未获取到当前租户' }
if (!tenantAccounts.isLocal(tenantId)) { exit 403, '当前租户未启用本地账号' }

var text = value => value == null ? '' : (value + '').trim()
var pageNum = Math.max(1, filters.pageNum || 1)
var pageSize = Math.min(200, Math.max(1, filters.pageSize || 200))
var ids = filters.ids
if (ids != null && ids.size() > 200) { exit 400, '一次最多查询200个用户' }
if (ids != null && ids.size() == 0) { return {list: [], total: 0} }
var online = filters.online
if (online != null && !(online instanceof Boolean)) {
    exit 400, '在线状态必须为布尔值'
}

var users = tableRead.from('rm_user_t', 'u', tenantId)
var col = (column, kind) => tableRead.column('u', column, kind)
users.where(tableRead.equal(col('deleted', 'NUMBER'), 0))
users.where(tableRead.like(col('user_name', 'TEXT'), '%' + text(filters.userName) + '%'))
users.where(tableRead.like(col('real_name', 'TEXT'), '%' + text(filters.realName) + '%'))
if (ids != null) { users.where(tableRead.in(col('id', 'TEXT'), ids)) }

// 使用同租户有效机构和分组后的成员关系限定范围，重复关系不增加分页行数。
var orgId = text(filters.orgId)
if (orgId != '') {
    var members = tableRead.from('rm_user_org_rela_t', 'member', tenantId)
    var memberId = tableRead.column('member', 'user_id', 'TEXT')
    var activeOrgId = tableRead.column('active_org', 'id', 'TEXT')
    members.leftJoin('rm_org_t', 'active_org', 'id', 'org_id', [
        tableRead.equal(tableRead.column('active_org', 'deleted', 'NUMBER'), 0)
    ])
    members.where(tableRead.equal(activeOrgId, orgId))
    users.leftJoinGrouped(members, 'org_members', 'user_id', 'id', {user_id: memberId}, [memberId])
    users.where(tableRead.not(tableRead.isNull(tableRead.column('org_members', 'user_id', 'TEXT'))))
}

var includeOnline = online != null || filters.includeOnline == true
var onlineIds = includeOnline ? tenantAccounts.onlineUserIds(tenantId) : []
if (online != null) {
    // 绑定会话账号 ID，避免把不受限的占位符数量交给数据库驱动。
    if (onlineIds.size() > 20000) { exit 503, '在线账号过多，暂无法按在线状态筛选' }
    var matchesSession = tableRead.in(col('id', 'TEXT'), onlineIds)
    users.where(online == true ? matchesSession : tableRead.not(matchesSession))
}

var fields = {
    id: col('id', 'TEXT'), realName: col('real_name', 'TEXT'), userName: col('user_name', 'TEXT'),
    phone: col('phone', 'TEXT'), gender: col('gender', 'TEXT'), status: col('status', 'NUMBER'),
    appFileId: col('app_file_id', 'TEXT'), createId: col('create_id', 'TEXT'), createTime: col('create_time', 'TIME'),
    updateTime: col('update_time', 'TIME'), idCard: col('id_card', 'TEXT'), dateBirth: col('date_birth', 'TIME'),
    nation: col('nation', 'TEXT'), officeAddress: col('office_address', 'TEXT'), jobNumber: col('job_number', 'TEXT'),
    authorizedStrength: col('authorized_strength', 'TEXT'), originPosition: col('origin_position', 'TEXT'),
    originRank: col('origin_rank', 'TEXT')
}
var sort = tableSort.field(filters, {userName: 'userName', realName: 'realName', createTime: 'createTime', updateTime: 'updateTime'}, 'createTime')
var page = users.page(fields, fields[sort], tableSort.direction(filters), 'id', 'asc', pageNum, pageSize, true, 200)
var userPage = {list: page.rows, total: page.total, pageNum: pageNum, pageSize: pageSize}
if (userPage.list.size() == 0) { return userPage }

var userIds = userPage.list.map(item => item.id)
var roleRelations = db.table("rm_user_role_rela_t").where()
    .eq('tenant_id',tenantId)
    .in("user_id", userIds)
    .select()
var roleNames = {}
if (roleRelations != null && roleRelations.size() > 0) {
    var roleIds = roleRelations.map(item => item.roleId)
    var roles = db.table("rm_role_t").where()
        .eq('tenant_id',tenantId)
        .in("id", roleIds)
        .eq("deleted", 0)
        .select()
    var namesById = {}
    for (role in roles) {
        namesById[role.id] = role.name
    }
    for (relation in roleRelations) {
        var roleName = namesById[relation.roleId]
        if (roleName != null && roleName != '') {
            roleNames[relation.userId] = roleNames[relation.userId] == null
                ? roleName
                : roleNames[relation.userId] + ',' + roleName
        }
    }
}

var orgRelations = db.table("rm_user_org_rela_t").where()
    .eq('tenant_id',tenantId).eq('job_type','1')
    .in("user_id", userIds)
    .select()
var orgIds = {}
if (orgRelations != null) {
    for (relation in orgRelations) {
        orgIds[relation.userId] = relation.orgId
    }
}

var sessionUsers = new HashSet(onlineIds)
for (user in userPage.list) {
    // 兼容历史表格字段，投影不读取数据库内的密码摘要。
    user.password = '******'
    user.roleNames = roleNames[user.id] || ''
    user.orgId = orgIds[user.id] || ''
    if (includeOnline) { user.online = sessionUsers.contains(user.id + '') }
}
return userPage

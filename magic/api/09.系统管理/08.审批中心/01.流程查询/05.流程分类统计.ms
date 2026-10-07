{
  "properties": {},
  "id": "6efe69006a134fcda69d1838d0ab0701",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "05.流程分类统计",
  "createTime": 1782955344480,
  "updateTime": 1787153759596,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "count",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "结合当前人员的角色和组织范围，统计其可见流程的分类与数量。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：05.流程分类统计
// 路由：POST /sym/approval/query/count
// 业务说明：结合当前人员的角色和组织范围，统计其可见流程的分类与数量。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil;

/*
 * 审批中心 - 流程分类统计
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */

var currentUserId = StpUtil.getLoginId() + ""

var addUnique = (list, value) => {
    if (value == null || value == "") {
        return
    }
    var strValue = value + ""
    if (list.filter(item => item == strValue).size() == 0) {
        list.add(strValue)
    }
}

var permissionList = []
addUnique(permissionList, currentUserId)
var viewerUserIds = []
addUnique(viewerUserIds, currentUserId)

var appId = tenantAccounts.session().get("appId")
// 从 MySQL 业务表读取当前有效数据。
var userRoleList = db.table("rm_user_role_rela_t").where()
    .eq("user_id", currentUserId)
    .select()

if (userRoleList != null && userRoleList.size() > 0) {
    var roleIds = userRoleList.map(item => {
        // 返回当前业务分支的标准结果。
        return item.roleId
    })

    // 从 MySQL 业务表读取当前有效数据。
    var roleList = db.table("rm_role_t").where()
        .eq("app_id", appId)
        .in("id", roleIds)
        .select()

    if (roleList != null && roleList.size() > 0) {
        roleList.each(item -> {
            addUnique(permissionList, "role:" + item.codeNum)
        })

        // 从 MySQL 业务表读取当前有效数据。
        var sameRoleUserList = db.table("rm_user_role_rela_t").where()
            .in("role_id", roleIds)
            .select()
        if (sameRoleUserList != null && sameRoleUserList.size() > 0) {
            sameRoleUserList.each(item -> {
                addUnique(viewerUserIds, item.userId)
            })
        }
    }
}

var orgSQL = """
    SELECT
        org.id
    FROM
        rm_user_org_rela_t u,
        rm_org_t org
    WHERE
        u.user_id = #{currentUserId}
        AND u.job_type = 1
        AND u.org_id = org.id
"""
// 从 MySQL 业务表读取当前有效数据。
var orgInfo = db.selectOne(orgSQL)
if (orgInfo != null) {
    addUnique(permissionList, "org:" + orgInfo.id)

    // 从 MySQL 业务表读取当前有效数据。
    var sameOrgUserList = db.table("rm_user_org_rela_t").where()
        .eq("org_id", orgInfo.id)
        .select()
    if (sameOrgUserList != null && sameOrgUserList.size() > 0) {
        sameOrgUserList.each(item -> {
            addUnique(viewerUserIds, item.userId)
        })
    }
}

var allCountSql = """
    SELECT COUNT(1) AS total
    FROM flow_instance i
    <where>
        i.del_flag = '0'
        AND i.create_by in
        <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
            #{viewerUserId}
        </foreach>
        AND i.flow_status in (1, 2, 4, 6, 8, 9, 11)
    </where>
"""

var processCountSql = """
    SELECT COUNT(1) AS total
    FROM flow_instance i
    <where>
        i.del_flag = '0'
        AND i.create_by in
        <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
            #{viewerUserId}
        </foreach>
        AND i.flow_status in (1, 9)
    </where>
"""

var achieveCountSql = """
    SELECT COUNT(1) AS total
    FROM flow_instance i
    <where>
        i.del_flag = '0'
        AND i.create_by in
        <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
            #{viewerUserId}
        </foreach>
        AND i.flow_status in (4, 6, 8)
    </where>
"""

var doneCountSql = """
    SELECT COUNT(1) AS total
    FROM (
        SELECT MAX(id) AS id
        FROM flow_his_task
        <where>
            approver in
            <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
                #{viewerUserId}
            </foreach>
            AND del_flag = '0'
        </where>
        GROUP BY instance_id
    ) t
"""

var todoCountSql = """
    SELECT COUNT(DISTINCT t.id) AS total
    FROM flow_task t
    JOIN flow_user uu ON uu.associated = t.id
    LEFT JOIN flow_instance i ON t.instance_id = i.id
    <where>
        t.node_type = 1
        AND t.del_flag = '0'
        AND uu.del_flag = '0'
        AND i.del_flag = '0'
        AND t.flow_status != 4
        AND uu.processed_by in
        <foreach item="permission" collection="permissionList" open="(" separator="," close=")">
            #{permission}
        </foreach>
    </where>
"""

var queryCount = (sql) => {
    // 从 MySQL 业务表读取当前有效数据。
    var row = db.selectOne(sql)
    if (row == null || row.total == null) {
        // 返回当前业务分支的标准结果。
        return 0
    }
    // 返回当前业务分支的标准结果。
    return row.total::long(0)
}

// 返回当前业务分支的标准结果。
return {
    all: queryCount(allCountSql),
    todo: queryCount(todoCountSql),
    done: queryCount(doneCountSql),
    process: queryCount(processCountSql),
    achieve: queryCount(achieveCountSql)
}

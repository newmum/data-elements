{
  "properties": {},
  "id": "5f6b55b2b62d4b7e9d964cb4a9db247b",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "06.流程分页查询",
  "createTime": 1782955344488,
  "updateTime": 1787153759644,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "page_copy",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pageNum\": \"1\",\n    \"pageSize\": \"20\",\n    \"flowSatusType\": \"achieve\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前人员的角色和组织范围，结合节点、实例、状态及分页条件查询审批流程。",
  "requestBodyDefinition": {
    "name": "root",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "pageNum",
        "value": "1",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "pageSize",
        "value": "20",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "nodeCode",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "nodeName",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "instanceId",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "flowSatusType",
        "value": "todo",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  },
  "responseBodyDefinition": {
    "name": "",
    "value": "",
    "description": "",
    "required": false,
    "dataType": "Object",
    "type": null,
    "defaultValue": null,
    "validateType": "",
    "error": "",
    "expression": "",
    "children": [
      {
        "name": "code",
        "value": "100120",
        "description": "",
        "required": false,
        "dataType": "Integer",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "msg",
        "value": "user_no_login",
        "description": "",
        "required": false,
        "dataType": "String",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "success",
        "value": "false",
        "description": "",
        "required": false,
        "dataType": "Boolean",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      },
      {
        "name": "data",
        "value": "null",
        "description": "",
        "required": false,
        "dataType": "Object",
        "type": null,
        "defaultValue": null,
        "validateType": "",
        "error": "",
        "expression": "",
        "children": []
      }
    ]
  }
}
================================
// 接口：06.流程分页查询
// 路由：POST /sym/approval/query/page_copy
// 业务说明：按当前人员的角色和组织范围，结合节点、实例、状态及分页条件查询审批流程。
import tenantAccounts
import cn.dev33.satoken.stp.StpUtil;

/*
 * 审批中心 - 流程分页查询
 * 本接口属于 /sym/approval 下的独立 Warm-Flow 基础能力。
 * 数据资产登记步骤不会自动调用或自动提交本接口。
 */

// 解析请求体并为可选参数设置默认值。
var nodeCode = body?.nodeCode
var nodeName = body?.nodeName
var instanceId = body?.instanceId

var flowStatusType = body?.flowSatusType
if (flowStatusType == null || flowStatusType == "") {
    flowStatusType = "all"
}

var currentUserId = StpUtil.getLoginId() + ""

var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
var offsetLimit = pageNum == 0 ? 0 : (pageNum - 1) * pageSize

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

var todoPageSql = """
    SELECT DISTINCT
        t.id,
        t.node_code,
        t.node_name,
        t.node_type,
        t.definition_id,
        t.instance_id,
        t.create_time,
        t.update_time,
        t.tenant_id,
        i.flow_status,
        i.business_id,
        i.activity_status,
        d.flow_name,
        t.form_custom,
        t.form_path
    FROM flow_task t
    JOIN flow_user uu ON uu.associated = t.id
    LEFT JOIN flow_definition d ON t.definition_id = d.id
    LEFT JOIN flow_instance i ON t.instance_id = i.id
    <where>
        t.node_type = 1
        AND t.del_flag = '0'
        AND uu.del_flag = '0'
        AND i.del_flag = '0'
        AND uu.processed_by in
        <foreach item="permission" collection="permissionList" open="(" separator="," close=")">
            #{permission}
        </foreach>
        <if test="nodeCode != null and nodeCode != ''">AND t.node_code = #{nodeCode}</if>
        <if test="nodeName != null and nodeName != ''">AND t.node_name like concat('%', #{nodeName}, '%')</if>
        <if test="instanceId != null and instanceId != ''">AND t.instance_id = #{instanceId}</if>
    </where>
    ORDER BY t.create_time DESC
"""

var myInstancePageSql = """
    SELECT
        i.id,
        i.node_code,
        i.node_name,
        i.node_type,
        i.definition_id,
        i.id AS instance_id,
        i.create_time,
        i.update_time,
        i.tenant_id,
        i.flow_status,
        i.business_id,
        i.activity_status,
        d.flow_name,
        d.form_custom,
        d.form_path
    FROM flow_instance i
    LEFT JOIN flow_definition d ON i.definition_id = d.id
    <where>
        i.del_flag = '0'
        AND i.create_by in
        <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
            #{viewerUserId}
        </foreach>
        <if test="nodeCode != null and nodeCode != ''">AND i.node_code = #{nodeCode}</if>
        <if test="nodeName != null and nodeName != ''">AND i.node_name like concat('%', #{nodeName}, '%')</if>
        <if test="instanceId != null and instanceId != ''">AND i.id = #{instanceId}</if>
        <if test="flowStatusType == 'process'">AND i.flow_status in (1, 6, 9)</if>
        <if test="flowStatusType == 'achieve'">AND i.flow_status in (2, 4, 8)</if>
        <if test="flowStatusType == 'all'">AND i.flow_status in (1, 2, 4, 6, 8, 9, 11)</if>
    </where>
    ORDER BY i.create_time DESC
"""

var donePageSql = """
    SELECT
        t.id,
        t.node_code,
        t.node_name,
        t.cooperate_type,
        t.approver,
        t.collaborator,
        t.node_type,
        t.target_node_code,
        t.target_node_name,
        t.definition_id,
        t.instance_id,
        i.flow_status,
        t.message,
        t.ext,
        t.create_time,
        t.update_time,
        t.tenant_id,
        i.business_id,
        t.form_path,
        d.flow_name
    FROM (
        SELECT MAX(id) AS id
        FROM flow_his_task
        <where>
            approver in
            <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
                #{viewerUserId}
            </foreach>
            AND del_flag = '0'
            <if test="nodeCode != null and nodeCode != ''">AND node_code = #{nodeCode}</if>
            <if test="nodeName != null and nodeName != ''">AND node_name like concat('%', #{nodeName}, '%')</if>
            <if test="instanceId != null and instanceId != ''">AND instance_id = #{instanceId}</if>
        </where>
        GROUP BY instance_id
    ) tmp
    LEFT JOIN flow_his_task t ON t.id = tmp.id
    LEFT JOIN flow_definition d ON t.definition_id = d.id
    LEFT JOIN flow_instance i ON t.instance_id = i.id
    ORDER BY t.create_time DESC
"""

var representPageSql = """
    SELECT
        t.id,
        t.node_code,
        t.node_name,
        t.cooperate_type,
        t.approver,
        t.collaborator,
        t.node_type,
        t.target_node_code,
        t.target_node_name,
        t.definition_id,
        t.instance_id,
        i.flow_status,
        t.message,
        t.ext,
        t.create_time,
        t.update_time,
        t.tenant_id,
        i.business_id,
        t.form_path,
        d.flow_name
    FROM (
        SELECT MAX(id) AS id
        FROM flow_his_task
        <where>
            del_flag = '0'
            AND (cooperate_type = 2 OR cooperate_type = 3)
            AND (
                approver in
                <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator="," close=")">
                    #{viewerUserId}
                </foreach>
                <if test="viewerUserIds != null and viewerUserIds.size() > 0">
                    OR
                    <foreach item="viewerUserId" collection="viewerUserIds" open="(" separator=" OR " close=")">
                        collaborator like concat('%', #{viewerUserId}, '%')
                    </foreach>
                </if>
            )
            <if test="nodeCode != null and nodeCode != ''">AND node_code = #{nodeCode}</if>
            <if test="nodeName != null and nodeName != ''">AND node_name like concat('%', #{nodeName}, '%')</if>
            <if test="instanceId != null and instanceId != ''">AND instance_id = #{instanceId}</if>
        </where>
        GROUP BY instance_id
    ) tmp
    LEFT JOIN flow_his_task t ON t.id = tmp.id
    LEFT JOIN flow_definition d ON t.definition_id = d.id
    LEFT JOIN flow_instance i ON t.instance_id = i.id
    ORDER BY t.create_time DESC
"""

var pageSql = myInstancePageSql
if (flowStatusType == "todo") {
    pageSql = todoPageSql
} else if (flowStatusType == "done") {
    pageSql = donePageSql
} else if (flowStatusType == "represent") {
    pageSql = representPageSql
}

// 从 MySQL 业务表读取当前有效数据。
var pageList = db.page(pageSql, pageSize, offsetLimit)

if (pageList.total == 0) {
    pageList.list = []
} else {
    pageList.list.each(item -> {
        item.id = item.id + ""
        item.definitionId = item.definitionId + ""
        item.instanceId = item.instanceId + ""
        if (item.businessId != null) {
            item.businessId = item.businessId + ""
        }
    })
}

// 返回当前业务分支的标准结果。
return pageList

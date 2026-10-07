{
  "properties": {},
  "id": "e827efbdb789464ea6fbf4b8570f4a61",
  "script": null,
  "groupId": "b2bc4c16954544a69d4bcb4700dadce1",
  "name": "01.查看审批信息",
  "createTime": null,
  "updateTime": 1786951315306,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "info",
  "method": "GET",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "审批详情（GET /sym/approval/info）。按租户校验流程参与人，只读取 Warm-Flow 与本地 sym_* 身份数据；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：01.查看审批信息
// 路由：GET /sym/approval/info
// 业务说明：审批详情（GET /sym/approval/info）。按租户校验流程参与人，只读取 Warm-Flow 与本地 sym_* 身份数据；公安LOCAL账号使用本地库与令牌会话，其他租户保留原机制。
import tenantRuntime
import approvalRuntime
import tenantAccounts
import jsons


if ((businessId == null || businessId == '') && (instanceId == null || instanceId == '')) {
    exit -1, 'businessId 和 instanceId 不能同时为空'
}

var principal = approvalRuntime.principal()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = tenantRuntime.id()
var instance = db.selectOne("""
    select i.id, i.business_id, i.flow_status, i.definition_id, i.create_by,
           i.node_code, i.node_name, i.ext, i.create_time, i.update_time,
           d.flow_code, d.flow_name, null as create_by_name
      from flow_instance i
      join flow_definition d on d.id = i.definition_id and d.tenant_id = i.tenant_id
     where i.tenant_id = #{tenantId}
       and i.del_flag = '0'
       <if test="businessId != null and businessId != ''">and i.business_id = #{businessId}</if>
       <if test="instanceId != null and instanceId != ''">and i.id = #{instanceId}</if>
""")
if (instance == null) { exit -1, '审批流程不存在或已删除' }

var permissionList = principal.permissionKeys
var canView = instance.createBy + '' == principal.userId
if (!canView) {
    var involved = db.selectInt("""
        select count(*) from flow_user fu
          join flow_task t on t.id = fu.associated and t.tenant_id = fu.tenant_id
         where t.instance_id = #{instance.id}
           and fu.tenant_id = #{tenantId}
           and fu.del_flag = '0' and t.del_flag = '0'
           and fu.processed_by in
           <foreach item="permission" collection="permissionList" open="(" separator="," close=")">
             #{permission}
           </foreach>
    """)
    var handled = db.selectInt("""
        select count(*) from flow_his_task h
         where h.instance_id = #{instance.id}
           and h.tenant_id = #{tenantId}
           and h.del_flag = '0'
           and h.approver = #{principal.userId}
    """)
    canView = involved > 0 || handled > 0
}
if (!canView) { exit -1, '您不是该流程的发起人或参与人，无权查看' }

var currentTask = db.selectOne("""
    select id, node_code, node_name, form_custom, form_path
      from flow_task
     where instance_id = #{instance.id}
       and tenant_id = #{tenantId}
       and del_flag = '0' and node_type = 1
     order by create_time desc
""")
var nodes = db.select("""
    select h.id, h.node_code, h.node_name, h.node_type as skip_type,
           h.approver, null as approver_name, h.message,
           h.create_time, h.update_time
      from flow_his_task h
     where h.instance_id = #{instance.id}
       and h.tenant_id = #{tenantId}
       and h.del_flag = '0'
     order by h.create_time
""")
nodes.each(item -> item.id = item.id + '')
var names = tenantAccounts.names(([instance.createBy] + nodes.map(item => item.approver)).filter(item => item != null))
instance.createByName = names[instance.createBy] || instance.createBy
nodes.each(item -> item.approverName = names[item.approver] || item.approver)

return {
    approvalInfo: {
        instanceId: instance.id + '',
        businessId: instance.businessId == null ? null : instance.businessId + '',
        flowStatus: instance.flowStatus,
        flowCode: instance.flowCode,
        flowName: instance.flowName,
        definitionId: instance.definitionId + '',
        createBy: instance.createBy + '',
        createByName: instance.createByName,
        createTime: instance.createTime,
        updateTime: instance.updateTime,
        nodeCode: currentTask?.nodeCode || instance.nodeCode,
        nodeName: currentTask?.nodeName || instance.nodeName,
        taskId: currentTask?.id == null ? null : currentTask.id + '',
        ext: instance.ext == null || instance.ext == '' ? {} : jsons.toJSONObject(instance.ext)
    },
    flowNodes: nodes
}

{
  "properties": {},
  "id": "f23cef22cacc4a8cbe5ac3df7c3a1566",
  "script": null,
  "groupId": "208d0e4705354e9eba03d8d531396f82",
  "name": "07.流程分页查询",
  "createTime": 1786756145867,
  "updateTime": 1786951315185,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "page",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"flowSatusType\":\"todo\",\"flowCode\":\"\",\"key\":\"\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "按当前人员的角色和组织范围，结合节点、实例、状态及分页条件查询审批流程。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.流程分页查询
// 路由：POST /sym/approval/query/page
// 业务说明：按当前人员的角色和组织范围，结合节点、实例、状态及分页条件查询审批流程。
import tenantRuntime
import approvalRuntime
import tenantAccounts
import jsons


var principal = approvalRuntime.principal()
// 业务租户从当前会话读取，不采用请求体中的租户标识。
var tenantId = tenantRuntime.id()
var currentUserId = principal.userId
var permissionList = principal.permissionKeys
var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
var offset = (pageNum - 1) * pageSize
var view = body?.flowSatusType || "todo"
var flowCode = body?.flowCode
var keyword = body?.key

var todoSql = """
    select distinct t.id, t.node_code, t.node_name, t.node_type,
           t.definition_id, t.instance_id, t.create_time, t.update_time,
           t.tenant_id, t.flow_status, i.business_id, i.activity_status,
           i.create_by, i.ext, d.flow_code, d.flow_name, t.form_custom, t.form_path,
           null as create_name
      from flow_task t
      join flow_user fu on fu.associated = t.id
      join flow_instance i on i.id = t.instance_id
      join flow_definition d on d.id = t.definition_id
     where t.tenant_id = #{tenantId}
       and fu.tenant_id = #{tenantId}
       and i.tenant_id = #{tenantId}
       and d.tenant_id = #{tenantId}
       and t.del_flag = '0' and fu.del_flag = '0' and i.del_flag = '0'
       and t.node_type = 1
       and t.flow_status not in ('4', '6', '8')
       and fu.processed_by in
       <foreach item="permission" collection="permissionList" open="(" separator="," close=")">
         #{permission}
       </foreach>
       <if test="flowCode != null and flowCode != ''">and d.flow_code = #{flowCode}</if>
       <if test="keyword != null and keyword != ''">
         and (d.flow_name like concat('%', #{keyword}, '%') or i.ext like concat('%', #{keyword}, '%'))
       </if>
     order by t.create_time desc
"""

var instanceSql = """
    select i.id, i.node_code, i.node_name, i.node_type, i.definition_id,
           i.id as instance_id, i.create_time, i.update_time, i.tenant_id,
           i.flow_status, i.business_id, i.activity_status, i.create_by, i.ext,
           d.flow_code, d.flow_name, d.form_custom, d.form_path,
           null as create_name
      from flow_instance i
      join flow_definition d on d.id = i.definition_id
     where i.tenant_id = #{tenantId}
       and d.tenant_id = #{tenantId}
       and i.del_flag = '0'
       and i.create_by = #{currentUserId}
       <if test="view == 'process'">and i.flow_status in ('1', '9')</if>
       <if test="view == 'archive' || view == 'achieve'">and i.flow_status in ('2', '4', '6', '8')</if>
       <if test="view == 'all'">and i.flow_status in ('1', '2', '4', '6', '8', '9', '11')</if>
       <if test="flowCode != null and flowCode != ''">and d.flow_code = #{flowCode}</if>
       <if test="keyword != null and keyword != ''">
         and (d.flow_name like concat('%', #{keyword}, '%') or i.ext like concat('%', #{keyword}, '%'))
       </if>
     order by i.create_time desc
"""

var doneSql = """
    select h.id, h.node_code, h.node_name, h.node_type, h.definition_id,
           h.instance_id, h.create_time, h.update_time, h.tenant_id,
           i.flow_status, i.business_id, i.activity_status, i.create_by, i.ext,
           h.message, h.form_path, d.flow_code, d.flow_name,
           null as create_name
      from flow_his_task h
      join flow_instance i on i.id = h.instance_id
      join flow_definition d on d.id = h.definition_id
     where h.tenant_id = #{tenantId}
       and i.tenant_id = #{tenantId}
       and d.tenant_id = #{tenantId}
       and h.del_flag = '0'
       and h.approver = #{currentUserId}
       and h.id = (
           select max(h2.id) from flow_his_task h2
            where h2.instance_id = h.instance_id
              and h2.approver = #{currentUserId}
              and h2.tenant_id = #{tenantId}
              and h2.del_flag = '0'
       )
       <if test="flowCode != null and flowCode != ''">and d.flow_code = #{flowCode}</if>
       <if test="keyword != null and keyword != ''">
         and (d.flow_name like concat('%', #{keyword}, '%') or i.ext like concat('%', #{keyword}, '%'))
       </if>
     order by h.create_time desc
"""

var sql = view == "todo" ? todoSql : (view == "done" ? doneSql : instanceSql)
var result = db.page(sql, pageSize, offset)
result.list = result.list || []
var names = tenantAccounts.names(result.list.map(item => item.createBy))
result.list.each(item -> {
    item.id = item.id == null ? null : item.id + ""
    item.definitionId = item.definitionId == null ? null : item.definitionId + ""
    item.instanceId = item.instanceId == null ? null : item.instanceId + ""
    item.businessId = item.businessId == null ? null : item.businessId + ""
    item.createName = names[item.createBy] || item.createBy
    item.ext = item.ext == null || item.ext == "" ? {} : jsons.toJSONObject(item.ext)
    item.businessName = item.ext?.businessName || item.flowName
})
return result

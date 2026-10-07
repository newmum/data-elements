{
  "properties": {},
  "id": "169959dd8fb148e68b4cfe4644f63c76",
  "script": null,
  "groupId": "86af6a92565d4dc38074fdc22b9127f6",
  "name": "03.流程定义列表",
  "createTime": null,
  "updateTime": 1786191950796,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"pageNum\":1,\"pageSize\":20,\"keyword\":\"\",\"publishStatus\":\"\"}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "读取审批中心可用的流程定义，返回发起流程时可选择的定义清单。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.流程定义列表
// 路由：POST /sym/approval/config/list
// 业务说明：读取审批中心可用的流程定义，返回发起流程时可选择的定义清单。
// 流程定义列表：处理 POST list 的核心查询和组装返回数据逻辑。
import approvalRuntime
import tenantRuntime
var principal = approvalRuntime.principal()
var tenantId = tenantRuntime.id()
var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
var offset = (pageNum - 1) * pageSize
var keyword = body?.keyword
var publishStatus = body?.publishStatus

var result = db.page("""
    select id, flow_code, flow_name, model_value, category, version,
           is_publish, activity_status, form_custom, form_path,
           create_by, update_by, create_time, update_time
      from flow_definition
     where tenant_id = #{tenantId} and del_flag = '0'
       <if test="keyword != null and keyword != ''">
         and (flow_code like concat('%', #{keyword}, '%') or flow_name like concat('%', #{keyword}, '%'))
       </if>
       <if test="publishStatus != null and publishStatus != ''">and is_publish = #{publishStatus}</if>
     order by update_time desc, create_time desc
""", pageSize, offset)
result.list = result.list || []
result.list.each(item -> item.id = item.id + "")
return result

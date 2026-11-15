{
  "properties": {},
  "id": "c07b80ac993947689394afcb66e3efba",
  "script": null,
  "groupId": "5f6726d5f8f44059a0151b1f35f42320",
  "name": "04.操作流程定义",
  "createTime": 1782955344704,
  "updateTime": 1784446270733,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/exec",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"id\": \"2036256483356024834\",\n    \"action\": \"copy\",\n    \"file\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "校验定义归属并调用 Warm-Flow 执行定义操作。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import org.dromara.warm.flow.core.service.DefService as defService
import response
import tableWrite
import tenantRuntime

// 校验定义归属并调用 Warm-Flow 执行定义操作。
var action = body?.action
if (!action) { exit -1, '请指定操作' }
var value = (body?.id || '') + ''
if (value == '') { exit -1, '流程定义id不能为空' }
if (!value.matches('[1-9][0-9]{0,18}')) { exit -1, '流程定义ID无效' }
var id = value.asLong()
var tenant = tenantRuntime.id()
if (!tenant) { exit 401, '当前租户无效' }
if (id == null || id <= 0) { exit -1, '流程定义ID无效' }
return db.transaction(() => {
    // 同一事务中的行锁覆盖 Warm-Flow 的发布、停用和删除。
    var locked = tableWrite.lockRow('flow_definition', tenant, 'id', id, ['flow_code'], {del_flag: '0'})
    if (locked == null) { exit -1, '不存在此流程定义' }
    if (action == 'detail') {
        var flow = db.table('flow_definition').where().eq('tenant_id', tenant).eq('id', id).eq('del_flag', '0').selectOne()
        flow.id = flow.id + ''
        return flow
    }
    if (action == 'export') { return response.download(defService.exportJson(id), locked.flow_code + '.json') }
    if (action == 'import') {
        if (body?.file == null) { exit -1, '请选择流程文件' }
        defService.importIs(body.file.getInputStream())
        return
    }
    if (action == 'copy') { return defService.copyDef(id) }
    if (action == 'publish') { return defService.publish(id) }
    if (action == 'unPublish') { return defService.unPublish(id) }
    if (action == 'unActive') { return defService.unActive(id) }
    if (action == 'active') { return defService.active(id) }
    if (action == 'delete') { return defService.removeDef([id]) }
})

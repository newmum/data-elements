{
  "properties": {},
  "id": "7620748a6f17464fab1e392b278c6365",
  "script": null,
  "groupId": "37c35deab6bc4e678c8955cc951f2ffd",
  "name": "04.应用发布",
  "createTime": 1782955345178,
  "updateTime": 1784437771872,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/publish",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"xxxx\",\n    \"publishStatus\": \"0\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": null\n}",
  "description": "按请求目标与业务状态执行应用发布，返回本次操作结果；使用请求参数 tid、publishStatus；涉及 app_workshop_repository 等业务表；执行数据库写入并返回处理结果。按当前认证行业租户处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import log
import cn.dev33.satoken.stp.StpUtil
import tenantRuntime

// POST /das/app/publish 应用发布。按请求目标与业务状态执行应用发布，返回本次操作结果；使用请求参数 tid、publishStatus；涉及 app_workshop_repository 等业务表；执行数据库写入并返回处理结果。
var tenantId = tenantRuntime.id()

var tid = body?.tid
var publishStatus = body?.publishStatus

if (tid == null || tid == '') {
    exit 500, '应用ID不能为空'
}

var text = value => value == null ? '' : (value + '').trim()
var rowValue = (row, keys) => {
    if (row == null) { return null }
    for (key in keys) {
        var expected = (key + '').toLowerCase()
        var found = null
        for (actual in row.keySet()) {
            if ((actual + '').toLowerCase() == expected && row[actual] != null) {
                found = row[actual]
            }
        }
        if (found != null) { return found }
    }
    return null
}

var currentUserId = StpUtil.getLoginId() + ""
var now = new Date()
var currentTime = now.format('yyyy-MM-dd HH:mm:ss')
var published = publishStatus != null && (publishStatus + '') == '1'

if (published) {
    // 发布预览地址取第一个页签：专题/报表/大屏页按组件预览，表单页按表单预览。
    var tab = db.table('app_workshop_repository_tab').columns('rela_id', 'tab_type').where().eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .eq('pid', tid)
        .orderBy('seq_no', 'asc')
        .orderBy('created_time', 'asc')
        .selectOne()
    var relaId = text(rowValue(tab, ['relaId', 'RELA_ID']))
    var tabType = rowValue(tab, ['tabType', 'TAB_TYPE'])

    var updateData = {
        publish_status: 1,
        updated_by: currentUserId,
        updated_time: currentTime
    }
    if (relaId != '') {
        var previewType = (tabType + '') == '2' ? 'form' : 'component'
        updateData.publish_url = '/#/publish-view?showNav=0&type=' + previewType + '&pid=' + tid + '&tid=' + relaId
        updateData.publish_id = tid
    }
    db.table("app_workshop_repository").where().eq('tenant_id', tenantId).eq("tid", tid).update(updateData)
} else {
    db.table('app_workshop_repository').withBlank().where().eq('tenant_id', tenantId).eq('tid', tid).update({
        publish_status: 0,
        publish_url: null,
        publish_id: null,
        updated_by: currentUserId,
        updated_time: currentTime
    })
}

log.info('应用发布状态更新, tid: {}, published: {}', tid, published)
return "success"

{
  "properties": {},
  "id": "8c12c7d95d1148f892182090e6006924",
  "script": null,
  "groupId": "cc977d046a125088bbe411c296b12efc",
  "name": "04.页签新增",
  "createTime": 1782955345291,
  "updateTime": 1784437771882,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/create",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"pid\": \"1f079665d86b4e06b7542247498eb9f0\",\n    \"tabType\": \"1\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 0,\n    \"msg\": \"success\",\n    \"success\": true,\n    \"data\": {\n        \"tid\": \"680185f2a6ed43adb6b4dd4766efeb67\"\n    }\n}",
  "description": "校验请求资料后执行页签新增，返回保存或配置结果；使用请求参数 pid、tabType、relaId、name、nameCn、thumbnail、publishId、seqNo；涉及 app_workshop_repository_tab 等业务表。按当前认证行业租户处理。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.core.util.IdUtil
import log
import tenantRuntime

// POST /das/tab/create 页签新增。校验请求资料后执行页签新增，返回保存或配置结果；使用请求参数 pid、tabType、relaId、name、nameCn、thumbnail、publishId、seqNo；涉及 app_workshop_repository_tab 等业务表。
var tenantId = tenantRuntime.id()

var pid = body?.pid
if (pid == null || pid == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '应用仓库ID(pid)不能为空' 
}

var tabType = body?.tabType
if (tabType == null || tabType == '') {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '页签类型(tabType)不能为空' 
}

if ([0,1,2,3].contains(tabType::int) == false) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '页签类型(tabType)仅支持 0/1/2/3' 
}

var relaId = body?.relaId

if (db.table('app_workshop_repository').where().eq('tenant_id', tenantId).eq('is_del', 0).eq('tid', pid).count() == 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '应用仓库不存在' 
}

if (relaId != null && relaId != '' && db.table('app_workshop_repository_tab').where().eq('tenant_id', tenantId)
        .eq('is_del', 0)
        .eq('pid', pid)
        .eq('tab_type', tabType::int)
        .eq('rela_id', relaId)
        .count() > 0) {
    // 参数或业务状态不满足要求时立即终止请求。
    exit 500, '页签已存在' 
}

var now = new Date()
var currentTime = now.format('yyyy-MM-dd HH:mm:ss')
var tid = IdUtil.getSnowflakeNextIdStr()
var currentUserId = StpUtil.getLoginId() + ""

db.table('app_workshop_repository_tab')
    .insert({
        tenant_id: tenantId,
        tid: tid,
        pid: pid,
        tab_type: tabType::int,
        rela_id: relaId,
        name: body?.name,
        name_cn: body?.nameCn,
        thumbnail: body?.thumbnail,
        publish_id: body?.publishId,
        seq_no: body?.seqNo::int(0),
        is_del: 0,
        created_by: currentUserId,
        created_time: currentTime,
        updated_by: currentUserId,
        updated_time: currentTime
    })

log.info('新增应用仓库页签成功, tid: {}, pid: {}, tabType: {}, relaId: {}', tid, pid, tabType, relaId)

// 返回当前业务分支的标准结果。
return  {
  tid : tid
}

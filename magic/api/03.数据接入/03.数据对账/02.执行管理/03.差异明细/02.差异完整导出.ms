{
  "properties": {},
  "id": "8d8b3bc50f054a0dbeb2baf22500e8a6",
  "groupId": "0c0fe2ac06f04679b4b52cfa19c54d96",
  "name": "02.差异完整导出",
  "path": "/export",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"runId\":\"执行实例ID\",\"type\":\"VALUE_MISMATCH\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/reconciliation/run/diff/export；当前租户已完成实例的全部脱敏差异，最多5000条，超限明确拒绝，不截断。",
  "script": null
}
================================
// 复用仓储层的租户隔离与脱敏查询。运行中的差异仍可能增加，不作为完整导出。
import tenantRuntime
import reconcile
import cn.hutool.core.util.NumberUtil

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后导出'
}
var runId = body?.runId == null ? '' : ('' + body.runId).trim()
if (!runId.matches('^[A-Za-z0-9_-]{1,64}$')) {
    exit 400, '对账实例ID无效'
}
var type = body?.type == null ? '' : ('' + body.type).trim()
if (type.length() > 64) {
    exit 400, '差异类型无效'
}
var ownedRun = db.table('data_reconcile_run_t').where()
    .eq('tenant_id', tenantId).eq('tid', runId).selectOne()
if (ownedRun == null) {
    exit 404, '对账实例不存在或无权访问'
}
var run = reconcile.runDetail(runId)
var runStatus = run.status == null ? '' : ('' + run.status).trim().toUpperCase()
if (runStatus != 'CONSISTENT' && runStatus != 'INCONSISTENT') {
    exit 409, '仅已完成的对账实例可完整导出差异'
}

var limit = 5000
var pageSize = 200
var first = reconcile.diffPage(runId, type, 1, pageSize)
var total = NumberUtil.parseLong('' + first.total)
if (total < 0) {
    exit 500, '差异总数无效，请稍后重试'
}
if (total > limit) {
    exit 413, '差异共' + total + '条，超过单次导出上限5000条；请缩小差异类型范围或使用后台分片导出'
}
var rows = []
var page = 1
var current = first
while (rows.size() < total) {
    var batch = current.list == null ? [] : current.list
    if (batch.size() == 0) {
        exit 409, '差异数据在导出期间发生变化，请重新导出'
    }
    for (diff in batch) {
        rows.add({
            businessKey: diff.business_key,
            diffType: diff.diff_type,
            fieldName: diff.field_name,
            sourceValueMasked: diff.source_value_masked,
            targetValueMasked: diff.target_value_masked
        })
    }
    if (rows.size() >= total) { break }
    page = page + 1
    current = reconcile.diffPage(runId, type, page, pageSize)
}
if (rows.size() != total) {
    exit 409, '差异数据在导出期间发生变化，请重新导出'
}
return {
    runId: runId,
    runStatus: runStatus,
    type: type,
    total: total,
    limit: limit,
    filename: 'reconciliation-diffs-' + runId + '.json',
    rows: rows
}

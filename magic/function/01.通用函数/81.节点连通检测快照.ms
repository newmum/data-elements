{
  "id": "node_health_snapshot_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "81.节点连通检测快照",
  "path": "/nodehealth",
  "description": "抽取、推送及服务节点列表共用的最近检测快照。view仅处理已授权批量记录；store按当前租户、配置版本和检测序号原子保存，旧检测不覆盖新配置。不保存凭据。",
  "parameters": [{"name":"input","dataType":"Object","required":true}]
}
================================
import tenantRuntime
import tableWrite
import org.springframework.transaction.support.TransactionTemplate as transactions
import cn.hutool.core.util.IdUtil
import java.sql.Timestamp
import java.time.LocalDateTime
import java.text.SimpleDateFormat
import java.util.Date

// @/common/nodehealth 由 /ods/node/page 和 /ods/service/page 共同调用。
// 调用方先按当前租户取得节点；列表直接使用已批量读取的列，不逐节点补查或探测。
var text = value => value == null ? '' : value + ''
var field = (row,snake,camel) => row[snake] == null ? row[camel] : row[snake]
var revision = value => {
    if (value instanceof Timestamp) { return value.toLocalDateTime().toString() }
    var normalized = text(value).trim().replace(' ','T')
    try { return LocalDateTime.parse(normalized).toString() } catch (ignored) { return normalized }
}
if (input.action == 'begin') { return {checkId:IdUtil.getSnowflakeNextIdStr(),started:new Date().getTime()} }
if (input.action == 'view') {
    var results = {}
    for (row in input.rows || []) {
        var checkedAt = field(row,'last_health_at','lastHealthAt')
        var status = text(field(row,'last_health_status','lastHealthStatus'))
        var savedRevision = text(field(row,'last_health_revision','lastHealthRevision'))
        var changed = checkedAt != null && savedRevision != revision(field(row,'updated_time','updatedTime'))
        var valid = checkedAt != null && !changed && ['reachable','unreachable'].contains(status)
        // JDBC 时间可能已由共享值适配层转为 LocalDateTime，统一后再格式化和计算时效。
        var checkedDate = checkedAt == null ? null : checkedAt instanceof Date ? checkedAt : Timestamp.valueOf(revision(checkedAt).replace('T',' '))
        var time = checkedDate == null ? '' : new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(checkedDate)
        results[text(row.tid)] = {
            healthStatus:valid?status:'unknown', healthTime:time,
            healthMessage:changed?'配置已修改，请重新检测':text(field(row,'last_health_message','lastHealthMessage')),
            healthLatencyMs:field(row,'last_health_latency_ms','lastHealthLatencyMs'),
            healthConfigChanged:changed,
            healthStale:valid && new Date().getTime()-checkedDate.getTime()>900000,
            healthRecordedStatus:status
        }
    }
    return results
}
if (input.action != 'store' || !['sync','push','service'].contains(input.kind)) { throw new IllegalArgumentException('不支持的节点检测操作') }
var tenantId = text(tenantRuntime.id()).trim()
if (tenantId == '') { throw new IllegalStateException('当前登录租户无效') }
var node = input.node
var nodeId = text(node?.tid)
var modifiedAt = field(node,'updated_time','updatedTime')
if (nodeId == '' || modifiedAt == null || !text(input.attempt?.checkId).matches('[0-9]{18,20}')) { throw new IllegalArgumentException('节点或检测版本无效') }
var result = input.result
var message = text(result.message).replaceAll('[\r\n\t]+',' ')
var password = text(field(node,'auth_password','authPassword'))
if (password != '') { message = message.replace(password,'[已隐藏]') }
message = message.replaceAll('(?i)(https?://)[^/@\\s]+@','$1[已隐藏]@')
if (message.length()>1000) { message = message.substring(0,1000) }
if (message == '') { message = result.success?'节点检测通过':'节点检测失败' }
var now = new Date()
var latency = Math.max(0,new Date().getTime()-input.attempt.started)
var table = input.kind == 'sync'?'nifi_node_t':input.kind == 'push'?'push_node_t':'service_node_t'
// 检测期间改过配置或已有更晚发起的检测时，本次结果不得覆盖当前快照。
var updated = transactions.execute(transaction => {
    var current = tableWrite.lockRow(table,tenantId,'tid',nodeId,
        ['updated_time','last_health_check_id'],{is_del:0})
    if (current == null || revision(current.updated_time) != revision(modifiedAt)
        || (current.last_health_check_id != null && text(current.last_health_check_id).compareTo(input.attempt.checkId)>0)) { return 0 }
    // 状态采样独立于配置修订，使用已有结构化写入能力，避免审计自动填充修改配置更新时间。
    return tableWrite.batchUpdate(table,tenantId,
        ['last_health_status','last_health_at','last_health_message','last_health_latency_ms','last_health_revision','last_health_check_id'],
        ['tid'],{is_del:0,updated_time:modifiedAt},[{tid:nodeId,
            last_health_status:result.success?'reachable':'unreachable',last_health_at:now,
            last_health_message:message,last_health_latency_ms:latency,
            last_health_revision:revision(modifiedAt),last_health_check_id:input.attempt.checkId}])
})
result.message = message
result.latencyMs = latency
result.healthTime = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(now)
result.persisted = updated == 1
result.superseded = updated != 1
return result

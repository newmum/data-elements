{
  "properties": {},
  "id": "data_report_submit_01",
  "script": null,
  "groupId": "6e30fb6d565c40b18d480b219e13b20e",
  "name": "27.提交上报文件",
  "path": "/report-submit",
  "method": "POST",
  "parameters": [
    {"name":"file","required":true,"dataType":"MultipartFile"},
    {"name":"datasourceId","required":true,"dataType":"String"},
    {"name":"tableId","required":true,"dataType":"String"},
    {"name":"fileId","required":true,"dataType":"String"},
    {"name":"requestId","required":true,"dataType":"String"}
  ],
  "options": [], "headers": [], "paths": [],
  "description": "接收 file、datasourceId、tableId、fileId 和 requestId。按认证租户验证文件与登记表同源且已通过规范，锁内保留幂等回执，再向配置的目标存储投递。返回已持久化的成功回执，处理中或失败请求冲突，投递失败时保存失败结果。"
}
================================
import tenantRuntime
import jsons
import tableWrite
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import java.time.Instant
import com.linewell.dataelement.datareport.FtpReportUploadService as reportUploadService

// POST /dst/database/metadata/report-submit 接收 file、datasourceId、tableId、fileId 和 requestId。
// 按登录租户校验数据源、登记表和已通过的文件规范。先在事务内保留请求，再投递文件并记录结果。
// 返回实际成功回执；重复请求沿用已有结果，处理中或此前明确失败的请求按既有规则返回冲突。
StpUtil.checkLogin()
var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var text = value => value == null ? '' : (value + '').trim()
if (text(tenantId) == '') {
    exit 401, '请重新登录后上报数据'
}
if (!text(requestId).matches('^[A-Za-z0-9_-]{1,100}$')) {
    exit 400, '上报请求标识不正确'
}

// 投递前核对双方标识都属于当前租户的本次登记，且规范已由服务端确认。
var source = db.table('db_datasource_t').where()
    .eq('tid', datasourceId)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0).selectOne()
if (source == null) {
    exit 404, '数据源不存在或不属于当前租户'
}
var pool = JSONUtil.parseObj(source.poolCfg == null ? '{}' : source.poolCfg + '')
var isReporting = config => ['upload', 'report'].contains(text(config.accessMode))
if (!isReporting(pool)) {
    exit 400, '仅数据上报方式允许提交文件'
}
var manifest = pool.reportRegistration
if (manifest == null) {
    exit 400, '请先上传文件并保存数据规范'
}
var sameFileAndTable = item => text(item.fileId) == text(fileId) && text(item.tableId) == text(tableId)
var table = (manifest.tables == null ? [] : manifest.tables)
    .find(item => text(item.tid) == text(tableId) && text(item.fileId) == text(fileId))
if (table == null) {
    exit 403, '文件和数据表不属于当前数据源的上传清单'
}
if (table.normalized != true || table.normalizationVersion != 1) {
    exit 409, '请先在第二步保存并通过数据规范，再进行上报'
}
var registered = db.table('db_table_t').where()
    .eq('tid', tableId)
    .eq('datasource_id', datasourceId)
    .eq('tenant_id', tenantId)
    .eq('is_del', 0).selectOne()
if (registered == null || text(registered.tableName) != text(table.tableName)) {
    exit 409, '数据表登记已变化，请重新读取规范后上报'
}
if (file == null || file.isEmpty() || file.getSize() > 10485760) {
    exit 400, '请选择不超过 10 MB 的非空数据文件'
}

var lockSourceConfig = () => {
    var locked = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', datasourceId,
        ['pool_cfg'], { is_del: 0 })
    return locked == null || locked.pool_cfg == null ? null : JSONUtil.parseObj(locked.pool_cfg + '')
}
var saveSourceConfig = config => {
    db.table('db_datasource_t').where()
        .eq('tid', datasourceId)
        .eq('tenant_id', tenantId)
        .eq('is_del', 0).update({ pool_cfg: jsons.toJSONStr(config) })
}

// 串行检查重试键与同表同文件的活动回执，锁内保存占位后再允许实际投递。
var reservation = db.transaction(() => {
    var config = lockSourceConfig()
    var latest = config == null ? null : config.reportRegistration
    if (latest == null || !isReporting(config) || latest.revision != manifest.revision) {
        throw new IllegalStateException('数据规范已更新，请重新读取后上报')
    }
    var receipts = latest.receipts == null ? [] : latest.receipts
    var existing = receipts.find(item => text(item.requestId) == text(requestId))
    if (existing != null) {
        if (!sameFileAndTable(existing)) {
            throw new IllegalArgumentException('请求标识已用于其他文件或数据表')
        }
        return { receipt: existing, alreadyExists: true }
    }
    var active = receipts.find(item => sameFileAndTable(item) && ['sending', 'success'].contains(text(item.status)))
    if (active != null) {
        return { receipt: active, alreadyExists: true }
    }
    if (receipts.size() >= 1000) {
        throw new IllegalStateException('当前数据源已达到 1000 次上报记录上限，请联系维护人员归档后继续')
    }
    var row = {
        id: IdUtil.fastSimpleUUID(), requestId: requestId, fileId: fileId, tableId: tableId,
        tableName: table.tableName, fileName: table.fileName, rowCount: table.recordCount,
        status: 'sending', createdAt: Instant.now().toString(), createdBy: actorId, reportedAt: '',
        registrationSnapshot: JSONUtil.parseObj(jsons.toJSONStr(table))
    }
    receipts.add(row)
    latest.receipts = receipts
    saveSourceConfig(config)
    return { receipt: row, alreadyExists: false }
})
var receipt = reservation.receipt
if (receipt.status == 'success') {
    return receipt
}
if (reservation.alreadyExists) {
    exit 409, '该请求已在处理或已失败，请查看记录；明确失败后使用新的请求标识重试'
}

// 协议投递在事务外执行，避免网络等待时持有数据源行锁。
var outcome = null
var failure = ''
try {
    outcome = reportUploadService.upload(datasourceId, table.tableName, file)
} catch (e) {
    failure = '上报文件投递失败，请检查目标存储连通性和目录权限后重试'
}

// 重新加锁后更新本次保留的回执，同时保留其他请求和不相关表的并发修改。
var completed = db.transaction(() => {
    var config = lockSourceConfig()
    var latest = config == null ? null : config.reportRegistration
    var receipts = latest == null || latest.receipts == null ? [] : latest.receipts
    var row = receipts.find(item => text(item.requestId) == text(requestId) && text(item.id) == text(receipt.id))
    if (row == null || !sameFileAndTable(row) || text(row.status) != 'sending') {
        throw new IllegalStateException('上报回执已变化，请核查投递结果')
    }
    row.status = failure == '' ? 'success' : 'failed'
    row.reportedAt = Instant.now().toString()
    row.message = failure
    if (outcome != null) {
        row.remoteFileName = outcome.remoteFileName
        row.targetDatasourceName = outcome.targetDatasourceName
    }
    saveSourceConfig(config)
    return row
})
if (failure != '') {
    exit 502, failure
}
return completed

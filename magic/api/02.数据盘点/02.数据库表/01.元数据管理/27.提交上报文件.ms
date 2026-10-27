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
  "description": "数据上报工作台：按当前租户校验文件及登记表关联，将规范化文件交给既有 FTP 投递服务，持久化真实成功或失败结果。"
}
================================
import tenantRuntime
import jsons
import cn.dev33.satoken.stp.StpUtil
import cn.hutool.json.JSONUtil
import cn.hutool.core.util.IdUtil
import java.time.Instant
import com.linewell.dataelement.datareport.FtpReportUploadService as reportUploadService

StpUtil.checkLogin()
var tenantId = tenantRuntime.id()
var actorId = StpUtil.getLoginIdAsString()
var text = value => value == null ? '' : (value + '').trim()
if (text(tenantId) == '') { exit 401, '请重新登录后上报数据' }
if (!text(requestId).matches('^[A-Za-z0-9_-]{1,100}$')) { exit 400, '上报请求标识不正确' }
var source = db.table('db_datasource_t').where().eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (source == null) { exit 404, '数据源不存在或不属于当前租户' }
var pool = JSONUtil.parseObj(source.poolCfg == null ? '{}' : source.poolCfg + '')
if (!['upload','report'].contains(text(pool.accessMode))) { exit 400, '仅数据上报方式允许提交文件' }
var manifest = pool.reportRegistration
if (manifest == null) { exit 400, '请先上传文件并保存数据规范' }
var table = (manifest.tables == null ? [] : manifest.tables).find(item => text(item.tid) == text(tableId) && text(item.fileId) == text(fileId))
if (table == null) { exit 403, '文件和数据表不属于当前数据源的上传清单' }
var registered = db.table('db_table_t').where().eq('tid', tableId).eq('datasource_id', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (registered == null || text(registered.tableName) != text(table.tableName)) { exit 409, '数据表登记已变化，请重新读取规范后上报' }
if (file == null || file.isEmpty() || file.getSize() > 10485760) { exit 400, '请选择不超过 10 MB 的非空数据文件' }

var reservation = db.transaction(() => {
    var locked = db.selectOne('select pool_cfg from db_datasource_t where tid=#{datasourceId} and tenant_id=#{tenantId} and is_del=0 for update')
    var config = JSONUtil.parseObj(locked.poolCfg + '')
    if (config.reportRegistration.revision != manifest.revision) { throw new IllegalStateException('数据规范已更新，请重新读取后上报') }
    var receipts = config.reportRegistration.receipts == null ? [] : config.reportRegistration.receipts
    var existing = receipts.find(item => text(item.requestId) == text(requestId))
    if (existing != null) {
        if (text(existing.fileId) != text(fileId) || text(existing.tableId) != text(tableId)) { throw new IllegalArgumentException('请求标识已用于其他文件或数据表') }
        return { receipt: existing, alreadyExists: true }
    }
    if (receipts.size() >= 1000) { throw new IllegalStateException('当前数据源已达到 1000 次上报记录上限，请联系维护人员归档后继续') }
    var row = { id: IdUtil.fastSimpleUUID(), requestId: requestId, fileId: fileId, tableId: tableId,
        tableName: table.tableName, fileName: table.fileName, rowCount: table.recordCount,
        status: 'sending', createdAt: Instant.now().toString(), createdBy: actorId, reportedAt: '' }
    receipts.add(row); config.reportRegistration.receipts = receipts
    db.table('db_datasource_t').where().eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).update({ pool_cfg: jsons.toJSONStr(config) })
    return { receipt: row, alreadyExists: false }
})
var receipt = reservation.receipt
if (receipt.status == 'success') { return receipt }
if (reservation.alreadyExists) { exit 409, '该请求已在处理或已失败，请查看记录；明确失败后使用新的请求标识重试' }
var outcome = null; var failure = ''
try { outcome = reportUploadService.upload(datasourceId, table.tableName, file) }
catch (e) { failure = '上报文件投递失败，请检查目标存储连通性和目录权限后重试' }
var completed = db.transaction(() => {
    var locked = db.selectOne('select pool_cfg from db_datasource_t where tid=#{datasourceId} and tenant_id=#{tenantId} and is_del=0 for update')
    var config = JSONUtil.parseObj(locked.poolCfg + '')
    var row = config.reportRegistration.receipts.find(item => text(item.requestId) == text(requestId))
    row.status = failure == '' ? 'success' : 'failed'; row.reportedAt = Instant.now().toString(); row.message = failure
    if (outcome != null) { row.remoteFileName = outcome.remoteFileName; row.targetDatasourceName = outcome.targetDatasourceName }
    db.table('db_datasource_t').where().eq('tid', datasourceId).eq('tenant_id', tenantId).eq('is_del', 0).update({ pool_cfg: jsons.toJSONStr(config) })
    return row
})
if (failure != '') { exit 502, failure }
return completed

{
  "id": "collect_start_v1",
  "groupId": "collection_api_group_v1",
  "name": "13.启动数据源表采集",
  "path": "/start",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "按行业角色和来源串行受理供后台探查的持久化任务。支持 dryRun 校验。"
}
================================
import '@/collect/job' as status
import '@/collect/jobs' as jobColumns
import '@/collect/response' as respond
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration
import com.linewell.dataelement.platform.persistence.id.NumericId
import com.linewell.dataelement.shared.error.ErrorDetailSanitizer
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.LinkedHashMap
import java.util.Locale
import dataScope
import metadataAsset
import metadataScan
import request
import tableRead
import tableWrite
import tenantRuntime

// POST /dst/collect/start。先受理，提交后探查物理库。快照、登记摘要及任务完成状态同事务保存。
return respond(() => {
    var jsonError = request.get().getAttribute(MagicBootConfiguration.MAGIC_COLLECTION_WRITE_JSON_ERROR)
    if (jsonError != null) {
        throw jsonError
    }
    var text = value => value == null ? '' : (value + '').trim()
    var sourceId = text(body?.dbId)
    if (!sourceId.matches('[A-Za-z0-9_-]{1,64}')) {
        throw new IllegalArgumentException('dbId 格式不正确')
    }
    var mode = text(body?.mode).toUpperCase(Locale.ROOT)
    var refresh = mode == 'REFRESH' || mode == 'REEXPLORE'
    var collectionMode = refresh ? 'REFRESH' : 'INITIAL'
    var validationOnly = body?.dryRun == true
    var tenantId = tenantRuntime.id()
    var active = {is_del: 0}
    var jobScoped = {datasource_id: sourceId, is_del: 0}
    var json = SpringUtil.getBean(ObjectMapper.class)
    var normalize = value => text(value).toLowerCase(Locale.ROOT)
    var first = values => {
        for (value in values) {
            var candidate = text(value)
            if (candidate != '') {
                return candidate
            }
        }
        return ''
    }
    var number = value => value == null ? 0L : (value + '')::long(0)
    var updateJob = (id, values) => db.table('metadata_table_collection_job_t').withBlank().where()
        .eq('tenant_id', tenantId).eq('datasource_id', sourceId).eq('tid', id).eq('is_del', 0)
        .eq('status', 'RUNNING').update(values)
    var failJob = (id, message) => {
        var now = new Date()
        updateJob(id, {status: 'FAILED', phase: 'FAILED', error_message: message, finished_time: now, updated_time: now})
    }
    var resultFor = row => {
        var columns = jobColumns('p', false)
        var previous = tableRead.from('metadata_table_collection_job_t', 'p', tenantId)
        previous.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
        previous.where(tableRead.equal(columns.datasource_id, sourceId))
        previous.where(tableRead.equal(columns.status, 'SUCCEEDED'))
        previous.where(tableRead.not(tableRead.equal(columns.tid, row.tid)))
        var prior = previous.rows({finished: columns.finished_time}, [columns.finished_time, columns.tid],
            ['desc', 'desc'], 1, true)
        return status(row, sourceId, prior.isEmpty() ? null : prior[0].finished, false)
    }

    var collect = jobId => {
        try {
            if (updateJob(jobId, {phase: 'SCANNING', updated_time: new Date()}) != 1) {
                return
            }
            var inventory = metadataScan.scan(sourceId)
            var physical = new LinkedHashMap()
            for (table in inventory.tables()) {
                if (table != null) {
                    var name = normalize(table.getTableName())
                    if (name != '') {
                        physical.putIfAbsent(name, table)
                    }
                }
            }
            var count = physical.size()
            if (updateJob(jobId, {phase: 'PERSISTING', scanned_count: count, total_count: count, updated_time: new Date()}) != 1) {
                return
            }
            db.transaction(() => {
                // 受理、快照提交和改名都先锁来源。租约丢失或任务被替换后禁止提交旧结果。
                var source = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', sourceId, ['tid'], active)
                if (source == null || !metadataScan.holdsLease(jobId)) {
                    throw new IllegalStateException('数据源已失效或采集任务占用已结束，请重新探查')
                }
                var running = tableWrite.lockRow('metadata_table_collection_job_t', tenantId, 'tid', jobId,
                    ['tid'], {datasource_id: sourceId, is_del: 0, status: 'RUNNING'})
                if (running == null) {
                    throw new IllegalStateException('采集任务状态已变化，请重新探查')
                }
                var saved = new LinkedHashMap()
                var savedFields = {}
                for (field in ['tid', 'table_name', 'table_name_cn', 'table_type', 'business_type']) {
                    savedFields[field] = tableRead.column('t', field, 'TEXT')
                }
                savedFields.table_comment = tableRead.column('t', 'table_comment', 'LOB')
                for (field in ['annotated', 'field_count', 'record_count']) {
                    savedFields[field] = tableRead.column('t', field, 'NUMBER')
                }
                var current = tableRead.from('db_table_t', 't', tenantId)
                current.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), sourceId))
                current.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
                var updated = tableRead.column('t', 'updated_time', 'TIME')
                current.forEachOrderedRow(savedFields, [savedFields.annotated, updated, savedFields.tid],
                    ['desc', 'desc', 'desc'], false, row => {
                        var key = normalize(row.table_name)
                        if (key != '') {
                            saved.putIfAbsent(key, row)
                        }
                    })

                var timestamp = new Date()
                var owner = normalize(inventory.legacyOwner())
                var updates = []
                if (owner != '') {
                    // 旧版本以裸名保存当前所有者的表。仅迁移已匹配的代表行，保留其治理标注。
                    for (key in physical.keySet()) {
                        var physicalTable = physical.get(key)
                        var qualified = text(physicalTable.getTableName())
                        var separator = qualified.indexOf('.')
                        if (normalize(physicalTable.getSchemaName()) == owner && separator > 0 && separator < qualified.length() - 1) {
                            var priorName = normalize(qualified.substring(separator + 1))
                            var priorTable = saved.get(priorName)
                            if (priorTable != null && !saved.containsKey(key)) {
                                updates.add({tid: priorTable.tid, table_name: qualified, table_name_en: qualified, updated_time: timestamp})
                                saved.remove(priorName)
                                priorTable.table_name = qualified
                                saved.put(key, priorTable)
                            }
                        }
                    }
                }
                if (!updates.isEmpty() && tableWrite.batchUpdate('db_table_t', tenantId,
                    ['table_name', 'table_name_en', 'updated_time'], ['tid'], jobScoped, updates) != updates.size()) {
                    throw new IllegalStateException('历史表名更新期间记录已变化，请重新探查')
                }

                var deleted = []
                if (refresh) {
                    for (key in saved.keySet()) {
                        if (!physical.containsKey(key)) {
                            var row = saved.get(key)
                            deleted.add({tid: text(row.tid), tableId: text(row.tid), tableName: text(row.table_name),
                                tableNameEn: text(row.table_name),
                                tableNameCn: first([row.table_name_cn, row.table_comment, row.table_name]),
                                tableComment: first([row.table_comment, row.table_name_cn, row.table_name]),
                                tableType: text(row.table_type), businessType: text(row.business_type),
                                fieldCount: number(row.field_count), recordCount: number(row.record_count)})
                        }
                    }
                }
                var additions = []
                for (key in physical.keySet()) {
                    if (!saved.containsKey(key)) {
                        var table = physical.get(key)
                        var name = text(table.getTableName())
                        var title = first([table.getTableComment(), name])
                        additions.add({tid: NumericId.nextId(), tenant_id: tenantId, datasource_id: sourceId, table_name: name,
                            table_name_en: name, table_name_cn: title, table_comment: title,
                            table_type: first([table.getTableType(), '数据表']), business_type: '业务表', annotated: 0,
                            record_count: table.getRowCount(), field_count: table.getColumnCount(),
                            created_time: timestamp, updated_time: timestamp, reg_time: timestamp,
                            asset_status: 0, flow_status: 0, is_del: 0})
                    }
                }
                if (!additions.isEmpty()) {
                    db.table('db_table_t').withBlank().batchInsert(additions, 200)
                }
                tableWrite.updateWithDatabaseTimestamp('db_datasource_t', tenantId, 'tid', sourceId,
                    {table_num: count}, active, 'updated_time', 6)
                metadataAsset.refreshRegistrationStatus(sourceId, false)
                var finished = new Date()
                if (updateJob(jobId, {status: 'SUCCEEDED', phase: 'COMPLETED', scanned_count: count,
                    total_count: count, persisted_count: additions.size(), added_count: additions.size(),
                    unchanged_count: count - additions.size(), deleted_count: deleted.size(),
                    deleted_detail: json.writeValueAsString(deleted), error_message: '',
                    finished_time: finished, updated_time: finished}) != 1) {
                    throw new IllegalStateException('采集任务状态已变化，请重新探查')
                }
            })
        } catch (error) {
            // 回滚结束后单独标记失败，前端可读到原因并重试。
            var reason = ErrorDetailSanitizer.errorText(first([error.getMessage(), '采集任务失败，请重新探查']))
            failJob(jobId, reason.length() > 1000 ? reason.substring(0, 1000) : reason)
        }
    }

    return db.transaction(() => {
        var source = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', sourceId,
            ['tid', 'org_id', 'created_by'], active)
        if (source == null || !dataScope.canReadDataSource(source)) {
            throw new IllegalArgumentException('数据源不存在或无访问权限')
        }
        metadataScan.validate(sourceId)
        if (validationOnly) {
            return {dryRun: true, datasourceId: sourceId, mode: collectionMode}
        }

        // 来源行锁串行判断运行任务。其他实例仍持有占用时直接返回现有任务。
        var columns = jobColumns('j', false)
        var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
        jobs.where(tableRead.equal(tableRead.column('j', 'is_del', 'NUMBER'), 0))
        jobs.where(tableRead.equal(columns.datasource_id, sourceId))
        jobs.where(tableRead.equal(columns.status, 'RUNNING'))
        var running = jobs.rows(columns, [columns.created_time, columns.tid], ['desc', 'desc'], 1, true)
        if (!running.isEmpty()) {
            var previous = running[0]
            if (metadataScan.active(previous.tid)) {
                return resultFor(previous)
            }
            failJob(previous.tid, '采集服务已重启，请重新发起采集')
        }

        var now = new Date()
        var jobId = NumericId.nextId()
        var entry = {tid: jobId, tenant_id: tenantId, datasource_id: sourceId, collection_mode: collectionMode,
            status: 'RUNNING', phase: 'QUEUED', scanned_count: 0, persisted_count: 0, total_count: 0,
            added_count: 0, unchanged_count: 0, deleted_count: 0, deleted_detail: '[]',
            created_time: now, updated_time: now, started_time: now, is_del: 0}
        db.table('metadata_table_collection_job_t').insert(entry)
        metadataScan.afterCommit(jobId, () => collect(jobId),
            () => failJob(jobId, '采集执行器不可用，请重新发起采集'))
        return resultFor(entry)
    })
})
